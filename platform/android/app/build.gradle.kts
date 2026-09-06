import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject
import java.util.Properties

plugins {
    id("com.android.application")
}

val distributionSigning = listOf("RR2_ANDROID_KEYSTORE", "RR2_ANDROID_KEYSTORE_PASSWORD",
    "RR2_ANDROID_KEY_ALIAS", "RR2_ANDROID_KEY_PASSWORD").associateWith {
    providers.environmentVariable(it).orNull
}
val requireDistributionSigning = providers.environmentVariable("RR2_ANDROID_SIGNING_REQUIRED")
    .orNull == "true"
val hasDistributionSigning = distributionSigning.values.all { !it.isNullOrBlank() }
if ((requireDistributionSigning || distributionSigning.values.any { it != null }) && !hasDistributionSigning) {
    throw GradleException("Complete Android distribution signing credentials are required; refusing debug-key fallback.")
}
if (hasDistributionSigning && gradle.startParameter.isConfigurationCacheRequested) {
    throw GradleException("Use --no-configuration-cache for signed distribution builds to avoid caching credentials.")
}

val releaseVersion = Properties().apply {
    file("../../../version.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.romraider.mobile"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        resValues = true
    }

    defaultConfig {
        applicationId = "com.romraider.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("rr2AndroidVersionCode").orNull?.toInt()
            ?: releaseVersion.getProperty("version.android.code").toInt()
        versionName = providers.gradleProperty("rr2AndroidVersionName").orNull
            ?: releaseVersion.getProperty("version.buildnumber")
        testInstrumentationRunner = "com.romraider.mobile.LoggerSetupInstrumentation"
        require(versionCode!! > 0 && versionName!!.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            "Android versions must use positive versionCode and numeric major.minor.patch"
        }
        if (hasDistributionSigning) {
            require(versionName == releaseVersion.getProperty("version.buildnumber") &&
                versionCode == releaseVersion.getProperty("version.android.code").toInt()) {
                "Signed distribution builds must use the shared release version and versionCode"
            }
        }
    }

    testBuildType = providers.gradleProperty("rr2AndroidTestBuildType").orNull ?: "debug"

    if (hasDistributionSigning) {
        signingConfigs.create("distribution") {
            storeFile = file(distributionSigning.getValue("RR2_ANDROID_KEYSTORE")!!)
            storePassword = distributionSigning.getValue("RR2_ANDROID_KEYSTORE_PASSWORD")
            keyAlias = distributionSigning.getValue("RR2_ANDROID_KEY_ALIAS")
            keyPassword = distributionSigning.getValue("RR2_ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // Retain the installed package identity; this is not a version label.
            applicationIdSuffix = ".preview"
            if (hasDistributionSigning) signingConfig = signingConfigs.getByName("distribution")
        }
        create("openportDiagnostic") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview.openporttest"
            resValue("string", "app_name", "RomRaider2 OpenPort Test")
            signingConfig = signingConfigs.getByName(if (hasDistributionSigning) "distribution" else "debug")
            matchingFallbacks += listOf("debug")
        }
        create("automation") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".automation"
            resValue("string", "app_name", "RomRaider2 Automation")
            matchingFallbacks += listOf("debug")
        }
        release {
            if (hasDistributionSigning) signingConfig = signingConfigs.getByName("distribution")
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
