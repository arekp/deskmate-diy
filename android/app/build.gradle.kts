import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Sekrety czytamy z local.properties (poza repozytorium), patrz local.properties.example
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(key: String, default: String = ""): String =
    (localProps.getProperty(key) ?: default).replace("\"", "\\\"")

android {
    namespace = "pl.arekp.deskmate"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.arekp.deskmate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${secret("anthropic.apiKey")}\"")
        buildConfigField("String", "ANTHROPIC_MODEL", "\"${secret("anthropic.model", "claude-haiku-5-5")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${secret("gemini.apiKey")}\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${secret("openrouter.apiKey")}\"")
        buildConfigField("String", "SCREEN_HELPER_URL", "\"${secret("screenHelper.url")}\"")
        // Wersja z CI (skrót commita) – trafia do nagłówka logów
        buildConfigField("String", "GIT_SHA", "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "lokalna"}\"")
        buildConfigField("String", "BUILD_NUMBER", "\"${System.getenv("GITHUB_RUN_NUMBER") ?: "-"}\"")
        buildConfigField("String", "SCREEN_HELPER_TOKEN", "\"${secret("screenHelper.token")}\"")
    }

    signingConfigs {
        // Wspólny klucz testowy z repozytorium (NIE do publikacji w sklepie).
        // Dzięki temu każdy build z GitHub Actions instaluje się jako aktualizacja poprzedniego.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Kamera + wykrywanie twarzy (działa lokalnie na telefonie)
    val cameraX = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX") // podgląd kamery w rogu ekranu
    implementation("com.google.mlkit:face-detection:16.1.7")
    // Rozpoznawanie otoczenia na telefonie (modele w APK, bez internetu)
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // HTTP do API modelu językowego i pomocnika ekranu
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Testy jednostkowe logiki (JVM, bez telefonu)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
