package com.alertify.mobileapp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlertifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aquí podrás inicializar otras cosas globales en el futuro
        // (por ejemplo, Timber para logs, o configuraciones de Mapbox)
    }
}