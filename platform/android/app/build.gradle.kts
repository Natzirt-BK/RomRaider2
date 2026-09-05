import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    id("com.android.application")
}

android {
    namespace = "com.romraider.mobile"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.romraider.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 110403
        versionName = "1.1.0-rc4-preview3"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

abstract class StageAndroidBranding : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        fileSystemOperations.copy {
            from(sourceFile)
            into(outputDirectory.dir("drawable"))
        }
    }
}

val stageAndroidBranding by tasks.registering(StageAndroidBranding::class) {
    sourceFile.set(rootProject.layout.projectDirectory.file(
        "../../packaging/branding/linux/hicolor/192x192/apps/romraider2.png"))
    outputDirectory.set(layout.buildDirectory.dir("generated/branding-res"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            stageAndroidBranding, StageAndroidBranding::outputDirectory)
    }
}

dependencies {
    implementation(project(":shared-core"))
    testImplementation("junit:junit:4.13.2")
}
