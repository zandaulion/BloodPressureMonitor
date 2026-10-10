import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zandaulion.bpdigitized"
    compileSdk = 36
    // Allow device checks against the exact signed release that will be installed.
    testBuildType = providers.gradleProperty("ocrTestBuildType").orElse("debug").get()

    // Release signing is driven by an untracked keystore.properties at the repo
    // root (see keystore.properties.template). When the file is absent (fresh
    // clone, CI), release builds are simply left unsigned instead of failing.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
    }

    defaultConfig {
        applicationId = "com.zandaulion.bpdigitized"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "1.6.2"

        // Clinical mode. When true the app surfaces AHA hypertension staging,
        // the cardiovascular risk assessment, and condition inputs (diabetes,
        // smoker, MAP/PP, medication tags). Enabled for the directly-distributed
        // (sideloaded) build — Google Play's "Diseases & Conditions" policy does
        // not apply outside the Store. See Features.kt.
        buildConfigField("boolean", "CLINICAL_MODE", "true")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            // Opt-in device tests install beside the user's normal app and database.
            if (providers.gradleProperty("isolatedOcrTest").orNull == "true") {
                applicationIdSuffix = ".ocrtest"
            }
        }
        release {
            // Sideload releases target ARM64 phones; debug keeps emulator support.
            ndk { abiFilters += "arm64-v8a" }
            // Sign with the release key only when keystore.properties is present;
            // otherwise the build stays unsigned rather than breaking.
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        language {
            // The app has its own in-app language switcher, so all locale
            // resources must be present on every device. Disabling language
            // splits prevents the Play Store from stripping non-device-locale
            // strings, which would otherwise cause the switcher to fall back
            // to English on Play Store builds.
            enableSplit = false
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Keep AGP's dependency list, encrypted for Google Play, out of APKs and
    // bundles. F-Droid asks for this, and reproducible builds depend on it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Export the Room schema to a checked-in JSON file per version. This gives a
// reviewable record of every schema and is the source of truth for verifying
// migrations (and for MigrationTestHelper-based tests in the future).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.health.connect.client)
    implementation(libs.accompanist.permissions)

    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)


    testImplementation(libs.junit)
    // Desktop JNI is used only by local JVM tests; Android packages only the Android AAR.
    testRuntimeOnly(libs.onnxruntime.jvm)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
