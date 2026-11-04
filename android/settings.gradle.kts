// Self-contained Android build: nothing here may reference files outside
// android/ (see docs/repo-structure.md).
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    // Lets Gradle download the daemon JDK named in gradle/gradle-daemon-jvm.properties
    // when it is not installed, so Android Studio and ./gradlew both run on Java 25.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SplitParty"
include(":app")
