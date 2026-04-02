package com.alertify.feature_identidad.data.auth

sealed class AuthError(open val requestId: String? = null) {
    data class InvalidCredentials(override val requestId: String? = null) : AuthError(requestId)
    data class AccountBlocked(override val requestId: String? = null) : AuthError(requestId)
    data class AccountInactive(override val requestId: String? = null) : AuthError(requestId)

    data class ValidationError(
        val errors: List<String> = emptyList(),
        override val requestId: String? = null
    ) : AuthError(requestId)

    data class ConflictError(
        val detail: String? = null,
        override val requestId: String? = null
    ) : AuthError(requestId)

    data class ServerError(override val requestId: String? = null) : AuthError(requestId)
    data class NetworkError(val cause: Throwable? = null) : AuthError(null)

    data class UnknownError(
        override val requestId: String? = null,
        val cause: Throwable? = null
    ) : AuthError(requestId)
}
