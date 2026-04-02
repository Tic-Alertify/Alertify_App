package com.alertify.feature_identidad.ui.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alertify.core.network.ApiResult
import com.alertify.feature_identidad.data.auth.AuthError
import com.alertify.feature_identidad.data.auth.AuthErrorMapper
import com.alertify.feature_identidad.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<RegisterUiEvent>(Channel.BUFFERED)
    val events: Flow<RegisterUiEvent> = _events.receiveAsFlow()

    fun register(username: String, email: String, password: String, confirmPassword: String) {
        if (_uiState.value is RegisterUiState.Loading) return

        if (username.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            val error = AuthError.ValidationError(errors = listOf("Complete todos los campos"))
            _uiState.value = RegisterUiState.Error(error)
            viewModelScope.launch { _events.send(RegisterUiEvent.ShowError(error)) }
            return
        }

        if (password != confirmPassword) {
            val error = AuthError.ValidationError(errors = listOf("Las contrasenas no coinciden"))
            _uiState.value = RegisterUiState.Error(error)
            viewModelScope.launch { _events.send(RegisterUiEvent.ShowError(error)) }
            return
        }

        _uiState.value = RegisterUiState.Loading

        viewModelScope.launch {
            when (val result = authRepository.register(email, username, password)) {
                is ApiResult.Success -> {
                    _uiState.value = RegisterUiState.Success
                    _events.send(RegisterUiEvent.RegistrationSuccess)
                }

                is ApiResult.Error -> {
                    val authError = AuthErrorMapper.map(
                        result.apiError,
                        result.httpCode,
                        result.throwable
                    )
                    Log.e(TAG, "Register error", result.throwable)
                    _uiState.value = RegisterUiState.Error(authError)
                    _events.send(RegisterUiEvent.ShowError(authError))
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }

    companion object {
        private const val TAG = "RegisterViewModel"
    }
}
