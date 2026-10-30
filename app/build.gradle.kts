plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hoursgram.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hoursgram.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Pass -PTelegram.ApiId=... -PTelegram.ApiHash=... to bake the keys in;
        // without them the app asks for the keys on first start.
        val apiId = (findProperty("Telegram.ApiId") as String?)?.trim()?.toIntOrNull() ?: 0
        buildConfigField("int", "API_ID", "$apiId")
        val apiHash = ((findProperty("Telegram.ApiHash") as String?) ?: "").trim().filter { it.isLetterOrDigit() }
        buildConfigField("String", "API_HASH", "\"$apiHash\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.g000sha256:tdl-coroutines:17.0.0")
}
