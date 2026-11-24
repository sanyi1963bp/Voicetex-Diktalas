plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

android {
    namespace = "hu.voicetex.billentyuzet"
    compileSdk = 35

    defaultConfig {
        applicationId = "hu.voicetex.billentyuzet"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
    }

    buildFeatures {
        buildConfig = true
    }

    // Két kiadás: a "teljes" a lebegő gombbal (USB-n telepíthető),
    // a "lite" csak a billentyűzettel (linkről is telepíthető, mert nincs benne
    // kisegítő szolgáltatás, amit a Play Protect letiltana).
    flavorDimensions += "kiadas"
    productFlavors {
        create("teljes") {
            dimension = "kiadas"
            buildConfigField("boolean", "LEBEGO_GOMB", "true")
        }
        create("lite") {
            dimension = "kiadas"
            applicationIdSuffix = ".lite"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "LEBEGO_GOMB", "false")
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
            // Debug kulccsal írjuk alá, hogy a release APK azonnal telepíthető legyen.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
