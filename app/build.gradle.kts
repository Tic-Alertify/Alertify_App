import java.util.Properties
import java.io.FileInputStream

// Cargamos el archivo local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}
val googleMapsApiKey = localProperties.getProperty("MAPS_API_KEY") ?: ""


// Archivo: Alertify_App/app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Sprint 4: Procesa google-services.json para Firebase
    // TODO: Re-enable this plugin after adding google-services.json to the app/ folder
    id("com.google.gms.google-services")
}

android {
    namespace = "com.alertify.mobileapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alertify.mobileapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = googleMapsApiKey
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature_identidad"))
    implementation(project(":feature_ruteo"))
    implementation(project(":feature_reportes"))


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)

    implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Sprint 4: .await() en corrutinas para Firebase token y FusedLocationClient
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    // Sprint 4: FusedLocationProviderClient
    implementation(libs.play.services.location)

    // Retrofit — necesario en :app porque MainActivity y AlertifyMessagingService
    // usan ReportApiService que retorna retrofit2.Response (no es transitivo con 'implementation')
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // android-maps-utils — DashboardFragment.kt (en :app) usa PolyUtil directamente
    implementation("com.google.maps.android:android-maps-utils:3.8.0")
    implementation(libs.play.services.maps)

    // Hilt — @AndroidEntryPoint en MainActivity y AlertifyMessagingService
    implementation(libs.hilt.android)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    ksp(libs.hilt.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // LocationTracker
    implementation("com.google.android.gms:play-services-location:21.1.0")
}