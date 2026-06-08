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
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideRuteoApiService(@Named("ruteoRetrofit") retrofit: Retrofit): RuteoApiService {
        return retrofit.create(RuteoApiService::class.java)
    }
}
