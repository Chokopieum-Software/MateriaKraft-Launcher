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
//    implementation(compose.material3)
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
        // !!! ВАЖНО !!!
        // Если в твоем файле Main.kt написано "package org.chokopieum.software",
        // то строку ниже нужно заменить на: "org.chokopieum.software.MainKt"
        // Если package нет, оставь просто "MainKt"
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe)
        }
    }
}
