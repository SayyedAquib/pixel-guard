import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pixelguard.applock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pixelguard.applock"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val keystoreConfigFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystoreConfigFile.exists()) {
            FileInputStream(keystoreConfigFile).use { load(it) }
        }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProps.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("KEYSTORE_PATH")
            val storePass = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAl = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val keyPass = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            if (storeFilePath != null && storePass != null && keyAl != null && keyPass != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAl
                keyPassword = keyPass
            } else {
                throw GradleException("Release signing credentials missing. Please configure keystore.properties based on keystore.properties.example")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
            test.minHeapSize = "256m"
            test.maxHeapSize = "1024m"
            test.jvmArgs("-XX:+UseParallelGC")

            val requestedTasks = project.gradle.startParameter.taskNames
            val isFastMode = project.hasProperty("fast") ||
                requestedTasks.any { it.contains("testFast", ignoreCase = true) }

            if (isFastMode) {
                test.exclude("**/*UiTest.class", "**/*ContrastTest.class")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Biometrics for Pixel 9 (Fingerprint + Face Unlock)
    implementation(libs.androidx.biometric)

    // DataStore Preferences & Keystore Encryption
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // CameraX for Intruder Selfie
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    // Unit & Presentation Testing
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    // Compose UI Testing (Robolectric JVM + Connected Device)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("testFast") {
    group = "verification"
    description = "Runs fast pure unit and domain logic tests, skipping slow Robolectric Compose UI tests."
    dependsOn("testDebugUnitTest")
}
