plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.personal.autolab"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.personal.autolab"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
}
