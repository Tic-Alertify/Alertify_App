package com.alertify.core.network.dto

import com.google.gson.annotations.SerializedName

data class RegisterResponse(
    @SerializedName("message") val message: String,
    @SerializedName("userId") val userId: Int
)
