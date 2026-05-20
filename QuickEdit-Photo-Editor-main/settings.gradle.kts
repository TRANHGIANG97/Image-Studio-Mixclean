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

rootProject.name = "QuickEdit"
include(":app")
include(":quickedit")
include(":core-data")
include(":core-domain")
include(":core-util")
include(":core-ad")
include(":studio_edit")

project(":core-data").projectDir = file("../core-data")
project(":core-domain").projectDir = file("../core-domain")
project(":core-util").projectDir = file("../core-util")
project(":core-ad").projectDir = file("../core-ad")
project(":studio_edit").projectDir = file("../studio_edit")
