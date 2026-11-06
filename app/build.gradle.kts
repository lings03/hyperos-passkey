import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localSigning = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) localFile.inputStream().use { load(it) }
}
val signingStorePassword = providers.environmentVariable("PASSKEY_STORE_PASSWORD").orNull
    ?: localSigning.getProperty("passkey.storePassword")
val signingKeyPassword = providers.environmentVariable("PASSKEY_KEY_PASSWORD").orNull
    ?: localSigning.getProperty("passkey.keyPassword")

android {
    namespace = "io.github.rin.passkey"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.rin.passkey"
        minSdk = 34
        targetSdk = 37
        // The Shizuku user service is restarted when this changes, so bump it with every AIDL change.
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/passkey-release.jks")
            storePassword = signingStorePassword
            keyAlias = "passkey"
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { it.name == "validateSigningRelease" }.configureEach {
    doFirst {
        check(!signingStorePassword.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
            "Set passkey.storePassword and passkey.keyPassword in local.properties, or PASSKEY_STORE_PASSWORD and PASSKEY_KEY_PASSWORD."
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
