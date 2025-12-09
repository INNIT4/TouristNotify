plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-kapt")
}

android {
    namespace = "com.joseibarra.touristnotify"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.joseibarra.touristnotify"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("com.google.ai.client.generativeai:generativeai:0.5.0")
    
    // Dependencias de Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}