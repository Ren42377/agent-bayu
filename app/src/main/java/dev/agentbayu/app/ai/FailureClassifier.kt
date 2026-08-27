package dev.agentbayu.app.ai

enum class FailureKind {
    RETRYABLE,
    COOLDOWN,
    MODEL_LOCK,
    TERMINAL
}

data class RouteFailure(
    val kind: FailureKind,
    val message: String,
    val statusCode: Int? = null,
    val retryAfterMillis: Long? = null,
    val tripsBreaker: Boolean = false
) {
    val logLabel: String
        get() = "status=" + (statusCode ?: 0) + " kind=" + kind.name
}

object FailureClassifier {

    private val MODEL_HINTS = listOf(
        "model",
        "no such model",
        "unknown model",
        "does not exist",
        "not found",
        "unsupported",
        "decommissioned",
        "deprecated"
    )

    private val QUOTA_HINTS = listOf(
        "insufficient_quota",
        "insufficient quota",
        "billing",
        "credit",
        "payment",
        "balance"
    )

    fun classifyHttp(statusCode: Int, body: String, retryAfterHeader: String? = null): RouteFailure {
        val lower = body.lowercase()
        return when {
            statusCode == 401 -> RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "unauthorized",
                statusCode = statusCode
            )

            statusCode == 402 || statusCode == 403 -> RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "forbidden or out of credit",
                statusCode = statusCode
            )

            statusCode == 429 && QUOTA_HINTS.any { lower.contains(it) } -> RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "quota exhausted",
                statusCode = statusCode
            )

            statusCode == 429 -> RouteFailure(
                kind = FailureKind.COOLDOWN,
                message = "rate limited",
                statusCode = statusCode,
                retryAfterMillis = parseRetryAfter(retryAfterHeader)
            )

            statusCode == 408 -> RouteFailure(
                kind = FailureKind.RETRYABLE,
                message = "request timeout",
                statusCode = statusCode,
                tripsBreaker = true
            )

            statusCode == 404 && mentionsModel(lower) -> RouteFailure(
                kind = FailureKind.MODEL_LOCK,
                message = "model unavailable",
                statusCode = statusCode
            )

            statusCode == 404 -> RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "endpoint not found",
                statusCode = statusCode
            )

            statusCode == 400 && mentionsModel(lower) -> RouteFailure(
                kind = FailureKind.MODEL_LOCK,
                message = "model rejected the request",
                statusCode = statusCode
            )

            statusCode == 400 -> RouteFailure(
                kind = FailureKind.TERMINAL,
                message = "bad request",
                statusCode = statusCode
            )

            statusCode == 413 -> RouteFailure(
                kind = FailureKind.MODEL_LOCK,
                message = "payload too large",
                statusCode = statusCode
            )

            statusCode in 500..599 -> RouteFailure(
                kind = FailureKind.RETRYABLE,
                message = "server error",
                statusCode = statusCode,
                tripsBreaker = true
            )

            else -> RouteFailure(
                kind = FailureKind.RETRYABLE,
                message = "unexpected status",
                statusCode = statusCode
            )
        }
    }

    fun classifyError(error: Throwable): RouteFailure = RouteFailure(
        kind = FailureKind.RETRYABLE,
        message = error.javaClass.simpleName,
        tripsBreaker = true
    )

    private fun mentionsModel(lowerBody: String): Boolean =
        lowerBody.contains("model") && MODEL_HINTS.any { lowerBody.contains(it) }

    fun parseRetryAfter(header: String?): Long? {
        val value = header?.trim() ?: return null
        if (value.isEmpty()) return null
        val seconds = value.toLongOrNull()
        if (seconds != null) return (seconds.coerceAtLeast(0L)) * 1000L
        val fractional = value.toDoubleOrNull() ?: return null
        if (fractional < 0.0) return null
        return (fractional * 1000.0).toLong()
    }
}
