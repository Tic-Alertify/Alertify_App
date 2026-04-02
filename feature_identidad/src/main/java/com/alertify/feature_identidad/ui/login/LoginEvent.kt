package com.alertify.feature_identidad.ui.login

import com.alertify.feature_identidad.data.auth.AuthError

sealed class LoginEvent {
    data object NavigateToDashboard : LoginEvent()
    data class ShowError(val authError: AuthError) : LoginEvent()
}
