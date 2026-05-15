import java.util.Properties

plugins {
    id("jacoco")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.google.firebase.perf)
    id("kotlin-parcelize")
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.joseibarra.trazago"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joseibarra.trazago"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        multiDexKeepFile = file("multidex-config.txt")

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
        manifestPlaceholders["PLACES_API_KEY"] = properties.getProperty("PLACES_API_KEY", "")

        // Places API key como string resource (más confiable que BuildConfig para local.properties)
        resValue("string", "places_api_key", properties.getProperty("PLACES_API_KEY", ""))
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md"
            )
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.multidex:multidex:2.0.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.places)
    implementation(libs.android.maps.utils)
    implementation(libs.androidx.constraintlayout)
    // Firebase BoM — alinea versiones de dependencias transitivas (firebase-common, tasks, etc.)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.functions)
    implementation(libs.generativeai)

    // Glide
    implementation(libs.glide)

    // UI: Lottie, Splash Screen, Transitions
    implementation(libs.lottie)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.transition.ktx)

    // Markdown rendering
    implementation(libs.markwon.core)
    implementation(libs.markwon.image.glide)

    // ViewPager2 para galería de fotos
    implementation(libs.androidx.viewpager2)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.savedstate)

    // Kotlin Coroutines para Firebase
    implementation(libs.kotlinx.coroutines.play.services)

    // OkHttp para Routes API v2
    implementation(libs.okhttp)

    // WorkManager for event reminders
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // EncryptedSharedPreferences para almacenamiento seguro
    implementation(libs.security.crypto)

    // Timber para logging estructurado
    implementation(libs.timber)

    // LeakCanary para detectar memory leaks (solo en debug)
    debugImplementation(libs.leakcanary)

    // ===== UNIT TESTS =====
    testImplementation(libs.junit)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(libs.arch.core.testing)
    testImplementation(libs.test.core)

    // ===== INSTRUMENTED TESTS =====
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.espresso.accessibility)
    androidTestImplementation(libs.fragment.testing)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// ===== JaCoCo coverage report =====
tasks.register<JacocoReport>("jacocoTestDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Test*.*", "android/**/*.*",
        "**/*_Impl*.*", "**/*Dao_Impl*.*",
        "**/*Activity*.*", "**/*Adapter*.*"
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        exclude(fileFilter)
    }
    val kotlinDebugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(debugTree, kotlinDebugTree))
    sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/testDebugUnitTest.exec", "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        }
    )
}

tasks.register("verifyCoverage") {
    dependsOn("jacocoTestDebugUnitTestReport")
    doLast {
        val reportFile = file("${layout.buildDirectory.get()}/reports/jacoco/jacocoTestDebugUnitTestReport/jacocoTestDebugUnitTestReport.xml")
        if (!reportFile.exists()) {
            throw GradleException("JaCoCo XML report not found. Run jacocoTestDebugUnitTestReport first.")
        }
        val xml = reportFile.readText()
        val regex = Regex("""<counter type="LINE" missed="(\d+)" covered="(\d+)"/>""")
        val match = regex.findAll(xml).lastOrNull()
        if (match != null) {
            val missed = match.groupValues[1].toLong()
            val covered = match.groupValues[2].toLong()
            val total = missed + covered
            val pct = if (total > 0) covered * 100.0 / total else 0.0
            println("Line coverage: ${"%.1f".format(pct)}% ($covered/$total)")
            val threshold = 65.0
            if (pct < threshold) {
                throw GradleException("Coverage ${"%.1f".format(pct)}% is below threshold $threshold%")
            }
        }
    }
}

// ===== Detekt static analysis =====
configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    source.setFrom("src/main/java")
    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}
