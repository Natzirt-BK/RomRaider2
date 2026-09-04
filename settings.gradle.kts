pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "romraider2-desktop"
include(":ui:compose-logger")
include(":ui:javafx-desktop")
include(":platform:shared-core")
