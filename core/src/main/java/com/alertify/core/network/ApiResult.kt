package com.alertify.core.network

import com.alertify.core.network.dto.ErrorResponse

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()

    data class Error(
        val httpCode: Int? = null,
        val apiError: ErrorResponse? = null,
        val throwable: Throwable? = null
    ) : ApiResult<Nothing>()
}
