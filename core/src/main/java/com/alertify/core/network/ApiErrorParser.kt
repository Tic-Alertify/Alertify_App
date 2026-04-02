package com.alertify.core.network

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.alertify.core.network.dto.ErrorResponse
import retrofit2.Response

object ApiErrorParser {

    private val gson = Gson()

    fun parse(response: Response<*>): ErrorResponse? {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) return null

        return try {
            gson.fromJson(raw, ErrorResponse::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }
}
