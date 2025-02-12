buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.8.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info")
    }
}
