plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.graalvm.buildtools.native") version "0.11.1"
    application // Добавляем плагин application
}

group = "org.chokopieum.software"
version = "Develop Build"

// Конфигурация для плагина application
application {
    mainClass.set("org.chokopieum.software.mlgd.MainKt")
}

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.13"

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.3.0"))
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))

    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")

    implementation("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(kotlin("test"))
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("mlgd")
            mainClass.set("org.chokopieum.software.mlgd.MainKt")

            buildArgs.empty()
            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces",
                "--enable-url-protocols=http,https",
                "--initialize-at-build-time=kotlinx.serialization",
                "--initialize-at-build-time=kotlinx.io",
                "--initialize-at-build-time=kotlin",
                "--initialize-at-build-time=org.slf4j",
                "--initialize-at-run-time=ch.qos.logback",
                "--initialize-at-run-time=io.netty",
                "--initialize-at-run-time=io.ktor.server.routing.RoutingKt"
            )
        }
    }
}
