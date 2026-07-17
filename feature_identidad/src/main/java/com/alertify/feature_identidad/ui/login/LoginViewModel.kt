package com.alertify.feature_identidad.ui.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertify.core.network.ApiResult
import com.alertify.core.storage.AuthSessionManager
import com.alertify.feature_identidad.BuildConfig
import com.alertify.feature_identidad.data.AuthRepository
import com.alertify.feature_identidad.data.auth.AuthErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: AuthSessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun checkSession() {
        // DEBUG: Para testing sin backend del login
        // Descomentar estas líneas para saltarse el login
        if (BuildConfig.DEBUG) {
            sessionManager.saveTokensSync(
                accessToken = "debug-mock-token-12345",
                refreshToken = "debug-mock-refresh-67890"
            )
            viewModelScope.launch { _events.emit(LoginEvent.NavigateToDashboard) }
            return
        }
        
        if (sessionManager.isLoggedInSync()) {
            viewModelScope.launch { _events.emit(LoginEvent.NavigateToDashboard) }
        }
    }

    fun login(email: String, password: String) {
        if (_uiState.value is LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            // DEBUG MODE: Sin backend, simula login directo
            if (BuildConfig.DEBUG) {
                try {
                    // Simula tokens mock - sin validar credenciales
                    val simulatedUserId = kotlin.math.abs(email.hashCode())
                    sessionManager.saveTokensSync(
                        accessToken = "debug-mock-token-${email.hashCode()}",
                        refreshToken = "debug-mock-refresh-${System.currentTimeMillis()}"
                    )
                    sessionManager.saveCurrentUserIdSync(simulatedUserId)
                    _uiState.value = LoginUiState.Success
                    Log.d(TAG, "DEBUG: Login simulado para $email con userId=$simulatedUserId")
                    _events.emit(LoginEvent.NavigateToDashboard)
                    return@launch
                } catch (e: Exception) {
                    Log.e(TAG, "DEBUG: Error simulando login", e)
                }
            }

            // MODO PRODUCCIÓN: Llamar al backend real
            when (val result = authRepository.login(email, password)) {
                is ApiResult.Success -> {
                    try {
                        sessionManager.onLoginSuccess(
                            accessToken = result.data.accessToken,
                            refreshToken = result.data.refreshToken,
                            userId = result.data.user.id
                        )
                        _uiState.value = LoginUiState.Success
                        _events.emit(LoginEvent.NavigateToDashboard)
                    } catch (e: Exception) {
                        val authError = AuthErrorMapper.map(null, null, e)
                        _uiState.value = LoginUiState.Error(authError)
                        _events.emit(LoginEvent.ShowError(authError))
                    }
                }

                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError,
                        result.httpCode,
                        result.throwable
                    )
                    Log.e(TAG, "Login error", result.throwable)
                    _uiState.value = LoginUiState.Error(authError)
                    _events.emit(LoginEvent.ShowError(authError))
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}
