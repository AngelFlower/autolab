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

    // Fase 7H: tres puntos de entrada al auto, cada uno en su propio paquete para que
    // convivan instalados y el host no cachee uno sobre otro (ver Fase 4 en RESULTS.md).
    // Asi en un solo viaje al auto se ve cual(es) lista el head unit real.
    flavorDimensions += "entrada"
    productFlavors {
        // Activity CAR_LAUNCHER (juego "parked"): lo que funciona en el DHU.
        create("parked") {
            dimension = "entrada"
        }
        // CarAppService de plantillas (menu de laboratorios de la Fase 1).
        create("templates") {
            dimension = "entrada"
            applicationIdSuffix = ".tpl"
        }
        // MediaBrowserService: la integracion mas antigua y soportada por cualquier auto.
        create("media") {
            dimension = "entrada"
            applicationIdSuffix = ".media"
        }
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
