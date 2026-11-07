plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.freelink.autopilot"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.freelink.autopilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
