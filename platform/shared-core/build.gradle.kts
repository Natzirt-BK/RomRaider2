plugins {
    `java-library`
}

group = "com.romraider2"
version = "1.1.0-rc4"

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

tasks.check {
    dependsOn("portableCoreCheck")
}
