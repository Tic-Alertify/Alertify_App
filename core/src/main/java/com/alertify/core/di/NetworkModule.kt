package com.alertify.core.di

import android.content.Context
import android.content.SharedPreferences
import com.alertify.core.BuildConfig
import com.alertify.core.network.AuthApi
import com.alertify.core.network.AuthInterceptor
import com.alertify.core.network.TokenAuthenticator
import com.alertify.core.network.socket.SocketManager
import com.alertify.core.storage.AuthSessionManager
import com.alertify.core.storage.SharedPrefsTokenStorage
import com.alertify.core.storage.TokenStorage
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /*
     * ─── CONFIGURACIÓN DE BACKENDS ───
     * IPs y URLs de conexión:
     * - Desarrollo (PC Local): http://192.168.100.35:3000/
     * - Emulador Android: http://10.0.2.2:3000/
     * - Producción: https://api.alertify.com/
     * - WebSockets: http://192.168.100.35:3000
     *
     * Servicios por Módulo:
     * - :core              -> AuthApi (Gestión de Sesión)
     * - :feature_reportes  -> ReportApiService (Incidentes y Alertas)
     * - :feature_ruteo     -> RuteoApiService (Rutas y Navegación)
     */

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideTokenStorage(impl: SharedPrefsTokenStorage): TokenStorage = impl

    @Provides
    @Singleton
    fun provideAuthSessionManager(
        tokenStorage: TokenStorage,
        authApi: AuthApi
    ): AuthSessionManager {
        return AuthSessionManager(tokenStorage, authApi)
    }

    @Provides
    @Singleton
    fun provideSocketManager(): SocketManager = SocketManager()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    @Named("baseUrl")
    fun provideBaseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    @Named("authlessOkHttp")
    fun provideAuthlessOkHttp(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            logging.redactHeader("Authorization")
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("authlessRetrofit")
    fun provideAuthlessRetrofit(
        @Named("baseUrl") baseUrl: String,
        @Named("authlessOkHttp") okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(@Named("authlessRetrofit") retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    @Named("authorizedOkHttp")
    fun provideAuthorizedOkHttp(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            logging.redactHeader("Authorization")
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @Named("authorizedRetrofit")
    fun provideAuthorizedRetrofit(
        @Named("baseUrl") baseUrl: String,
        @Named("authorizedOkHttp") okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(@Named("authorizedRetrofit") retrofit: Retrofit): Retrofit = retrofit

    private const val PREFS_NAME = "alertify_auth_prefs"
    private const val TIMEOUT_SECONDS = 30L
}
