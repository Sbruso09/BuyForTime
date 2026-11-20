plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.appsalario"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.appsalario"
        minSdk = 29
        targetSdk = 33
        versionCode = 10
        versionName = "4.2.1"
    }

    // Chiave di firma personale: NON e' nel repository. Se app/buyfortime.keystore esiste (in locale, o creata
    // dal workflow da un secret di GitHub) gli aggiornamenti si installano sopra l'app gia' presente;
    // altrimenti l'APK viene firmato con la chiave di debug.
    val chiave = file("buyfortime.keystore")
    val password = (project.findProperty("buyfortimePassword") as String?) ?: System.getenv("BUYFORTIME_KEY_PASSWORD")
    signingConfigs {
        if (chiave.exists() && password != null) {
            create("personale") {
                storeFile = chiave
                storePassword = password
                keyAlias = (project.findProperty("buyfortimeAlias") as String?) ?: System.getenv("BUYFORTIME_KEY_ALIAS") ?: "buyfortime"
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("personale") ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfigs.findByName("personale")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
