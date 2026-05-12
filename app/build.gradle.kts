plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("com.google.gms.google-services")
}


android {
    namespace = "app.nexstream.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.nexstream.player"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.compose.tv.material)
    implementation(libs.compose.tv.foundation)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)  // CHANGED FROM kapt
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)  // CHANGED FROM kapt

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)


    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")


    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    implementation("androidx.compose.material:material-icons-extended")

    // ZXing — QR code generation
    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.compose.material3:material3:1.2.1")

    implementation("androidx.core:core-ktx:1.13.1")


    implementation("com.google.firebase:firebase-messaging:24.0.0")

    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")


    }
//