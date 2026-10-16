plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.axiom.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.axiom.launcher"
        minSdk = 28
        targetSdk = 35
        versionCode = 22
        versionName = "0.22.0-beta.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    buildTypes {
        // Signed with the test/debug key for installability only. NOT a production release.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// Record 4600-grain benchmark timings in the GitHub Actions log.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    testLogging.showStandardStreams = true
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
