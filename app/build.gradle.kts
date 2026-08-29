import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("com.google.gms.google-services")
}


val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) keystoreFile.inputStream().use { keystoreProps.load(it) }

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) localPropsFile.inputStream().use { localProps.load(it) }

android {
    namespace = "app.nexstream.player"
    compileSdk = 35

    val buildNumberFile = rootProject.file("build_number.txt")
    val buildNumber = if (buildNumberFile.exists()) buildNumberFile.readText().trim() else "00001"

    defaultConfig {
        applicationId = "app.nexstream.player"
        minSdk = 21
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.4"
        buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")
        buildConfigField("int",    "BUILD_NUMBER_INT", (buildNumber.toIntOrNull() ?: 1).toString())
        buildConfigField("String", "GROQ_API_KEY",
            "\"${localProps.getProperty("GROQ_API_KEY", "")}\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                // Disable SIMD unavailable on all Android targets
                arguments += listOf(
                    "-DGGML_AVX=OFF",
                    "-DGGML_AVX2=OFF",
                    "-DGGML_F16C=OFF",
                    "-DGGML_FMA=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile     = file(keystoreProps["storeFile"] as String)
            storePassword = keystoreProps["storePassword"] as String
            keyAlias      = keystoreProps["keyAlias"] as String
            keyPassword   = keystoreProps["keyPassword"] as String
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        jniLibs {
            excludes += "**/libparakeet.so"
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

    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)


    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.media3.datasource.okhttp)

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")

    implementation("androidx.compose.material:material-icons-extended")

    // ZXing — QR code generation
    implementation("com.google.zxing:core:3.5.3")

    implementation("com.google.firebase:firebase-messaging:24.0.0")

    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Casting (Chromecast + DLNA/AirPlay discovery)
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    // Whisper.cpp AI subtitles built via NDK — see app/src/main/cpp/

    }
//