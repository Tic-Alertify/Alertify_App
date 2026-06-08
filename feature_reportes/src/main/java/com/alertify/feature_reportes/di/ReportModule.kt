package com.alertify.feature_reportes.di

import com.alertify.feature_reportes.BuildConfig
import com.alertify.feature_reportes.data.api.ReportApiService
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
object ReportModule {

    @Provides
    @Singleton
    @Named("reportRetrofit")
    fun provideReportRetrofit(
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
    fun provideReportApiService(@Named("reportRetrofit") retrofit: Retrofit): ReportApiService {
        return retrofit.create(ReportApiService::class.java)
    }
}
