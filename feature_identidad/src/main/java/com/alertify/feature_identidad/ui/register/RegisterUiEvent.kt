package com.alertify.feature_identidad.ui.register

import com.alertify.feature_identidad.data.auth.AuthError

sealed class RegisterUiEvent {
    data object RegistrationSuccess : RegisterUiEvent()
    data class ShowError(val authError: AuthError) : RegisterUiEvent()
}
