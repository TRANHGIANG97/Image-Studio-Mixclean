plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.android.hilt)
}

android {
    namespace = "com.abizer_r.quickedit"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.colorpicker)
    implementation(libs.compose.screenshot)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.mlkit.face.detection)
    implementation(libs.vision.common)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.cloudy)
    implementation(libs.gpuimage)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.0")
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    implementation(libs.android.image.cropper)
    implementation(project(":core-data"))
    implementation(project(":core-domain"))
    implementation(project(":core-util"))
    implementation(project(":core-ad"))
    implementation(project(":studio"))
}
