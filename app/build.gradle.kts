plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Facebook App ID: pass via -PFACEBOOK_APP_ID=... , local.properties, or env var.
// Defaults to a placeholder so the project always compiles; real Facebook Login
// requires a real Meta App ID (see README.md).
val facebookAppId: String =
    (project.findProperty("FACEBOOK_APP_ID") as String?)
        ?: System.getenv("FACEBOOK_APP_ID")
        ?: "YOUR_FACEBOOK_APP_ID"

android {
    namespace = "com.facebookpagemanager.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.facebookpagemanager.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        manifestPlaceholders["facebookAppId"] = facebookAppId
        resValue("string", "facebook_app_id", facebookAppId)
        buildConfigField("String", "FACEBOOK_APP_ID", "\"$facebookAppId\"")
        buildConfigField("String", "GRAPH_API_VERSION", "\"v20.0\"")
        buildConfigField("String", "GRAPH_API_BASE_URL", "\"https://graph.facebook.com/\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Official Meta Facebook SDK for Android (Login only; all Graph calls go
    // through Retrofit so every permission failure surfaces honestly).
    implementation(libs.facebook.login)
}
