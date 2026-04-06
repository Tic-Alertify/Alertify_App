package com.alertify.feature_ruteo.di

import com.alertify.feature_ruteo.data.api.RuteoApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RuteoModule {

    @Provides
    @Singleton
    fun provideRuteoApiService(retrofit: Retrofit): RuteoApiService {
        return retrofit.create(RuteoApiService::class.java)
    }
}
