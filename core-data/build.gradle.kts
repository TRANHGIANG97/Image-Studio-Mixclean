plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.thgiang.image.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.vision.common)
    implementation(libs.play.services.mlkit.subject.segmentation)
    implementation(libs.play.services.base)
    implementation(libs.mlkit.segmentation.selfie)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
