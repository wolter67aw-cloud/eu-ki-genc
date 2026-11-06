import java.time.LocalDate
import java.time.ZoneId

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Versionsnummern kommen aus dem CI (GitHub Actions run_number), lokal gibt es Standardwerte.
val ciVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
val ciVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0.0-dev"
val updateRepo = providers.environmentVariable("GITHUB_REPOSITORY").orNull
    ?: providers.gradleProperty("updateRepo").orNull
    ?: ""
val keystoreFile = providers.environmentVariable("KEYSTORE_FILE").orNull
// Optionale E-Mail für Meldungen von KI-Inhalten (Play-Store-Pflicht). In gradle.properties: reportEmail=…
val reportEmail = providers.gradleProperty("reportEmail").orNull ?: ""
// Testversion: Ablaufdatum und Prüfsummen der Zugangscodes
val betaExpiresMillis = LocalDate.parse(providers.gradleProperty("betaExpires").orNull ?: "2026-12-05")
    .atTime(23, 59, 59).atZone(ZoneId.of("Europe/Berlin")).toInstant().toEpochMilli()
val betaCodeHashes = providers.gradleProperty("betaCodeHashes").orNull ?: ""
val betaKeyBlobs = providers.gradleProperty("betaKeyBlobs").orNull ?: ""

android {
    namespace = "eu.kigenc.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "eu.kigenc.app"
        minSdk = 26
        targetSdk = 37
        versionCode = ciVersionCode
        versionName = ciVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "REPORT_EMAIL", "\"$reportEmail\"")
    }

    // Zwei Varianten: „github“ (mit Selbst-Update) und „play“ (Updates über Google Play, ohne Installationsrecht)
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "BETA", "false")
            buildConfigField("long", "BETA_EXPIRES", "0L")
            buildConfigField("String", "BETA_CODE_HASHES", "\"\"")
            buildConfigField("String", "BETA_KEY_BLOBS", "\"\"")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            buildConfigField("boolean", "BETA", "false")
            buildConfigField("long", "BETA_EXPIRES", "0L")
            buildConfigField("String", "BETA_CODE_HASHES", "\"\"")
            buildConfigField("String", "BETA_KEY_BLOBS", "\"\"")
        }
        // Testversion für 12 Personen, 30 Tage gültig – eigene App neben der normalen Version
        create("beta") {
            dimension = "store"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            // Updates kommen aus dem Vorab-Release „testversion“ auf GitHub
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "BETA", "true")
            buildConfigField("long", "BETA_EXPIRES", "${betaExpiresMillis}L")
            buildConfigField("String", "BETA_CODE_HASHES", "\"$betaCodeHashes\"")
            buildConfigField("String", "BETA_KEY_BLOBS", "\"$betaKeyBlobs\"")
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
