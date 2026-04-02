package com.alertify.feature_identidad.data.auth

import android.content.Context
import com.alertify.feature_identidad.R

object AuthUiMessageFactory {

    fun toMessage(context: Context, error: AuthError): String {
        val base = when (error) {
            is AuthError.InvalidCredentials ->
                context.getString(R.string.auth_error_auth_invalid_credentials)
            is AuthError.AccountBlocked ->
                context.getString(R.string.auth_error_auth_account_blocked)
            is AuthError.AccountInactive ->
                context.getString(R.string.auth_error_auth_account_inactive)
            is AuthError.ValidationError -> {
                val msgs = error.errors
                if (msgs.isNotEmpty()) msgs.joinToString("\n")
                else context.getString(R.string.auth_error_auth_validation)
            }
            is AuthError.ConflictError ->
                error.detail ?: context.getString(R.string.auth_error_auth_conflict)
            is AuthError.ServerError ->
                context.getString(R.string.auth_error_auth_server)
            is AuthError.NetworkError ->
                context.getString(R.string.auth_error_auth_network)
            is AuthError.UnknownError ->
                context.getString(R.string.auth_error_auth_unknown)
        }

        val requestId = error.requestId
        return if (!requestId.isNullOrBlank()) {
            "$base\n${context.getString(R.string.auth_error_support_code, requestId)}"
        } else {
            base
        }
    }
}
