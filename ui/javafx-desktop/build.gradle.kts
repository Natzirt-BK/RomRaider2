plugins {
    java
}

group = "com.romraider2"
version = "1.1.0-rc4"

val javafxVersion = "21.0.10"
val hostPlatform = when {
    System.getProperty("os.name").lowercase().contains("win") -> "win"
    System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64") ->
        "mac-aarch64"
    System.getProperty("os.name").lowercase().contains("mac") -> "mac"
    System.getProperty("os.arch").lowercase() in setOf("aarch64", "arm64") ->
        "linux-aarch64"
    else -> "linux"
}
val romraiderJar = providers.gradleProperty("romraiderJar")
    .orElse(rootProject.layout.projectDirectory.file(
        "build/${if (hostPlatform == "win") "windows" else "linux"}/lib/RomRaider2.jar")
        .asFile.absolutePath)

val javafxLinuxRuntime by configurations.creating
val javafxWindowsRuntime by configurations.creating

dependencies {
    compileOnly(files(romraiderJar))
    listOf("base", "graphics", "controls").forEach { module ->
        implementation("org.openjfx:javafx-$module:$javafxVersion:$hostPlatform")
        javafxLinuxRuntime("org.openjfx:javafx-$module:$javafxVersion:linux")
        javafxWindowsRuntime("org.openjfx:javafx-$module:$javafxVersion:win")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.14.0")
    testImplementation(files(romraiderJar))
    testRuntimeOnly(fileTree(rootProject.layout.projectDirectory.dir(
        "lib/common")) { include("*.jar") })
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("romraider2-javafx-desktop")
}

fun registerStage(name: String, platform: String, artifactSuffix: String,
        runtime: Configuration) = tasks.register<Sync>(name) {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(runtime) {
        include("*-$artifactSuffix.jar")
    }
    into(rootProject.layout.buildDirectory.dir("javafx/$platform"))
}

registerStage("stageJavaFxLinux", "linux", "linux", javafxLinuxRuntime)
registerStage("stageJavaFxWindows", "windows", "win", javafxWindowsRuntime)

dependencyLocking {
    lockAllConfigurations()
}
