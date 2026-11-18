pluginManagement {
    repositories {
        google()
        // Google-hosted mirror of Maven Central improves reliability on CI runners
        // when repo.maven.apache.org is throttled or intermittently unavailable.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Keep the mirror before Maven Central so Android builds have a resilient fallback.
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}

rootProject.name = "YovoiceAndroid"
include(":app")
