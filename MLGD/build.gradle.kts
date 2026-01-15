plugins {
    kotlin("jvm") version "2.3.0" // Убедитесь, что эта версия совместима с Ktor
    kotlin("plugin.serialization") version "2.3.0"
    id("org.graalvm.buildtools.native") version "0.11.1" // Лучше обновить до актуальной, если есть (напр. 0.10.5+)
}

group = "org.chokopieum.software"
version = "Develop Build"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.13" // Проверьте актуальность для Kotlin 2.3.0

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")

    // Если реально нужна рефлексия (судя по вашим флагам), раскомментируйте:
    // implementation(kotlin("reflect"))

    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(kotlin("test"))
}

graalvmNative {
    binaries {
        named("main") {
            // Оставляем вашу настройку Java (25 или 21 - как у вас стоит)
            javaLauncher.set(project.javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
            })

            imageName.set("mlgd")
            mainClass.set("org.chokopieum.software.mlgd.MainKt")

            buildArgs.empty() // Очищаем старые аргументы, чтобы не было дублей
            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--enable-url-protocols=http,https",

                // --- СЕРИАЛИЗАЦИЯ (Обязательно build-time) ---
                "--initialize-at-build-time=kotlinx.serialization",
                "--initialize-at-build-time=kotlinx.io",
                "--initialize-at-build-time=kotlin",

                // Логгеры (build-time для API, run-time для реализации)
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-run-time=ch.qos.logback",

                // Ktor и Netty
                "--initialize-at-run-time=io.netty",
                // RoutingKt вызывает проблемы, если в build-time, оставляем в run-time
                "--initialize-at-run-time=io.ktor.server.routing.RoutingKt"
            )
        }
    }
}