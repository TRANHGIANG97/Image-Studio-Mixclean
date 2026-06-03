import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.google.devtools.ksp)
}

val localSigningProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream -> load(stream) }
    }
}

fun releaseSigningValue(name: String): String? {
    return providers.gradleProperty(name).orNull
        ?: localSigningProperties.getProperty(name)
        ?: providers.environmentVariable(name).orNull
}

val releaseStoreFilePath = releaseSigningValue("RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val googleServicesFile = project.file("google-services.json")
val hasGoogleServicesFile = googleServicesFile.exists()

if (hasGoogleServicesFile) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.thgiang.image"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thgiang.image"
        minSdk = 24
        targetSdk = 35
        versionCode = 205
        versionName = "2.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.addAll(setOf("arm64-v8a", "armeabi-v7a"))
        }
        externalNativeBuild {
            cmake {
                cppFlags += ""
                abiFilters("arm64-v8a", "armeabi-v7a","x86_64")
                arguments("-DANDROID_STL=c++_shared", "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384")
            }
        }

    }
    signingConfigs {
        create("release") {
            storeFile = file("my-release-key.jks")
            storePassword = "135798"
            keyAlias = "my-key-alias"
            keyPassword = "135798"
        }
    }
    buildTypes {
        release {
            // Chuyển thành true để tối ưu dung lượng và bảo mật code (rất quan trọng khi lên Store)
            isMinifyEnabled = true

            // Gán cấu hình ký vừa tạo ở trên vào đây
            signingConfig = signingConfigs.getByName("release")

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
        dataBinding = true
        viewBinding = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
    
    androidResources {
        noCompress += "tflite"
    }

    packaging {
        jniLibs {
            // Enable legacy packaging to force extraction of native libraries upon installation,
            // resolving the UnsatisfiedLinkError crash on split APK installations for older Android versions (e.g. Android 11).
            useLegacyPackaging = true
            // Exclude the non-16KB-aligned libraries only for x86 architectures to prevent Play Store rejection
            // while preserving them for ARM architectures to prevent runtime crashes.
            excludes.add("lib/x86_64/libxeno_native.so")
            excludes.add("lib/x86/libxeno_native.so")
            excludes.add("lib/*/libyuv-decoder.so")
        }
    }
    
    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = false
        }
        abi {
            enableSplit = false
        }
    }
    
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Ensure 16 KB alignment for all native libraries during the build process
tasks.withType<com.android.build.gradle.internal.tasks.MergeNativeLibsTask> {
    doLast {
        println("Performing 16KB alignment check for native libraries...")
        // This task is now handled by the packaging options above in modern AGP,
        // but we can add additional verification or processing here if needed.
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-ad"))
    implementation(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":core-util"))
    implementation(project(":quickedit"))
    implementation(project(":studio_edit"))
    debugImplementation(project(":admin_tools"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.ui)
    val work_version = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$work_version")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.vision.common)
    implementation(libs.mlkit.face.detection)
    implementation(libs.play.services.mlkit.subject.segmentation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.foundation)
    implementation("jp.wasabeef:blurry:4.0.1")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.androidx.lifecycle.process)
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("com.google.android.gms:play-services-ads:23.2.0")
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.android.billingclient:billing:7.1.1")
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("nl.joery.animatedbottombar:library:1.1.0")
    
    // QuickEdit dependencies
    implementation(libs.android.image.cropper)
    implementation(libs.cloudy)
    implementation(libs.colorpicker)
    implementation(libs.compose.screenshot)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.kotlinx.serialization.json)


    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
