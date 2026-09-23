import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Station settings live in local.properties (not committed):
//   operator.pin=1234
//   support.contact=+34 600 000 000
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Release signing key, also never committed: keystore.properties next to local.properties
//   storeFile=D:/AutoTennisClub/keys/autotennisclub-release.jks
//   storePassword=...
//   keyAlias=autotennisclub
//   keyPassword=...
// Kiosk tablets only accept updates signed with this same key: keep two backups.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.autotennisclub.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.autotennisclub.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val operatorPin = localProperties.getProperty("operator.pin") ?: "0000".also {
            logger.warn("operator.pin missing in local.properties: Maintenance PIN defaults to 0000")
        }
        buildConfigField("String", "OPERATOR_PIN", "\"$operatorPin\"")
        buildConfigField("String", "SUPPORT_CONTACT", "\"${localProperties.getProperty("support.contact", "")}\"")
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            if (signingConfig == null) {
                logger.warn("keystore.properties missing: release APKs will be unsigned and cannot be installed")
            }
            optimization {
                enable = false
            }
        }
    }

    flavorDimensions += "station"
    productFlavors {
        // Simulated MAX B and card terminal: development, QA and sales demos. Installs next
        // to the real app and shows a DEMO label, so it can never pass for a real station.
        create("demo") {
            dimension = "station"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "SIMULATED", "true")
        }
        // Real MAX B over BLE; payments stay disabled until SumUp is integrated (phase 6).
        create("production") {
            dimension = "station"
            buildConfigField("boolean", "SIMULATED", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The customer switches ES / CAT / EN at runtime, so every language must ship in the APK.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
