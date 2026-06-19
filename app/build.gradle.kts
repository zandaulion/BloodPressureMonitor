plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.zandaulion.bpdigitized"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zandaulion.bpdigitized"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "1.4.0"

        // Clinical mode. When true the app surfaces AHA hypertension staging,
        // the cardiovascular risk assessment, and condition inputs (diabetes,
        // smoker, MAP/PP, medication tags). Shipped false so the published build
        // presents as a neutral fitness blood-pressure logger. See Features.kt.
        buildConfigField("boolean", "CLINICAL_MODE", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            // Lets Remote Config conditions target dev builds only (Version
            // contains "-dev"), so model changes can be tested on this machine's
            // builds before being promoted to the default value for all users.
            versionNameSuffix = "-dev"
        }
        release {
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

    implementation(libs.tesseract4android) {
        // The main artifact transitively depends on tesseract4android-openmp which
        // contains identical classes → duplicate class conflict.  Exclude it and
        // use the standard (non-OpenMP) native libraries only.
        exclude(group = "com.github.adaptech-cz.Tesseract4Android", module = "tesseract4android-openmp")
    }

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

    // Firebase: AI Logic (Gemini via Vertex AI backend), Remote Config, App Check
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // RevenueCat
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
