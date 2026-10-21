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
        // 7-Zip-JBinding for Android (RAR5 archives)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "fwip"
include(":app")
