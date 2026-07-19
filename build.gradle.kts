// Top-level build file where you can add configuration options common to all sub-projects/modules.
import com.android.build.gradle.BaseExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.hilt) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Hilt Gradle plugin injects kapt-style AP options into javaCompileOptions.
// Library modules use KSP for Hilt; clear stale javac processor flags on libraries only.
// The app module still needs hiltJavaCompile (e.g. Hilt_ImageApp) — do not clear AP args there.
subprojects {
    plugins.withId("com.google.dagger.hilt.android") {
        afterEvaluate {
            if (plugins.hasPlugin("com.android.library")) {
                extensions.findByType(BaseExtension::class.java)?.apply {
                    defaultConfig {
                        javaCompileOptions {
                            annotationProcessorOptions {
                                arguments.clear()
                            }
                        }
                    }
                }
            }
        }
    }
}
