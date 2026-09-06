import java.util.Properties

plugins {
    `java-library`
}

group = "com.romraider2"
version = Properties().apply {
    file("../../version.properties").inputStream().use { load(it) }
}.getProperty("version.buildnumber")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}

tasks.register<JavaExec>("portableCoreCheck") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.romraider.portable.PortableCoreCheck")
}

tasks.test {
    enabled = false
}

tasks.register<JavaExec>("portableCsvStreamingCheck") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.romraider.portable.PortableCsvStreamingCheck")
    maxHeapSize = "64m"
}

tasks.check {
    dependsOn("portableCoreCheck", "portableCsvStreamingCheck")
}
