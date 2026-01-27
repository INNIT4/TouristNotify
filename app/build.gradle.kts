import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")
}

android {
    namespace = "com.joseibarra.touristnotify"
    compileSdk = 34 // release(36) is likely incorrect, setting to a standard stable version or keeping it if user wants but compileSdk = 36 is the way

    defaultConfig {
        applicationId = "com.joseibarra.touristnotify"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Leer API keys desde local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"${properties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "DIRECTIONS_API_KEY", "\"${properties.getProperty("DIRECTIONS_API_KEY", "")}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${properties.getProperty("WEATHER_API_KEY", "")}\"")

        // Para Maps API, el Secrets Plugin lo maneja automáticamente
        manifestPlaceholders["MAPS_API_KEY"] = properties.getProperty("MAPS_API_KEY", "")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_SKIP_LOGIN", "false")
        }
        debug {
            buildConfigField("Boolean", "ENABLE_SKIP_LOGIN", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    implementation("com.google.android.libraries.places:places:3.5.0")
    implementation("com.google.android.gms:play-services-location:21.2.0") // Para obtener ubicación actual
    implementation("com.google.maps:google-maps-services:2.2.0")      // Para la API de Direcciones
    implementation("com.google.maps.android:android-maps-utils:2.3.0") // LIBRERÍA NECESARIA PARA DECODIFICAR LA RUTA
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-auth:24.0.1")
    implementation("com.google.firebase:firebase-storage:21.0.1")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.5.0")
    
    // Dependencias de Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // MPAndroidChart para gráficas de estadísticas
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ViewPager2 para galería de fotos
    implementation("androidx.viewpager2:viewpager2:1.0.0")

    // Room para base de datos local (modo offline)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager para sincronización en background
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ML Kit para escaneo de códigos QR
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // CameraX para el lector de QR
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Guava (requerido por CameraX para ListenableFuture)
    implementation("com.google.guava:guava:31.1-android")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
