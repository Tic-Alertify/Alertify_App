package com.alertify.core.network.dto

import com.google.gson.annotations.SerializedName

data class LogoutRequest(
    @SerializedName("refresh_token") val refreshToken: String
)
