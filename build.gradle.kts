@file:Suppress("DEPRECATION")
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy
import org.gradle.jvm.tasks.Jar
import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    // Плагин Compose Multiplatform
    id("org.jetbrains.compose") version "1.10.0-rc02"
}

val packageVersion = "1.0.60000"

group = "org.chokopieum.software"
version = packageVersion

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // === UI (Compose) ===
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(compose.material3)
    // === Асинхронность (Coroutines) ===
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("com.microsoft.azure:msal4j:1.23.1")
    implementation("com.github.javakeyring:java-keyring:1.0.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.akuleshov7:ktoml-core-jvm:0.7.1")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.39.0")
    // === Логгирование ===
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // === Сеть (HTTP Client) ===
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.13") // Движок для Ktor
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
    implementation("io.ktor:ktor-client-logging:2.3.13")

    // === Работа с архивами ===
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.apache.commons:commons-lang3:3.18.0")

    // === Тестирование ===
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.3")
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        jvmArgs += listOf(
            "-XX:+UseZGC",
            "-Xms512m",
            "-Xmx512m",
            "-XX:+DisableExplicitGC"
        )

        nativeDistributions {
            packageName = "materia-launcher"
            packageVersion = packageVersion
            description = "Modern launcher for Minecraft"
            vendor = "Chokopieum Software"
            copyright = "© 2025 Chokopieum Software"

            // Собираем только EXE для Windows со встроенной JDK
            targetFormats(TargetFormat.Exe)

            windows {
                menu = true
                shortcut = true
                upgradeUuid = "019b375c-3319-7eec-8098-e50668c43b5a"
            }
        }
    }
}

// Конфигурация для задачи создания Uber-JAR
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
        // Определяем источник сборки
        val buildSource = when {
            System.getenv("BUILD_SOURCE") == "AUR" -> "AUR"
            System.getenv("GITHUB_ACTIONS") == "true" -> "GitHub"
            else -> "Local"
        }

        val buildPropertiesFile = project.rootProject.file("build.properties")
        val buildProps = Properties().apply {
            buildPropertiesFile.inputStream().use { load(it) }
        }
        val currentBuild = buildProps.getProperty("buildNumber").toInt()
        var finalBuildNumber = currentBuild

        // Инкрементируем номер сборки только для Local и GitHub
        if (buildSource == "Local" || buildSource == "GitHub") {
            finalBuildNumber = currentBuild + 1
            buildProps.setProperty("buildNumber", finalBuildNumber.toString())
            buildPropertiesFile.outputStream().use { buildProps.store(it, null) }
        }

        // Генерируем app.properties
        val props = Properties()
        props.setProperty("version", packageVersion)
        props.setProperty("buildNumber", finalBuildNumber.toString())
        props.setProperty("buildSource", buildSource)

        // Извлекаем версию Gradle
        val wrapperProps = Properties()
        project.file("gradle/wrapper/gradle-wrapper.properties").inputStream().use { wrapperProps.load(it) }
        val gradleVersion = wrapperProps.getProperty("distributionUrl").substringAfterLast("gradle-").substringBefore("-bin")
        props.setProperty("gradleVersion", gradleVersion)

        // Записываем файл в выходную директорию задачи
        file("$destinationDir/app.properties").writer().use {
            props.store(it, null)
        }
    }
}
