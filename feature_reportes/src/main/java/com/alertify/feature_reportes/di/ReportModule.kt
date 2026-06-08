package com.alertify.feature_reportes.di

import com.alertify.feature_reportes.data.api.ReportApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReportModule {

    /*
     * ─── CONFIGURACIÓN DE BACKENDS ───
     * IPs y URLs de conexión:
     * - Desarrollo (PC Local): http://192.168.100.35:3000/
     * - Emulador Android: http://10.0.2.2:3000/
     * - Producción: https://api.alertify.com/
     * - WebSockets: http://192.168.100.35:3000
     */

    @Provides
    @Singleton
    fun provideReportApiService(retrofit: Retrofit): ReportApiService {
        return retrofit.create(ReportApiService::class.java)
    }
}
