package com.alertify.core.network

import com.alertify.core.network.dto.LoginRequest
import com.alertify.core.network.dto.LoginResponse
import com.alertify.core.network.dto.LogoutRequest
import com.alertify.core.network.dto.LogoutResponse
import com.alertify.core.network.dto.RefreshRequest
import com.alertify.core.network.dto.RefreshResponse
import com.alertify.core.network.dto.RegisterRequest
import com.alertify.core.network.dto.RegisterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("auth/registro")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<LogoutResponse>
}
