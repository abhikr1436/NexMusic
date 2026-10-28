plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.nexmusic.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nexmusic.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // shortcut: shrinking off — the core (Ktor, NewPipe extractors, Room) needs keep rules first; add a proguard-rules.pro before shipping a small APK.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/*.md"
            excludes += "com/atilika/kuromoji/ipadic/*.bin"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugaring)

    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.common)
    implementation(projects.media3)

    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation(libs.compose.material3.lib)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:${libs.versions.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-process:${libs.versions.lifecycle.get()}")

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coroutines.android)
}
