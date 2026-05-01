// Archivo: Alertify_App/build.gradle.kts (RAÍZ)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    // Sprint 4: Plugin Google Services para procesar google-services.json (Firebase)
    id("com.google.gms.google-services") version "4.4.2" apply false
}
