plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.christiandonatti.fwip"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.christiandonatti.fwip"
        minSdk = 26
        targetSdk = 35
        // Test builds pass the CI run number; releases pass a code derived from the tag (v0.3.0 -> 300).
        versionCode = (System.getenv("FWIP_VERSION_CODE") ?: "300").toInt()
        versionName = System.getenv("FWIP_VERSION_NAME") ?: "0.3.0"

        ndk {
            // Phones and tablets; keeps the ML runtime from bloating the APK with emulator builds.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // The private release key only exists in CI secrets (see RELEASING.md).
    val releaseStore = System.getenv("FWIP_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = System.getenv("FWIP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FWIP_KEY_ALIAS")
                keyPassword = System.getenv("FWIP_KEY_PASSWORD")
            }
        }
        // Development key kept in the repo so every test build installs over the previous one.
        create("dev") {
            storeFile = rootProject.file("keystore/fwip-dev.jks")
            storePassword = "fwipdev"
            keyAlias = "fwipdev"
            keyPassword = "fwipdev"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseStore != null) "release" else "dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The balloon model is memory-mapped straight from the APK.
        noCompress += "tflite"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/META-INF/versions/**"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    implementation("io.coil-kt:coil-compose:2.7.0")

    // Home screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // On-device speech balloon detection
    implementation("com.google.ai.edge.litert:litert:1.2.0")

    // Comic archives: CBZ/ZIP and CB7/7z via Commons Compress (+ XZ for LZMA), CBR/RAR via junrar
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.junrar:junrar:7.5.5")
    // RAR5 archives (newer WinRAR default), which junrar cannot read
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    // Network folders: SMB shares (smbj, with Bouncy Castle for NTLM) and WebDAV (OkHttp)
    implementation("com.hierynomus:smbj:0.13.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
