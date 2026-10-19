plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.zandaulion.mlkitbenchmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zandaulion.mlkitbenchmark"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
