plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.jitesh.glassisle"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.jitesh.glassisle"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }
    val ksPath = System.getenv("GLASSISLE_KEYSTORE")
    if (ksPath != null && file(ksPath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("GLASSISLE_KS_PASS")
                keyAlias = "glassisle"
                keyPassword = System.getenv("GLASSISLE_KS_PASS")
            }
        }
        buildTypes { getByName("release") { signingConfig = signingConfigs.getByName("release") } }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
