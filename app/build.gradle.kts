plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

layout.buildDirectory.set(File("/Users/piums/gradle-builds/PiumsClienteAndroid/app"))

android {
    namespace = "com.piums.cliente"
    compileSdk = 35

    defaultConfig {
        applicationId = "piums.cliente.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://client.piums.io/\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://client.piums.io/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    // Google Sign-In
    implementation(libs.credentials)
    implementation(libs.credentials.play)
    implementation(libs.google.id)

    // Images
    implementation(libs.coil.compose)

    // Storage
    implementation(libs.security.crypto)
    implementation(libs.datastore.preferences)

    // Async
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)

    // Biometric
    implementation(libs.biometric)

    // Chrome Custom Tabs (OAuth web login)
    implementation(libs.browser)

    // Interactive map
    implementation(libs.osmdroid)

    // Video splash
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // WebSocket
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
}
