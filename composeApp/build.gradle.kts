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
        versionCode = 1
        versionName = "1.0.0"
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
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb, TargetFormat.Dmg, TargetFormat.Msi)
            packageName = "Pulsorb"
            packageVersion = "1.0.0"
            description = "Drum machine of glowing circles"
            vendor = "Marek Dudka"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.instrument", "jdk.unsupported")
            linux {
                iconFile.set(rootProject.file("docs/icon/pulsorb-512.png"))
            }
        }
    }
}
