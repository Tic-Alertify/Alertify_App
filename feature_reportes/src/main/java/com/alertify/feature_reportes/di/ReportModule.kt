package com.alertify.feature_reportes.di

import com.alertify.feature_reportes.data.api.ReportApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

/**
 * Módulo Hilt para inyección de dependencias de ReportApiService.
 * 
 * NOTAS ARQUITECTÓNICAS:
 * - ReportRepository y ReportEventListener se auto-inyectan via @Inject constructor
 * - Gson y Retrofit vienen del módulo core (NetworkModule)
 * - Este módulo SOLO proporciona la interfaz ReportApiService
 */
@Module
@InstallIn(SingletonComponent::class)
object ReportModule {

    @Provides
    @Singleton
    fun provideReportApiService(retrofit: Retrofit): ReportApiService {
        return retrofit.create(ReportApiService::class.java)
    }
}
