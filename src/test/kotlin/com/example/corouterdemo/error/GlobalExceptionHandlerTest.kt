package com.example.corouterdemo.error

import com.example.corouterdemo.support.IntegrationTest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.WebFilter
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList

private val logger = KotlinLogging.logger {}

/** Paths through [GlobalExceptionHandler] the real endpoints cannot trigger, driven by a test-only controller and filter. */
@ExtendWith(OutputCaptureExtension::class)
@Import(GlobalExceptionHandlerTest.Config::class, GlobalExceptionHandlerTest.FailingController::class)
class GlobalExceptionHandlerTest : IntegrationTest() {
    @Autowired
    lateinit var reporter: RecordingReporter

    /** Stands in for Sentry: records what it is given, or fails on demand. */
    class RecordingReporter : ErrorReporter {
        val reports = CopyOnWriteArrayList<Pair<Throwable, ErrorContext>>()

        @Volatile
        var failing = false

        override fun report(
            error: Throwable,
            context: ErrorContext,
        ) {
            if (failing) throw IllegalStateException("error tracker is down")
            reports += error to context
        }
    }

    @TestConfiguration
    class Config {
        @Bean
        fun recordingReporter() = RecordingReporter()

        /** Stands in for a filter that rejects requests, such as a rate limiter. */
        @Bean
        fun throwingFilter() =
            WebFilter { exchange, chain ->
                when (exchange.request.headers.getFirst("X-Test-Filter")) {
                    "conflict" -> {
                        exchange.response.headers.set(HttpHeaders.RETRY_AFTER, "5")
                        throw ConflictException(internalMessage = "Request still in progress")
                    }
                    "crash" -> throw IllegalStateException("R2DBC connection refused: password=s3cr3t")
                    else -> chain.filter(exchange)
                }
            }
    }

    @RestController
    @RequestMapping("/test-errors")
    class FailingController {
        @GetMapping("/crash")
        suspend fun crash(): String = throw IllegalStateException("Connection refused: jdbc password=s3cr3t")

        @GetMapping("/timeout")
        suspend fun timeout(): String = withTimeout(10) { delay(1_000).let { "late" } }

        @GetMapping("/cancelled")
        suspend fun cancelled(): String = throw CancellationException("Job was cancelled")

        @GetMapping("/denied")
        suspend fun denied(): String = throw AccessDeniedException("User 1 lacks ROLE_ADMIN")

        @GetMapping("/locked")
        suspend fun locked(): String = throw OptimisticLockingFailureException("Failed to update table [posts] version 3")

        @GetMapping("/legacy")
        suspend fun legacy(): String = throw ResponseStatusException(HttpStatus.FORBIDDEN, "Needs ROLE_ADMIN in acme-corp")

        @GetMapping("/field")
        suspend fun field(): String =
            throw FieldValidationException(
                FieldViolation("content", "error.validation.invalidValue"),
                internalMessage = "Content matches a banned phrase",
            )

        @GetMapping("/other-thread")
        suspend fun otherThread(): String =
            withContext(Dispatchers.IO) {
                logger.info { "Running on another thread" }
                "ok"
            }
    }

    @BeforeEach
    fun reset() {
        reporter.reports.clear()
        reporter.failing = false
    }

    private fun get(
        uri: String,
        vararg headers: Pair<String, String>,
    ) = asUser(ALICE)
        .get()
        .uri(uri)
        .headers { h -> headers.forEach { (name, value) -> h.set(name, value) } }
        .exchange()

    private fun WebTestClient.ResponseSpec.isProblem(
        status: Int,
        code: String,
    ): WebTestClient.BodyContentSpec =
        expectStatus()
            .isEqualTo(status)
            .expectHeader()
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo(status)
            .jsonPath("$.code")
            .isEqualTo(code)
            .jsonPath("$.correlationId")
            .isNotEmpty

    private fun WebTestClient.BodyContentSpec.bodyExcludes(vararg fragments: String): WebTestClient.BodyContentSpec =
        consumeWith { result ->
            val body = String(result.responseBody ?: ByteArray(0), Charsets.UTF_8)
            fragments.forEach { assertThat(body).doesNotContain(it) }
        }

    @Test
    fun `unexpected exception - generic 500, logged with the correlation id and reported`(output: CapturedOutput) {
        get("/test-errors/crash?token=secret", "X-Correlation-ID" to "req-123")
            .isProblem(500, "error.internal")
            .jsonPath("$.correlationId")
            .isEqualTo("req-123")
            .bodyExcludes("s3cr3t", "jdbc", "IllegalStateException")

        assertThat(output.out).contains("correlationId=req-123").contains("java.lang.IllegalStateException: Connection refused")
        val (error, context) = reporter.reports.single()
        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(context).isEqualTo(ErrorContext("req-123", "GET", "/test-errors/crash", 500, "error.internal"))
    }

    @Test
    fun `failing reporter - does not change the response`() {
        reporter.failing = true
        get("/test-errors/crash").isProblem(500, "error.internal")
    }

    @Test
    fun `own timeout - 503 logged as an error and reported`(output: CapturedOutput) {
        get("/test-errors/timeout", "X-Correlation-ID" to "req-timeout", HttpHeaders.ACCEPT_LANGUAGE to "fr")
            .isProblem(503, "error.serviceUnavailable")
            .jsonPath("$.detail")
            .isEqualTo("Le service est temporairement indisponible. Veuillez réessayer plus tard.")
            .bodyExcludes("Timed out")

        val line = output.out.lines().first { "req-timeout" in it && "Request failed" in it }
        assertThat(line).contains(" ERROR ").contains("reason=timeout")
        assertThat(
            reporter.reports
                .single()
                .second.status,
        ).isEqualTo(503)
    }

