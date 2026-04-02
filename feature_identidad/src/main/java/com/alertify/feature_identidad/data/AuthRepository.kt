package com.alertify.feature_identidad.data

import com.alertify.core.network.ApiErrorParser
import com.alertify.core.network.ApiResult
import com.alertify.core.network.AuthApi
import com.alertify.core.network.dto.LoginRequest
import com.alertify.core.network.dto.LoginResponse
import com.alertify.core.network.dto.LogoutRequest
import com.alertify.core.network.dto.LogoutResponse
import com.alertify.core.network.dto.RegisterRequest
import com.alertify.core.network.dto.RegisterResponse
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi
) {
    suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        return safeApiCall { authApi.login(LoginRequest(email, password)) }
    }

    suspend fun register(
        email: String,
        username: String,
        password: String
    ): ApiResult<RegisterResponse> {
        return safeApiCall { authApi.register(RegisterRequest(email, username, password)) }
    }

    suspend fun logout(refreshToken: String): ApiResult<LogoutResponse> {
        return safeApiCall { authApi.logout(LogoutRequest(refreshToken)) }
    }

    private suspend fun <T> safeApiCall(
        call: suspend () -> Response<T>
    ): ApiResult<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(httpCode = response.code())
                }
            } else {
                val apiError = ApiErrorParser.parse(response)
                ApiResult.Error(
                    httpCode = response.code(),
                    apiError = apiError
                )
            }
        } catch (e: IOException) {
            ApiResult.Error(throwable = e)
        } catch (e: Exception) {
            ApiResult.Error(throwable = e)
        }
    }
}
