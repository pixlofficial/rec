import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        load(FileInputStream(versionPropsFile))
    }
}
val appVersionName: String = versionProps.getProperty("VERSION_NAME", "1.0.0")
val appVersionCode: Int = versionProps.getProperty("VERSION_CODE", "1").toInt()

android {
    namespace = "pixl.rec"
    compileSdk = 36

    defaultConfig {
        applicationId = "pixl.rec"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("REC_KEYSTORE_PATH")
                ?: (project.findProperty("REC_KEYSTORE_PATH") as? String)
            val keystorePassword = System.getenv("REC_KEYSTORE_PASSWORD")
                ?: (project.findProperty("REC_KEYSTORE_PASSWORD") as? String)
            val keyAlias = System.getenv("REC_KEY_ALIAS")
                ?: (project.findProperty("REC_KEY_ALIAS") as? String)
            val keyPassword = System.getenv("REC_KEY_PASSWORD")
                ?: (project.findProperty("REC_KEY_PASSWORD") as? String)

            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()
            ) {
                val keystoreFile = File(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword
                }
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
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
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

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val variantName = variant.buildType.name
            val baseName = "REC-v${variant.versionName}"
            val newName = if (variantName == "release") "$baseName.apk" else "$baseName-$variantName.apk"
            output?.outputFileName = newName
        }
    }
}

gradle.taskGraph.whenReady {
    val isReleaseBuildRequested = allTasks.any { task ->
        task.name.equals("assembleRelease", ignoreCase = true) ||
        task.name.equals("bundleRelease", ignoreCase = true) ||
        task.name.equals("packageRelease", ignoreCase = true) ||
        task.name.equals("packageReleaseBundle", ignoreCase = true)
    }

    if (isReleaseBuildRequested) {
        val releaseConfig = android.signingConfigs.getByName("release")
        val storeFile = releaseConfig.storeFile

        if (storeFile == null || !storeFile.exists()) {
            val envKeystorePath = System.getenv("REC_KEYSTORE_PATH")
                ?: (project.findProperty("REC_KEYSTORE_PATH") as? String)
            val envKeystorePassword = System.getenv("REC_KEYSTORE_PASSWORD")
                ?: (project.findProperty("REC_KEYSTORE_PASSWORD") as? String)
            val envKeyAlias = System.getenv("REC_KEY_ALIAS")
                ?: (project.findProperty("REC_KEY_ALIAS") as? String)
            val envKeyPassword = System.getenv("REC_KEY_PASSWORD")
                ?: (project.findProperty("REC_KEY_PASSWORD") as? String)

            val missingVars = mutableListOf<String>().apply {
                if (envKeystorePath.isNullOrBlank()) add("REC_KEYSTORE_PATH")
                if (envKeystorePassword.isNullOrBlank()) add("REC_KEYSTORE_PASSWORD")
                if (envKeyAlias.isNullOrBlank()) add("REC_KEY_ALIAS")
                if (envKeyPassword.isNullOrBlank()) add("REC_KEY_PASSWORD")
            }

            val errorMessage = if (missingVars.isNotEmpty()) {
                """
                |========================================================================================
                | REC RELEASE BUILD FAILED: Missing release signing credentials!
                |----------------------------------------------------------------------------------------
                | Missing variable(s): ${missingVars.joinToString(", ")}
                |
                | To build a signed release APK or Google Play App Bundle (AAB), define the required
                | environment variables:
                |   export REC_KEYSTORE_PATH="/path/to/rec-release.jks"
                |   export REC_KEYSTORE_PASSWORD="<keystore-password>"
                |   export REC_KEY_ALIAS="<key-alias>"
                |   export REC_KEY_PASSWORD="<key-password>"
                |
                | In CI (GitHub Actions), ensure secrets REC_KEYSTORE_BASE64, REC_KEYSTORE_PASSWORD,
                | REC_KEY_ALIAS, and REC_KEY_PASSWORD are set in Repository Secrets.
                |
                | For complete setup instructions, see README.md.
                |========================================================================================
                """.trimMargin()
            } else {
                """
                |========================================================================================
                | REC RELEASE BUILD FAILED: Keystore file not found!
                |----------------------------------------------------------------------------------------
                | Configured keystore path does not exist: $envKeystorePath
                | Verify that REC_KEYSTORE_PATH points to a valid .jks or .keystore file.
                |========================================================================================
                """.trimMargin()
            }
            throw GradleException(errorMessage)
        }
    }
}

dependencies {
    // Core & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle & Service
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Activity & Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Media3 ExoPlayer for In-App Vault Video Playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // Debugging UI
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unit Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation("org.json:json:20240303")

    // Instrumented Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