    @Test
    fun `cancellation - 503 logged as a warning, not reported`(output: CapturedOutput) {
        get("/test-errors/cancelled", "X-Correlation-ID" to "req-cancel")
            .isProblem(503, "error.serviceUnavailable")
            .bodyExcludes("Job")

        val lines = output.out.lines().filter { "req-cancel" in it }
        assertThat(lines.first { "Request failed" in it }).contains(" WARN ").contains("reason=cancelled")
        assertThat(lines).noneMatch { " ERROR " in it }
        assertThat(reporter.reports).isEmpty()
    }

    @Test
    fun `access denied - translated 403 that hides the reason`(output: CapturedOutput) {
        get("/test-errors/denied", HttpHeaders.ACCEPT_LANGUAGE to "fr")
            .isProblem(403, "error.forbidden")
            .jsonPath("$.detail")
            .isEqualTo("Vous n’avez pas la permission d’effectuer cette action.")
            .bodyExcludes("ROLE_ADMIN")
        assertThat(output.out).contains("User 1 lacks ROLE_ADMIN")
    }

    @Test
    fun `optimistic locking failure - 409 that hides table and version`() {
        get("/test-errors/locked")
            .isProblem(409, "error.conflict")
            .bodyExcludes("[posts]", "version 3")
    }

    @Test
    fun `ResponseStatusException - reason is not sent`() {
        get("/test-errors/legacy")
            .isProblem(403, "error.forbidden")
            .bodyExcludes("ROLE_ADMIN", "acme-corp")
    }

    @Test
    fun `manual field errors - same format as bean validation`(output: CapturedOutput) {
        get("/test-errors/field", HttpHeaders.ACCEPT_LANGUAGE to "fr")
            .isProblem(400, "error.validation")
            .jsonPath("$.errors.length()")
            .isEqualTo(1)
            .jsonPath("$.errors[0].field")
            .isEqualTo("content")
            .jsonPath("$.errors[0].code")
            .isEqualTo("error.validation.invalidValue")
            .jsonPath("$.errors[0].message")
            .isEqualTo("Valeur invalide.")
            .bodyExcludes("banned")
        assertThat(output.out).contains("fields=[content:error.validation.invalidValue] reason=Content matches a banned phrase")
    }

    @Test
    fun `client errors and cancellations - are not reported`() {
        get("/users/999999").expectStatus().isNotFound
        get("/test-errors/denied").expectStatus().isForbidden
        get("/test-errors/cancelled").expectStatus().isEqualTo(503)
        webTestClient
            .get()
            .uri("/users/me")
            .exchange()
            .expectStatus()
            .isUnauthorized
        assertThat(reporter.reports).isEmpty()
    }

    @Test
    fun `ApiException in a filter - normal translated problem, filter headers kept`() {
        get("/users/1", "X-Test-Filter" to "conflict", "X-Correlation-ID" to "req-filter", HttpHeaders.ACCEPT_LANGUAGE to "fr")
            .expectHeader()
            .valueEquals("X-Correlation-ID", "req-filter")
            .expectHeader()
            .valueEquals(HttpHeaders.RETRY_AFTER, "5")
            .isProblem(409, "error.conflict")
            .jsonPath("$.detail")
            .isEqualTo("La requête est en conflit avec l’état actuel de la ressource.")
            .bodyExcludes("in progress")
        assertThat(reporter.reports).isEmpty()
    }

    @Test
    fun `unexpected failure in a filter - generic 500, reported`() {
        get("/users/1", "X-Test-Filter" to "crash", "X-Correlation-ID" to "req-filter-crash")
            .isProblem(500, "error.internal")
            .bodyExcludes("s3cr3t", "R2DBC")
        assertThat(
            reporter.reports
                .single()
                .second.correlationId,
        ).isEqualTo("req-filter-crash")
    }

    @Test
    fun `unknown route - translated 404`() {
        get("/nope", HttpHeaders.ACCEPT_LANGUAGE to "fr")
            .isProblem(404, "error.notFound")
            .jsonPath("$.detail")
            .isEqualTo("La ressource demandée est introuvable.")
    }

    @Test
    fun `unsupported content type - translated 415`() {
        asUser(OTHER_USER)
            .put()
            .uri("/users/me/profile")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("bio")
            .exchange()
            .isProblem(415, "error.request.mediaTypeNotSupported")
    }

    @Test
    fun `malformed json - does not leak Jackson details`() {
        asUser(OTHER_USER)
            .put()
            .uri("/users/me/profile")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"bio": """)
            .exchange()
            .isProblem(400, "error.request.unreadable")
            .bodyExcludes("com.example", "JSON")
    }

    @Test
    fun `unsafe incoming correlation id - is replaced`() {
        get("/users/999999", "X-Correlation-ID" to "evil FAKE LOG LINE")
            .expectHeader()
            .value("X-Correlation-ID", not(containsString("evil")))
            .expectBody()
            .jsonPath("$.correlationId")
            .value(not(containsString("evil")))
    }

    @Test
    fun `correlation id - follows the coroutine onto another thread`(output: CapturedOutput) {
        get("/test-errors/other-thread", "X-Correlation-ID" to "req-co-7")
            .expectHeader()
            .valueEquals("X-Correlation-ID", "req-co-7")
        assertThat(output.out.lines().first { "Running on another thread" in it }).contains("correlationId=req-co-7")
    }
}
