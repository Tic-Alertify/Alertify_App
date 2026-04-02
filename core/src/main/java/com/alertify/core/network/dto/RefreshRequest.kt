package com.alertify.core.network.dto

import com.google.gson.annotations.SerializedName

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)
