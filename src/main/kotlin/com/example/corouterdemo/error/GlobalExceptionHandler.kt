package com.example.corouterdemo.error

import com.example.corouterdemo.filter.RequestLoggingFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.validation.ConstraintViolationException
import kotlinx.coroutines.TimeoutCancellationException
import org.slf4j.MDC
import org.springframework.beans.TypeMismatchException
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.Order
import org.springframework.core.codec.CodecException
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.codec.ServerCodecConfigurer
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.reactive.result.view.ViewResolver
import org.springframework.web.server.MethodNotAllowedException
import org.springframework.web.server.MissingRequestValueException
import org.springframework.web.server.NotAcceptableStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.UnsupportedMediaTypeStatusException
import org.springframework.web.server.WebExceptionHandler
import org.springframework.web.util.DisconnectedClientHelper
import reactor.core.publisher.Mono
import java.net.URI
import java.util.Locale
import java.util.concurrent.CancellationException

private val logger = KotlinLogging.logger {}

/**
 * The only place where an exception becomes an HTTP response.
 *
 * It is a [WebExceptionHandler], not a `@RestControllerAdvice`, so it sees every exception: from
 * controllers, from WebFilters (which a controller advice never sees), and from routing. It runs
 * before Spring Boot's own error handler (order -1), which therefore never answers a request.
 * Spring Security's entry point and access-denied handler delegate here too (see `SecurityConfig`).
 *
 * Each exception goes through three steps:
 * 1. [classify]: decide the status, the public message key, and how to log it ([Failure]).
 * 2. [logAndReport]: log it once, with the correlation ID, method, and path. Server faults are
 *    logged at ERROR with the stack trace and passed to every [ErrorReporter]; nothing else is.
 * 3. [write]: send an RFC 9457 `application/problem+json` body. Its `title` and `detail` come from
 *    `messages*.properties` in the caller's locale, plus a stable `code` and the `correlationId`.
 *
 * Exception messages are never sent to the client. That covers ours, Spring's, Jackson's, and the
 * JDK's. They go to the log, where the `correlationId` links them to the response.
 */
