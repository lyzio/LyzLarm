plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from environment variables set by the release workflow
// (from GitHub Secrets). The keystore itself is never committed.
// The same variables sign the APK (app signing key) and, in a second Gradle run, the
// Play bundle (upload key); see .github/workflows/release.yml.
val releaseKeystore = System.getenv("LYZLARM_KEYSTORE_FILE")

// Version: from the release tag (-PappVersion=v0.4.0), else gradle.properties.
// Version code = major*10000 + minor*100 + patch (0.4.0 -> 400); see buildSrc/AppVersion.kt.
val appVersion = (findProperty("appVersion") as String?) ?: (findProperty("anatole.version") as String)

android {
    namespace = "se.lyzio.anatole"
    compileSdk = 36

    defaultConfig {
        applicationId = "se.lyzio.anatole"
        minSdk = 26
        targetSdk = 36
        versionCode = AppVersion.code(appVersion)
        versionName = AppVersion.name(appVersion)
    }

    signingConfigs {
        // Test builds: one fixed, public key (app/debug.keystore) instead of a new random
        // key per CI run, so a newer debug APK installs over an older one. It never signs releases.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("LYZLARM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LYZLARM_KEY_ALIAS")
                keyPassword = System.getenv("LYZLARM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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

    // F-Droid: no Google dependency-info block in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            // Robolectric (database migration test) needs the merged resources.
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":sleep-engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    // Snooze lock barcode challenge (v0.3): open source, no Google Play Services (ML Kit is not allowed).
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)

    // Store screenshots (v0.4): real screens rendered on the JVM by Robolectric.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
