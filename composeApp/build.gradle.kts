// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Marek Dudka

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// Release signing key, kept outside the repository. keystore.properties is git-ignored; without it
// the release build is simply left unsigned.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.marek.pulsorb"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marek.pulsorb"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.marek.pulsorb.MainKt"
        nativeDistributions {
            // jpackage only builds packages for the system it runs on, and Compose rejects the others.
            val os = System.getProperty("os.name")
            when {
                os.startsWith("Mac") -> targetFormats(TargetFormat.Dmg)
                os.startsWith("Windows") -> targetFormats(TargetFormat.Msi, TargetFormat.Exe)
                else -> targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            }
            packageName = "Pulsorb"
            packageVersion = "1.1.1"
            description = "Drum machine of glowing circles"
            vendor = "Marek Dudka"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.instrument", "jdk.unsupported")
            linux {
                iconFile.set(rootProject.file("docs/icon/pulsorb-512.png"))
            }
            windows {
                iconFile.set(rootProject.file("docs/icon/pulsorb.ico"))
                menuGroup = "Pulsorb"
                shortcut = true
                dirChooser = true
                // Fixed forever: lets a newer installer replace the old version instead of installing beside it.
                upgradeUuid = "cd5834c8-3068-4939-84f2-32ebb94c4a0e"
            }
            macOS {
                iconFile.set(rootProject.file("docs/icon/pulsorb.icns"))
                bundleID = "com.marek.pulsorb"
                appCategory = "public.app-category.music"
                // The bundled Java 21 runtime needs macOS 11 (Big Sur) or newer.
                minimumSystemVersion = "11.0"
                infoPlist {
                    // Without this macOS silently denies the microphone (Sample circles).
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Pulsorb records short sounds from the microphone for Sample circles. Nothing is saved or sent anywhere.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
