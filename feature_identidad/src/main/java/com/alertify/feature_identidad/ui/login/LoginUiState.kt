package com.alertify.feature_identidad.ui.login

import com.alertify.feature_identidad.data.auth.AuthError

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val authError: AuthError) : LoginUiState()
}
