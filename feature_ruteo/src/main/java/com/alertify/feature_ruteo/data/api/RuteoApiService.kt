package com.alertify.feature_ruteo.data.api

import com.alertify.feature_ruteo.data.model.RuteoRequest
import com.alertify.feature_ruteo.data.model.RuteoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface RuteoApiService {

    @POST("api/ruteo/calcular")
    suspend fun calcularRutaSegura(
        @Body request: RuteoRequest
    ): Response<RuteoResponse>
}