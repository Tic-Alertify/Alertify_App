package com.alertify.feature_ruteo.di

import com.alertify.feature_ruteo.BuildConfig

import com.alertify.feature_ruteo.data.api.RuteoApiService

import dagger.Module

import dagger.Provides

import dagger.hilt.InstallIn

import dagger.hilt.components.SingletonComponent

import okhttp3.OkHttpClient

import retrofit2.Retrofit

import retrofit2.converter.gson.GsonConverterFactory

import java.util.concurrent.TimeUnit // <-- No olvides esta importación

import javax.inject.Named

import javax.inject.Singleton

@Module

@InstallIn(SingletonComponent::class)

object RuteoModule {

    @Provides

    @Singleton

    @Named("ruteoRetrofit")

    fun provideRuteoRetrofit(

        @Named("authorizedOkHttp") okHttpClient: OkHttpClient

    ): Retrofit {

        // 1. Creamos un cliente derivado con tiempos extendidos para el ruteo

        val ruteoHttpClient = okHttpClient.newBuilder()

            .connectTimeout(60, TimeUnit.SECONDS)

            .readTimeout(60, TimeUnit.SECONDS)

            .writeTimeout(60, TimeUnit.SECONDS)

            .build()

        // 2. Le pasamos este cliente modificado a Retrofit

        return Retrofit.Builder()

            .baseUrl(BuildConfig.BASE_URL)

            .client(ruteoHttpClient) // <-- Usamos el nuevo

            .addConverterFactory(GsonConverterFactory.create())

            .build()

    }

    @Provides

    @Singleton

    fun provideRuteoApiService(@Named("ruteoRetrofit") retrofit: Retrofit): RuteoApiService {

        return retrofit.create(RuteoApiService::class.java)

    }

}
