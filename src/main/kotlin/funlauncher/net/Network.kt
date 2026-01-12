/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.net

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Singleton object for managing network-related components like HttpClient and Json.
 * This ensures that a single, reusable instance of HttpClient is used throughout the application,
 * which is crucial for performance and resource management.
 */
object Network {
    /**
     * A configured Json instance for serialization and deserialization, ignoring unknown keys.
     */
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    /**
     * A shared HttpClient instance configured with CIO engine, content negotiation, and a default timeout.
     */
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 // 60 seconds
        }
        defaultRequest {
            header("User-Agent", "MateriaKraft Launcher")
        }
    }
}
