import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    // Плагин Compose Multiplatform
    id("org.jetbrains.compose") version "1.9.3"
}

group = "org.chokopieum.software"
version = "1.0.0"

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

    // === Логгирование ===
    implementation("org.slf4j:slf4j-simple:2.0.17")


    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // === Сеть (HTTP Client) ===
    // Ktor 2.3.13 - стабильная версия
    implementation("io.ktor:ktor-client-core:2.3.13")
    implementation("io.ktor:ktor-client-cio:2.3.13") // Движок для Ktor
    // Для автоматической работы с JSON в Ktor
    implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")

    // === Работа с архивами ===
    implementation("org.apache.commons:commons-compress:1.26.2")
    // Явно указываем безопасную версию commons-lang3 для устранения уязвимости
    implementation("org.apache.commons:commons-lang3:3.18.0")
}

kotlin {
    // Java 25
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            packageName = "materia-launcher"
            packageVersion = "1.0.0"
            description = "Modern launcher for Minecraft"
            vendor = "Chokopieum Software"
            copyright = "© 2025 Chokopieum Software"

            // Настройка для Linux
            linux {
                appCategory = "Game"
                // iconFile.set(project.file("src/main/resources/icon.png"))
            }

            // Настройка для Windows
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "019b375c-3319-7eec-8098-e50668c43b5a"
            }

            // Логика выбора форматов в зависимости от ОС сборки
            val osName = System.getProperty("os.name").lowercase()
            when {
                osName.contains("win") -> {
                    targetFormats(TargetFormat.Msi, TargetFormat.Exe)
                }
                osName.contains("linux") -> {
                    targetFormats(TargetFormat.Deb, TargetFormat.Rpm)
                }
                osName.contains("mac") -> {
                    targetFormats(TargetFormat.Dmg)
                }
            }
        }
    }
}

// === ЗАДАЧА ДЛЯ СОЗДАНИЯ ПОРТАТИВНОГО TAR.GZ ===
tasks.register<Tar>("packagePortable") {
    group = "distribution"
    description = "Packages the app as a portable tar.gz archive"

    // Зависим от задачи создания структуры приложения
    dependsOn("createDistributable")

    // Определяем архитектуру (amd64 или aarch64)
    val arch = System.getProperty("os.arch").let {
        if (it == "aarch64") "arm64" else "amd64" // Нормализуем имя
    }

    // Настраиваем имя архива
    archiveBaseName.set("materia-launcher")
    archiveVersion.set(version.toString())
    archiveClassifier.set("linux-$arch") // Добавляем архитектуру в имя
    archiveExtension.set("tar.gz")
    compression = Compression.GZIP

    // Откуда брать файлы (стандартный путь output Compose)
    val distributionDir = project.layout.buildDirectory.dir("compose/binaries/main/app")

    from(distributionDir) {
        // Сохраняем права на выполнение (chmod +x) для скриптов и бинарников
        eachFile {
            if (this.name.endsWith(".sh") || !this.name.contains(".")) {
                mode = 493 // 0755 в десятичной системе
            }
        }
    }

    // Куда положить готовый архив
    destinationDirectory.set(project.layout.buildDirectory.dir("compose/binaries/main/portable"))

    doLast {
        println("Portable archive created at: ${destinationDirectory.get()}/${archiveFileName.get()}")
    }
}