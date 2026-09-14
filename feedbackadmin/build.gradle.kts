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
        versionCode = 1
        versionName = "1.0.0"
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

    buildTypes {
        release { isMinifyEnabled = false }
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
