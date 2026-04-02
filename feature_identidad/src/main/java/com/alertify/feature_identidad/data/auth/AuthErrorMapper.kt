package com.alertify.feature_identidad.data.auth

import com.alertify.core.network.dto.ErrorResponse
import java.io.IOException

object AuthErrorMapper {

    fun map(
        apiError: ErrorResponse?,
        httpCode: Int?,
        throwable: Throwable?
    ): AuthError {
        val requestId = apiError?.requestId
        val code = apiError?.code
        val message = when (val msg = apiError?.message) {
            is String -> msg
            else -> null
        }

        if (code != null) {
            return when (code) {
                "AUTH_INVALID_CREDENTIALS" -> AuthError.InvalidCredentials(requestId)
                "AUTH_REFRESH_INVALID" -> AuthError.InvalidCredentials(requestId)
                "AUTH_ACCOUNT_BLOCKED" -> AuthError.AccountBlocked(requestId)
                "AUTH_ACCOUNT_INACTIVE" -> AuthError.AccountInactive(requestId)
                "AUTH_UNEXPECTED_ERROR" -> AuthError.ServerError(requestId)
                "VALIDATION_ERROR" -> AuthError.ValidationError(
                    errors = extractMessageList(apiError.message),
                    requestId = requestId
                )
                "RESOURCE_CONFLICT" -> AuthError.ConflictError(
                    detail = message,
                    requestId = requestId
                )
                else -> mapByHttpCode(httpCode, requestId, throwable, message, apiError)
            }
        }

        if (throwable is IOException) {
            return AuthError.NetworkError(throwable)
        }

        return mapByHttpCode(httpCode, requestId, throwable, message, apiError)
    }

    private fun mapByHttpCode(
        httpCode: Int?,
        requestId: String?,
        throwable: Throwable?,
        message: String?,
        apiError: ErrorResponse? = null
    ): AuthError {
        return when {
            httpCode == 400 -> AuthError.ValidationError(
                errors = extractMessageList(apiError?.message),
                requestId = requestId
            )

            httpCode == 401 -> AuthError.InvalidCredentials(requestId)

            httpCode == 403 -> {
                when {
                    message?.contains("bloquead", ignoreCase = true) == true ->
                        AuthError.AccountBlocked(requestId)
                    message?.contains("inactiv", ignoreCase = true) == true ->
                        AuthError.AccountInactive(requestId)
                    else -> AuthError.AccountInactive(requestId)
                }
            }

            httpCode == 409 -> AuthError.ConflictError(
                detail = message,
                requestId = requestId
            )

            httpCode != null && httpCode >= 500 -> AuthError.ServerError(requestId)
            throwable is IOException -> AuthError.NetworkError(throwable)
            else -> AuthError.UnknownError(requestId, throwable)
        }
    }

    private fun extractMessageList(message: Any?): List<String> {
        return when (message) {
            is String -> listOf(message)
            is List<*> -> message.filterIsInstance<String>()
            else -> emptyList()
        }
    }
}
