import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
}

group = "com.romraider2"
version = "1.1.0-rc4"

val hostOs = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows"
    System.getProperty("os.name").lowercase().contains("mac") -> "macos"
    else -> "linux"
}
val hostArch = when (System.getProperty("os.arch").lowercase()) {
    "aarch64", "arm64" -> "arm64"
    else -> "x64"
}
val hostStage = if (hostOs == "macos") "$hostOs-$hostArch" else hostOs
val romraiderJar = providers.gradleProperty("romraiderJar")
    .orElse(rootProject.layout.projectDirectory.file(
        "build/$hostOs/lib/RomRaider2.jar").asFile.absolutePath)
val auditedDesktopRuntimes by configurations.creating

dependencies {
    compileOnly(files(romraiderJar))
    implementation(compose.desktop.currentOs)
    auditedDesktopRuntimes(
        "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.12.0")
    auditedDesktopRuntimes(
        "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.12.0")
    auditedDesktopRuntimes(
        "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:1.12.0")
    auditedDesktopRuntimes(
        "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.12.0")

    testImplementation(kotlin("test"))
    testImplementation(files(romraiderJar))
    testRuntimeOnly(fileTree(rootProject.layout.projectDirectory.dir(
        "lib/common")) { include("*.jar") })
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencyLocking {
    lockAllConfigurations()
    // Compose selects one checksum-audited native runtime per host.
    // Keeping every host runtime in a single strict lock configuration makes
    // the other operating-system entries appear missing on macOS and Windows.
    ignoredDependencies.add(
        "org.jetbrains.compose.desktop:desktop-jvm-*")
    ignoredDependencies.add(
        "org.jetbrains.skiko:skiko-awt-runtime-*")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
    if (name.endsWith("VisualFixture", ignoreCase = true)
        || name == "visualFixture") {
        systemProperty("romraider2.settings.dir",
            rootProject.layout.buildDirectory.dir("visual-fixture-settings")
                .get().asFile.absolutePath)
        systemProperty("romraider2.default.settings.file",
            rootProject.layout.projectDirectory.file("settings.xml")
                .asFile.absolutePath)
    }
}

tasks.register<JavaExec>("visualFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.romraider2.logger.compose.LoggerWorkspaceVisualFixtureKt")
    providers.gradleProperty("visualArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotBlank))
    }
    providers.gradleProperty("uiProfile").orNull?.let {
        systemProperty("romraider2.ui.profile", it)
    }
}

tasks.register<JavaExec>("calibrationVisualFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "com.romraider2.logger.compose.CalibrationWorkspaceVisualFixtureKt")
    providers.gradleProperty("visualArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotBlank))
    }
    providers.gradleProperty("uiProfile").orNull?.let {
        systemProperty("romraider2.ui.profile", it)
    }
}

tasks.register<JavaExec>("comparisonVisualFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "com.romraider2.logger.compose.RomComparisonWorkspaceVisualFixtureKt")
    providers.gradleProperty("visualArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotBlank))
    }
}

tasks.register<JavaExec>("editorNavigationVisualFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "com.romraider2.logger.compose.EditorNavigationWorkspaceVisualFixtureKt")
    providers.gradleProperty("visualArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotBlank))
    }
}

tasks.register<JavaExec>("desktopShellVisualFixture") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set(
        "com.romraider2.logger.compose.ComposeDesktopShellVisualFixtureKt")
    providers.gradleProperty("visualArgs").orNull?.let {
        args(it.split(Regex("\\s+")).filter(String::isNotBlank))
    }
}

tasks.register("resolveAuditedDesktopRuntimes") {
    doLast {
        auditedDesktopRuntimes.resolve()
    }
}

tasks.jar {
    archiveBaseName.set("romraider2-compose-logger")
}

tasks.register<Sync>("stageLoggerWorkspace") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    configurations.runtimeClasspath.get().resolvedConfiguration
        .resolvedArtifacts.forEach { artifact ->
            from(artifact.file) {
                rename {
                    "${artifact.moduleVersion.id.group.replace('.', '-')}-${it}"
                }
            }
        }
    into(rootProject.layout.buildDirectory.dir("compose/$hostStage"))
}