@Component
@Order(-2)
class GlobalExceptionHandler(
    private val translator: MessageTranslator,
    private val validationErrors: ValidationErrorTranslator,
    private val reporters: ObjectProvider<ErrorReporter>,
    codecs: ServerCodecConfigurer,
) : WebExceptionHandler {
    /** Writes bodies with the application's own codecs, so JSON settings match normal responses. */
    private val responseContext =
        object : ServerResponse.Context {
            override fun messageWriters() = codecs.writers

            override fun viewResolvers() = emptyList<ViewResolver>()
        }

    override fun handle(
        exchange: ServerWebExchange,
        ex: Throwable,
    ): Mono<Void> {
        // The client is gone (broken pipe, connection reset): nobody to answer and nothing to fix.
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            withCorrelationMdc(exchange) { logger.debug { "Client disconnected: $ex" } }
            return Mono.empty()
        }
        val locale = exchange.localeContext.locale ?: Locale.ENGLISH
        val failure = classify(ex, locale)
        logAndReport(exchange, ex, failure)
        if (exchange.response.isCommitted) {
            // Part of the response was already sent, so a problem body cannot be added. Let the
            // server close the connection.
            withCorrelationMdc(exchange) { logger.warn { "Response already committed; cannot write error body for ${failure.key}" } }
            return Mono.error(ex)
        }
        return write(exchange, failure, locale)
    }

    /**
     * What to send and how to log it.
     *
     * @property key the public message key. Also sent as the stable `code`.
     * @property errors field errors, sent as `errors` for validation failures only
     * @property serverFault logged at ERROR with the stack trace and reported. Defaults to every 5xx.
     * @property logDetail extra text for the log line. Never sent to the client.
     * @property logStackTrace for a client error: log the stack trace too, because it wraps a cause
     */
    private class Failure(
        val status: HttpStatusCode,
        val key: String,
        val args: List<Any> = emptyList(),
        val headers: HttpHeaders = HttpHeaders(),
        val errors: List<ValidationErrorItem>? = null,
        val serverFault: Boolean = status.is5xxServerError,
        val logDetail: String? = null,
        val logStackTrace: Boolean = false,
    )

    // Step 1: classify.

    private fun classify(
        ex: Throwable,
        locale: Locale,
    ): Failure =
        when (ex) {
            // Our own withTimeout expired: a real failure, and the client is still waiting. Must come
            // before CancellationException, which it extends.
            is TimeoutCancellationException ->
                Failure(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_KEY, logDetail = "reason=timeout")

            // Work cancelled while the client is still connected: a shutdown in progress, or awaiting
            // a job other code cancelled. (A client disconnect cancels the coroutine without an error,
            // so it never gets here.) Not a bug on its own: a rolling deploy produces many of these.
            is CancellationException ->
                Failure(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    SERVICE_UNAVAILABLE_KEY,
                    serverFault = false,
                    logDetail = "reason=cancelled: ${ex.message}",
                )

            // Exceptions the application throws on purpose. FieldValidationException first: it is
            // also an ApiException.
            is FieldValidationException ->
                validationFailure(
                    validationErrors.fromFieldViolations(ex.violations, locale),
                    // Without an internal message, Throwable.message is only the key: not worth logging.
                    reason = ex.message?.takeIf { it != ex.messageKey },
                )

            is ApiException ->
                Failure(ex.status, ex.messageKey, ex.publicArgs, logDetail = "reason=${ex.message}", logStackTrace = ex.cause != null)

            // Validation. These must come before ErrorResponse, which they implement.
            is WebExchangeBindException -> validationFailure(validationErrors.fromBinding(ex, locale))
            is HandlerMethodValidationException -> validationFailure(validationErrors.fromMethodValidation(ex, locale))
            is ConstraintViolationException -> validationFailure(validationErrors.fromViolations(ex.constraintViolations, locale))

            // Security and persistence exceptions that are not bugs. Spring Security's entry point and
            // access-denied handler pass theirs here, as does method security (@PreAuthorize).
            is AccessDeniedException -> clientError(ex, HttpStatus.FORBIDDEN, "error.forbidden")
            is AuthenticationException -> clientError(ex, HttpStatus.UNAUTHORIZED, "error.unauthenticated")
            // @Version check failed: another request saved first. The client should reload and retry.
            is OptimisticLockingFailureException -> clientError(ex, HttpStatus.CONFLICT, "error.conflict")
            // Body larger than spring.codec.max-in-memory-size, when not wrapped by Spring.
            is DataBufferLimitException -> clientError(ex, HttpStatus.PAYLOAD_TOO_LARGE, "error.request.payloadTooLarge")

            // Spring's own exceptions: 404, 405, 415, unreadable body, type mismatch, and so on, plus
            // ResponseStatusException. Their text may echo client input or a developer's reason, so it
            // is replaced with a translated message. Their headers are kept (for example Allow on 405).
            is ErrorResponse -> frameworkFailure(ex)

            else -> unexpected(ex)
        }

    private fun validationFailure(
        errors: List<ValidationErrorItem>,
        reason: String? = null,
    ): Failure {
        // Log field names and constraint codes only. Never log rejected values.
        val fields = "fields=" + errors.map { "${it.field ?: "<object>"}:${it.code}" }
        return Failure(
            HttpStatus.BAD_REQUEST,
            VALIDATION_KEY,
            errors = errors,
            logDetail = fields + (reason?.let { " reason=$it" } ?: ""),
        )
    }

    private fun clientError(
        ex: Throwable,
        status: HttpStatus,
        key: String,
    ) = Failure(status, key, logDetail = describe(ex))

    /**
     * Maps a Spring exception to a public message key. Arguments are limited to names the API itself
     * defines, such as parameter or header names. Values the client sent are never used.
     *
     * WebFlux reports most request problems as a [ServerWebInputException], so the cause and the
     * method parameter decide which message applies.
     */
    private fun frameworkFailure(ex: ErrorResponse): Failure {
        val status = ex.statusCode
        val detail = describe(ex as Throwable)

        fun failure(
            key: String,
            args: List<Any> = emptyList(),
            status: HttpStatusCode = ex.statusCode,
        ) = Failure(status, key, args, headers = ex.headers, logDetail = detail)

        return when {
            ex is MissingRequestValueException && ex.label == "header" ->
                failure("error.request.missingHeader", listOf(ex.name))
            ex is MissingRequestValueException -> failure("error.request.missingParameter", listOf(ex.name))
            ex is ServerWebInputException && ex.hasCause<DataBufferLimitException>() ->
                failure("error.request.payloadTooLarge", status = HttpStatus.PAYLOAD_TOO_LARGE)
            ex is ServerWebInputException && ex.cause is TypeMismatchException ->
                apiName(ex.methodParameter)?.let { failure("error.request.typeMismatch", listOf(it)) }
                    ?: failure(genericKey(status))
            ex is ServerWebInputException &&
                (ex.hasCause<CodecException>() || ex.methodParameter?.hasParameterAnnotation(RequestBody::class.java) == true) ->
                failure("error.request.unreadable")
            ex is MethodNotAllowedException -> failure("error.request.methodNotSupported")
            ex is UnsupportedMediaTypeStatusException -> failure("error.request.mediaTypeNotSupported")
            ex is NotAcceptableStatusException -> failure("error.request.notAcceptable")
            ex is NoResourceFoundException -> failure("error.notFound")
            else -> failure(genericKey(status))
        }
    }

    /**
     * Anything unexpected becomes a generic 500. Exceptions annotated with `@ResponseStatus` keep
     * their status but still get a generic message.
     */
    private fun unexpected(ex: Throwable): Failure {
        val status =
            AnnotatedElementUtils
                .findMergedAnnotation(ex.javaClass, ResponseStatus::class.java)
                ?.code ?: HttpStatus.INTERNAL_SERVER_ERROR
        return Failure(status, genericKey(status), logDetail = describe(ex))
    }

    // Step 2: log and report.

    private fun logAndReport(
        exchange: ServerWebExchange,
        ex: Throwable,
        failure: Failure,
    ) {
        val request = exchange.request
        // The path never includes the query string, which may hold tokens.
        val line =
            "Request failed status=${failure.status.value()} code=${failure.key} " +
                "method=${request.method} path=${request.path.value()}" + (failure.logDetail?.let { " $it" } ?: "")
        withCorrelationMdc(exchange) {
            when {
                failure.serverFault -> logger.error(ex) { line }
                failure.logStackTrace -> logger.warn(ex) { line }
                else -> logger.warn { line }
            }
        }
        if (failure.serverFault) report(exchange, ex, failure)
    }

    private fun report(
        exchange: ServerWebExchange,
        ex: Throwable,
        failure: Failure,
    ) {
        val context =
            ErrorContext(
                correlationId = RequestLoggingFilter.currentCorrelationId(exchange),
                method = exchange.request.method.name(),
                path = exchange.request.path.value(),
                status = failure.status.value(),
                code = failure.key,
            )
        reporters.orderedStream().forEach { reporter ->
            try {
                reporter.report(ex, context)
            } catch (reportFailure: Exception) {
                // A broken error tracker must never change the response.
                withCorrelationMdc(exchange) { logger.warn(reportFailure) { "Error reporter ${reporter.javaClass.name} failed" } }
            }
        }
    }

    // Step 3: write.

    private fun write(
        exchange: ServerWebExchange,
        failure: Failure,
        locale: Locale,
    ): Mono<Void> {
        val status = failure.status
        val problem =
            ProblemDetail.forStatus(status).apply {
                title = translator.translate("error.title.${status.value()}", emptyList(), locale)
                    ?: HttpStatus.resolve(status.value())?.reasonPhrase
                    ?: "Error"
                detail = translator.translate(failure.key, failure.args, locale)
                    ?: translator.translate(genericKey(status), emptyList(), locale)
                instance = URI.create(exchange.request.path.value())
                setProperty("code", failure.key)
                RequestLoggingFilter.currentCorrelationId(exchange)?.let { setProperty("correlationId", it) }
                failure.errors?.let { setProperty("errors", it) }
            }
        return ServerResponse
            .status(status)
            .headers { it.addAll(failure.headers) }
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValue(problem)
            .flatMap { it.writeTo(exchange, responseContext) }
    }

    // Helpers.

    /** Exception class and message, for the log only. */
    private fun describe(ex: Throwable) = "exception=${ex.javaClass.name}: ${ex.message}"

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this as Throwable?) { it.cause.takeIf { cause -> cause !== it } }.any { it is T }

    private fun genericKey(status: HttpStatusCode): String =
        when (status.value()) {
            400 -> "error.badRequest"
            401 -> "error.unauthenticated"
            403 -> "error.forbidden"
            404 -> "error.notFound"
            405 -> "error.request.methodNotSupported"
            409 -> "error.conflict"
            413 -> "error.request.payloadTooLarge"
            415 -> "error.request.mediaTypeNotSupported"
            422 -> "error.businessRule"
            503 -> SERVICE_UNAVAILABLE_KEY
            in 400..499 -> "error.badRequest"
            else -> "error.internal"
        }

    /**
     * This handler runs on whatever thread completed the reactive chain, so the MDC may not hold
     * this request's correlation ID. It is restored from the exchange for the duration of the call.
     */
    private inline fun withCorrelationMdc(
        exchange: ServerWebExchange,
        block: () -> Unit,
    ) {
        val correlationId = RequestLoggingFilter.currentCorrelationId(exchange) ?: return block()
        MDC.putCloseable(RequestLoggingFilter.MDC_CORRELATION_ID, correlationId).use { block() }
    }

    private companion object {
        const val VALIDATION_KEY = "error.validation"
        const val SERVICE_UNAVAILABLE_KEY = "error.serviceUnavailable"
    }
}
