plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.georg912.plugnap"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.georg912.plugnap"
        // ZenDeviceEffects / AutomaticZenRule.Builder only exist from Android 15 (API 35)
        minSdk = 35
        targetSdk = 35
        versionCode = 10
        versionName = "1.6.0"
    }

    // Release signing: keystore + password are NOT in the repo but in
    // ~/.gradle/gradle.properties (ZENDOCK_KEYSTORE, ZENDOCK_KEYSTORE_PW,
    // ZENDOCK_KEY_ALIAS). Without these properties the build is unsigned.
    val ksPath = providers.gradleProperty("ZENDOCK_KEYSTORE").orNull
    if (ksPath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = providers.gradleProperty("ZENDOCK_KEYSTORE_PW").get()
                keyAlias = providers.gradleProperty("ZENDOCK_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("ZENDOCK_KEYSTORE_PW").get()
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
            if (ksPath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
