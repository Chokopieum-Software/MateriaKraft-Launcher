/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher

import com.github.javakeyring.Keyring
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.microsoft.aad.msal4j.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

class MateriaAuthenticator {

    // --- КОНФИГУРАЦИЯ ---
    private val clientId = "706fbc67-be98-4ded-bbe7-cc50097e2e2d"
    // Используем localhost, как самый надежный способ для Desktop Java
    private val redirectUri = URI("http://localhost")
    private val authority = "https://login.microsoftonline.com/consumers/"
    private val scopes = setOf("XboxLive.Signin", "Offline_access")

    private val client = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Настройка хранилища токенов (как в прошлом примере)
    private val tokenCacheAspect = object : ITokenCacheAccessAspect {
        val service = "MateriaLauncher"
        val accountKey = "MateriaAuthCache"

        override fun beforeCacheAccess(context: ITokenCacheAccessContext) {
            try {
                val keyring = Keyring.create()
                val data = keyring.getPassword(service, accountKey)
                if (data != null) context.tokenCache().deserialize(data)
            } catch (_: Exception) {}
        }

        override fun afterCacheAccess(context: ITokenCacheAccessContext) {
            if (context.hasCacheChanged()) {
                try {
                    val keyring = Keyring.create()
                    keyring.setPassword(service, accountKey, context.tokenCache().serialize())
                } catch (e: Exception) {
                    println("Warning: Не удалось сохранить сессию: ${e.message}")
                }
            }
        }
    }

    private val app: PublicClientApplication = PublicClientApplication.builder(clientId)
        .authority(authority)
        .setTokenCacheAccessAspect(tokenCacheAspect)
        .build()

    // --- ГЛАВНЫЙ МЕТОД ВХОДА ---
    fun login(): MinecraftProfile {
        val msAccessToken = try {
            println("[Materia] Пробую тихий вход...")
            acquireTokenSilently()
        } catch (_: Exception) {
            println("[Materia] Тихий вход не удался, открываю браузер...")
            acquireTokenInteractive()
        }

        println("[Materia] Вход в Microsoft выполнен! Получаю профиль Minecraft...")

        // Цепочка обмена токенов (без изменений, так как это стандарт)
        val xbox = authXboxLive(msAccessToken)
        val xsts = authXSTS(xbox.token)
        val mcToken = authMinecraft(xsts.userHash, xsts.token)

        return checkGameOwnership(mcToken)
    }

    // --- ЛОГИКА MSAL ---

    private fun acquireTokenSilently(): String {
        val accounts = app.accounts.join()
        if (accounts.isEmpty()) throw Exception("Нет сохраненных аккаунтов")

        val params = SilentParameters.builder(scopes, accounts.first()).build()
        return app.acquireTokenSilently(params).join().accessToken()
    }

    private fun acquireTokenInteractive(): String {
        // Настройка открытия браузера
        val params = InteractiveRequestParameters.builder(redirectUri)
            .scopes(scopes)
            .systemBrowserOptions(
                SystemBrowserOptions.builder()
                    .htmlMessageSuccess("<html><body><center><h1>Вы успешно вошли в Materia Launcher!</h1><p>Можете закрыть эту вкладку и вернуться в лаунчер.</p></center></body></html>")
                    .htmlMessageError("<html><body><center><h1>Ошибка входа :(</h1></center></body></html>")
                    .build()
            )
            .build()

        // Этот вызов сам откроет браузер, подождет, пока юзер войдет,
        // поймает редирект на localhost и вернет токен.
        return app.acquireToken(params).join().accessToken()
    }

    // --- MINECRAFT API (То же самое, что и раньше) ---

    data class XboxResponse(val token: String, val userHash: String)
    data class McTokenResponse(@SerializedName("access_token") val accessToken: String)
    data class MinecraftProfile(
        val id: String,
        val name: String,
        val accessToken: String,
        val skinUrl: String? // <-- Добавили поле для скина
    )

    private fun authXboxLive(msToken: String): XboxResponse {
        val json = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val resp = postJson("https://user.auth.xboxlive.com/user/authenticate", json)
        val jsonObj = gson.fromJson(resp, JsonObject::class.java)
        return XboxResponse(
            jsonObj.get("Token").asString,
            jsonObj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject.get("uhs").asString
        )
    }

    private fun authXSTS(xboxToken: String): XboxResponse {
        val json = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xboxToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val resp = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", json)
        val jsonObj = gson.fromJson(resp, JsonObject::class.java)
        return XboxResponse(
            jsonObj.get("Token").asString,
            jsonObj.getAsJsonObject("DisplayClaims").getAsJsonArray("xui")[0].asJsonObject.get("uhs").asString
        )
    }

    private fun authMinecraft(uhs: String, xstsToken: String): String {
        val json = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val resp = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", json)
        return gson.fromJson(resp, McTokenResponse::class.java).accessToken
    }

    private fun checkGameOwnership(mcToken: String): MinecraftProfile {
        val request = Request.Builder()
            .url("https://api.minecraftservices.com/minecraft/profile")
            .header("Authorization", "Bearer $mcToken")
            .build()

        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw Exception("Ошибка получения профиля или игра не куплена.")

            val json = gson.fromJson(it.body!!.string(), JsonObject::class.java)
            val id = json.get("id").asString
            val name = json.get("name").asString

            // --- Логика вытаскивания скина ---
            var skinUrl: String? = null
            if (json.has("skins")) {
                val skins = json.getAsJsonArray("skins")
                if (skins.size() > 0) {
                    // Берем первый попавшийся скин (обычно он там один активный)
                    skinUrl = skins.get(0).asJsonObject.get("url").asString
                }
            }
            // ---------------------------------

            return MinecraftProfile(id, name, mcToken, skinUrl)
        }
    }

    private fun postJson(url: String, json: String): String {
        val request = Request.Builder().url(url).post(json.toRequestBody(jsonMediaType)).header("Accept", "application/json").build()
        client.newCall(request).execute().use {
            if (!it.isSuccessful) throw Exception("API Error $url: ${it.code} ${it.body?.string()}")
            return it.body!!.string()
        }
    }
}
