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

val localPropsFile = rootProject.file("local.properties")
val localProps = Properties().apply {
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

fun getSigningProperty(key: String): String? {
    // 1. Environment variable (CI/CD or shell export)
    System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it }

    // 2. Gradle project property (-P or gradle.properties)
    (project.findProperty(key) as? String)?.takeIf { it.isNotBlank() }?.let { return it }

    // 3. rootProject local.properties
    localProps.getProperty(key)?.takeIf { it.isNotBlank() }?.let {
        return it.trim().removeSurrounding("\"").removeSurrounding("'")
    }

    return null
}

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
            val keystorePath = getSigningProperty("REC_KEYSTORE_PATH")
            val keystorePassword = getSigningProperty("REC_KEYSTORE_PASSWORD")
            val keyAlias = getSigningProperty("REC_KEY_ALIAS")
            val keyPassword = getSigningProperty("REC_KEY_PASSWORD")

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
            val envKeystorePath = getSigningProperty("REC_KEYSTORE_PATH")
            val envKeystorePassword = getSigningProperty("REC_KEYSTORE_PASSWORD")
            val envKeyAlias = getSigningProperty("REC_KEY_ALIAS")
            val envKeyPassword = getSigningProperty("REC_KEY_PASSWORD")

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
                | To build a signed release APK or Google Play App Bundle (AAB), configure credentials
                | in your 'local.properties' file:
                |   REC_KEYSTORE_PATH=/path/to/rec-release.jks
                |   REC_KEYSTORE_PASSWORD=your_keystore_password
                |   REC_KEY_ALIAS=your_key_alias
                |   REC_KEY_PASSWORD=your_key_password
                |
                | Or export them as environment variables:
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
                | Verify that REC_KEYSTORE_PATH in local.properties or environment points to a valid file.
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
