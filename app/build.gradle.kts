import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.mori"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.balazstasi.mori"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0"
    }

    // Release signing reads keystore.properties, which stays out of git. Without it, release builds are unsigned.
    val keystoreProperties = rootProject.file("keystore.properties")
    if (keystoreProperties.exists()) {
        val keystore = Properties().apply { keystoreProperties.inputStream().use(::load) }
        signingConfigs {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
            // The git state of the checkout would end up in the APK, so builds from other clones would differ.
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
}
