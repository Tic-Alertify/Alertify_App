package com.alertify.feature_identidad.ui.register

import com.alertify.feature_identidad.data.auth.AuthError

sealed class RegisterUiState {
    data object Idle : RegisterUiState()
    data object Loading : RegisterUiState()
    data object Success : RegisterUiState()
    data class Error(val authError: AuthError) : RegisterUiState()
}
