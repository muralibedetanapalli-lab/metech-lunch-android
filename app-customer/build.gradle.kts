plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val liveApiUrl: String = providers.gradleProperty("metech.liveApiUrl").get()

android {
    namespace = "com.metech.lunch.customer"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.metech.lunch.customer"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            // 10.0.2.2 is the host machine as seen from the Android emulator.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/api/\"")
            applicationIdSuffix = ".debug"
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$liveApiUrl\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Debug-signed build against the live API: installs on any phone, alongside the debug app.
        create("pilot") {
            initWith(getByName("debug"))
            buildConfigField("String", "API_BASE_URL", "\"$liveApiUrl\"")
            applicationIdSuffix = ".pilot"
            matchingFallbacks += listOf("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:auth"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.browser)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
