pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "image"
include(":app", ":lib", ":core-domain", ":core-ad", ":core-ui", ":core-data", ":core-util", ":quickedit")
project(":quickedit").projectDir = file("QuickEdit-Photo-Editor-main/quickedit")