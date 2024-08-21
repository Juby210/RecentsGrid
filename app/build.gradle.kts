plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.juby210.recentsgrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.juby210.recentsgrid"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
