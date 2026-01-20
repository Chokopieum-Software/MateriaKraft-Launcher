@file:Suppress("DEPRECATION")
import org.gradle.jvm.tasks.Jar
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
}

// --- Логика определения версии ---
fun getVersionInfo(): Pair<String, String> {
    val ci = System.getenv("CI") == "true"
    val githubRef = System.getenv("GITHUB_REF_NAME") ?: ""
    val githubRefType = System.getenv("GITHUB_REF_TYPE") ?: ""
    val githubRunNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
    val isAurBuild = System.getenv("BUILD_SOURCE") == "AUR"
    val isTag = githubRefType == "tag"
    val isMain = githubRef == "master"
    val isDev = githubRef == "dev"

    // Проверяем наличие приватного ключа разработчика
    val devKeystore = File(System.getProperty("user.home"), ".ssh/keystore.jks")
    val isDeveloperBuild = devKeystore.exists()

    val buildPropsFile = project.file("build.properties")
    val buildProps = Properties()
    if (buildPropsFile.exists()) {
        FileInputStream(buildPropsFile).use { buildProps.load(it) }
    }
    var buildNumber = buildProps.getProperty("buildNumber", "0").toInt()

    val version: String
    when {
        isTag -> {
            version = githubRef
        }
        isAurBuild -> {
            version = "AUR" // Или можно использовать другую логику для версии из AUR
        }
        ci && isMain -> {
            version = "Beta"
            buildNumber = githubRunNumber.toInt()
        }
        ci && isDev -> {
            version = "Canary"
            buildNumber = githubRunNumber.toInt()
        }
        isDeveloperBuild -> {
            version = "Develop Build"
            buildNumber++
            buildProps.setProperty("buildNumber", buildNumber.toString())
            FileOutputStream(buildPropsFile).use { buildProps.store(it, "Auto-incremented by Gradle") }
        }
        else -> {
            version = "Community Build"
            // Номер сборки не инкрементируется для сборок сообщества
        }
    }
    return Pair(version, buildNumber.toString())
}

val (appVersion, appBuildNumber) = getVersionInfo()

group = "org.chokopieum.software"
version = appVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(project(":MLGD"))
    implementation(compose.desktop.currentOs)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.components:components-resources:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.microsoft.azure:msal4j:1.23.1")
    implementation("com.github.javakeyring:java-keyring:1.0.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.akuleshov7:ktoml-core-jvm:0.7.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.39.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.13")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
    implementation("io.ktor:ktor-client-logging:2.3.13")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // Exposed for SQLite
    implementation("org.jetbrains.exposed:exposed-core:0.52.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.52.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.52.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")

    testImplementation(kotlin("test-junit5"))
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            packageName = "materia-launcher"
            val osName = System.getProperty("os.name")
            if (osName.lowercase(Locale.getDefault()).contains("win")) {
                packageVersion = "1.0.$appBuildNumber"
            } else {
                packageVersion = appVersion
            }
            description = "Modern launcher for Minecraft"
            vendor = "Chokopieum Software"
            copyright = "© 2025 Chokopieum Software"

            // Собираем только EXE для Windows со встроенной JDK
            // targetFormats(TargetFormat.Exe) // Временно отключено из-за проблем с версией

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "019b375c-3319-7eec-8098-e50668c43b5a"
            }
        }
    }
}

afterEvaluate {
    tasks.named("packageUberJarForCurrentOS", Jar::class) {
        archiveFileName.set("materia-launcher.jar")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Copy>("processResources") {
    doLast {
        val props = Properties()
        props.setProperty("version", appVersion)
        props.setProperty("buildNumber", appBuildNumber)
        props.setProperty("buildSource", when {
            System.getenv("BUILD_SOURCE") == "AUR" -> "AUR"
            System.getenv("CI") == "true" -> "GitHub"
            File(System.getProperty("user.home"), ".ssh/keystore.jks").exists() -> "Local"
            else -> "Community"
        })

        val wrapperProps = Properties()
        project.file("gradle/wrapper/gradle-wrapper.properties").inputStream().use { wrapperProps.load(it) }
        val gradleVersion = wrapperProps.getProperty("distributionUrl").substringAfterLast("gradle-").substringBefore("-bin")
        props.setProperty("gradleVersion", gradleVersion)

        file("$destinationDir/app.properties").writer().use {
            props.store(it, "Generated by Gradle")
        }
    }
}
// build.gradle.kts

compose.resources {
    // Укажите тот пакет, который вы хотите использовать
    packageOfResClass = "org.chokopieum.software.materia_launcher.generated.resources."

    // Если нужно, чтобы ресурсы были видны в других модулях
    publicResClass = true
    generateResClass = always
}
