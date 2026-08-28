plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.georg912.plugnap"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.georg912.plugnap"
        // ZenDeviceEffects / AutomaticZenRule.Builder only exist from Android 15 (API 35)
        minSdk = 35
        targetSdk = 36
        versionCode = 12
        versionName = "1.7.1"
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
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.transition:transition-ktx:1.5.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
