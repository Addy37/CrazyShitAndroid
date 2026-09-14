plugins {
    id("com.android.application")
}

android {
    namespace = "com.addy37.crazyshitadmin"
    compileSdk = 35

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.addy37.crazyshitadmin"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
        buildConfigField(
            "String",
            "ADMIN_FEEDBACK_ENDPOINT",
            "\"${System.getenv("ADMIN_FEEDBACK_ENDPOINT") ?: "https://fketutffusxgjxjlckci.supabase.co/functions/v1/feedback-admin"}\""
        )
        buildConfigField(
            "String",
            "ADMIN_SOURCE_CONFIG_ENDPOINT",
            "\"${System.getenv("ADMIN_SOURCE_CONFIG_ENDPOINT") ?: ""}\""
        )
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
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.srcDir("../app/src/main/assets")
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("androidx.work:work-runtime:2.11.2")
}
