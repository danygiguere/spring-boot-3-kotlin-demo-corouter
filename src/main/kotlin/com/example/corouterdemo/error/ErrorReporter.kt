package com.example.corouterdemo.error

/**
 * Sends server-side failures to an external error tracker such as Sentry.
 *
 * [GlobalExceptionHandler] calls every `ErrorReporter` bean for each failure it logs at ERROR, and
 * for nothing else: 4xx errors and plain cancellations are expected and are not reported. With no
 * bean registered, nothing is reported. A reporter that throws is logged and ignored, so a broken
 * tracker never changes the response.
 *
 * Example with the Sentry Java SDK (`io.sentry:sentry`):
 * ```kotlin
 * @Component
 * class SentryErrorReporter : ErrorReporter {
 *     override fun report(error: Throwable, context: ErrorContext) {
 *         Sentry.withScope { scope ->
 *             context.correlationId?.let { scope.setTag("correlationId", it) }
 *             scope.setTag("http.method", context.method)
 *             scope.setTag("http.status", context.status.toString())
 *             scope.setTag("error.code", context.code)
 *             scope.setExtra("path", context.path)
 *             Sentry.captureException(error)
 *         }
 *     }
 * }
 * ```
 * `captureException` only queues the event, so it is safe to call on the event loop. A reporter
 * that does blocking I/O must hand the work off to another thread itself.
 */
fun interface ErrorReporter {
    fun report(
        error: Throwable,
        context: ErrorContext,
    )
}

/**
 * What is known about the failed request. [path] never includes the query string, which may hold
 * tokens.
 */
data class ErrorContext(
    val correlationId: String?,
    val method: String,
    val path: String,
    val status: Int,
    val code: String,
)
