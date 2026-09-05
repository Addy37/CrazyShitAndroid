plugins {
    id("com.android.application")
}

android {
    namespace = "com.webapp.crazyshit"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Keep the existing release application ID so stable releases upgrade the main app.
        applicationId = "com.addy37.crazyshitunofficial"
        buildConfigField("boolean", "PRIVATE_DISTRIBUTION", "true")
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 2_012_001
        versionName = System.getenv("APP_VERSION_NAME") ?: "2.12.1"
    }

    val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

    if (
        releaseKeystorePath != null &&
        releaseKeystorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null
    ) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Betas remain side-by-side with stable while using the same persistent signing key.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "CrazyShit Beta")
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}