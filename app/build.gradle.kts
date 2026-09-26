import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "fr.edcapp.essencecam"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.edcapp.essencecam"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Aucun serveur de routage par défaut : distances estimées, sauf serveur OSRM saisi par l'utilisateur.
        buildConfigField("String", "DEFAULT_ROUTING_SERVER", "\"\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        // Clé de release : jamais dans le dépôt. keystore.properties (ignoré par git) à la racine :
        // storeFile=..., storePassword=..., keyAlias=..., keyPassword=... Sans ce fichier, la release reste non signée.
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 : retire le code et les ressources inutilisés. Les architectures x86 ne servent qu'aux
            // émulateurs : gardées en debug (contributeurs), retirées de la release (APK plus léger).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
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
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    // LifecycleService : fournit un LifecycleOwner à DashcamService, requis par
    // CameraX (bindToLifecycle) hors d'une Activity.
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // CarConnection uniquement (détection connexion Android Auto) — pas de CarAppService,
    // pas de template UI, donc aucune contrainte Play Store.
    implementation("androidx.car.app:app:1.7.0")

    // CameraX pour le module Dashcam — dernière version stable au 2026-09-18 (1.7.0 encore en
    // alpha, vérifié via maven-metadata.xml de Google Maven).
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")

    testImplementation("junit:junit:4.13.2")
}
