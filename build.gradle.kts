plugins {
    // 1. Ставим актуальную версию Kotlin (2.2.20 еще нет, последняя 2.1.0)
    kotlin("jvm") version "2.2.20"

    // 2. Версия плагина сериализации должна совпадать с версией Kotlin
    kotlin("plugin.serialization") version "2.2.20"

    // 3. !!! ОБЯЗАТЕЛЬНО для Kotlin 2.0+: Плагин компилятора Compose
    // Его версия ТОЖЕ должна совпадать с версией Kotlin
    kotlin("plugin.compose") version "2.2.20"

    // 4. Сам плагин Compose Multiplatform (у него своя нумерация, актуальная 1.7.0 или 1.7.1)
    id("org.jetbrains.compose") version "1.7.1"
}

group = "org.chokopieum.software"
version = "0.1.2-prealpha"

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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.slf4j:slf4j-simple:2.0.12")


    // === Работа с JSON (Сериализация) ===
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // === Сеть (HTTP Client) ===
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-cio:3.0.1") // Движок для Ktor
    // Для автоматической работы с JSON в Ktor
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
}

kotlin {
    // Java 21
    jvmToolchain(21)
}

compose.desktop {
    application {
        // !!! ВАЖНО !!!
        // Если в твоем файле Main.kt написано "package org.chokopieum.software",
        // то строку ниже нужно заменить на: "org.chokopieum.software.MainKt"
        // Если package нет, оставь просто "MainKt"
        mainClass = "MainKt"
    }
}

//        nativeDistributions {
//            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
//            packageName = "MateriaKraft"
//            packageVersion = "1.0.0"
//
//            // Настройки для Windows (когда будет иконка)
//            windows {
//                // menuGroup = "MateriaKraft"
//
//             // upgradeUuid = "..." // сгенерируй UUID, чтобы установщик обновлял старую версию
//            }
//        }
//    }
//}