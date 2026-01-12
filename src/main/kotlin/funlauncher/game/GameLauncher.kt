/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.game

import funlauncher.BuildType
import funlauncher.GameConsole
import funlauncher.MinecraftBuild
import funlauncher.auth.Account
import funlauncher.managers.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.jar.JarFile
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString

/**
 * Handles the final steps of launching the game: extracting natives and building the launch command.
 */
class GameLauncher(
    private val versionInfo: VersionInfo,
    private val build: MinecraftBuild,
    private val pathManager: PathManager
) {

    private val nativesDir: Path = pathManager.getNativesDir(build)
    private val globalLibrariesDir: Path = pathManager.getGlobalLibrariesDir()
    private val globalVersionsDir: Path = pathManager.getGlobalVersionsDir()
    private val globalAssetsDir: Path = pathManager.getGlobalAssetsDir()
    private val gameDir: Path = File(build.installPath).toPath()

    private fun log(message: String) = println("[GameLauncher] $message")

    suspend fun launch(
        account: Account,
        javaPath: String,
        maxRamMb: Int,
        customJavaArgs: String,
        envVars: String,
        showConsole: Boolean
    ): Process {
        extractNatives()
        val command = buildLaunchCommand(account, javaPath, maxRamMb, customJavaArgs)
        val processBuilder = ProcessBuilder(command)
            .directory(gameDir.toFile())
            .redirectErrorStream(true)

        if (envVars.isNotBlank()) {
            val env = processBuilder.environment()
            envVars.lines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) env[parts[0].trim()] = parts[1].trim()
            }
        }

        val process = withContext(Dispatchers.IO) { processBuilder.start() }

        // Start a thread to forward game output to the IDE console and the in-app console
        Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println(line) // Log to IDE console
                    if (showConsole) {
                        GameConsole.addLog(line) // Log to UI console
                    }
                }
            }
        }.start()

        return process
    }

    private fun extractNatives() {
        log("Extracting natives...")
        if (nativesDir.exists()) nativesDir.toFile().deleteRecursively()
        nativesDir.toFile().mkdirs()

        val osName = getOsName()
        val arch = getArch()
        val isLinuxArm = osName == "linux" && arch == "arm64"

        // Process all libraries from version info
        versionInfo.libraries
            .filter { it.downloads?.classifiers != null && isRuleApplicable(it.rules ?: emptyList()) }
            .forEach { lib ->
                // On Linux ARM, skip all LWJGL libraries, as we'll handle them manually
                if (isLinuxArm && lib.name.startsWith("org.lwjgl")) {
                    return@forEach // continue to next library
                }

                val classifiers = lib.downloads!!.classifiers!!
                val nativeKey = lib.natives?.get(osName)?.replace("\${arch}", arch)

                classifiers[nativeKey]?.let { artifact ->
                    val jarPath = globalLibrariesDir.resolve(artifact.path)
                    if (jarPath.exists()) {
                        extractJarContents(jarPath)
                    } else {
                        log("Native JAR not found for extraction: ${jarPath.pathString}")
                    }
                }
            }

        // If on Linux ARM, manually add and extract LWJGL 3.3.3 natives
        if (isLinuxArm) {
            log("Linux ARM detected. Forcing LWJGL 3.3.3 natives.")
            val lwjglArtifacts = listOf(
                "lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb", "lwjgl-tinyfd"
            )
            val lwjglVersion = "3.3.3"
            val nativeClassifier = "natives-linux-arm64"

            lwjglArtifacts.forEach { artifactName ->
                val artifactPath = "org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion-$nativeClassifier.jar"
                val jarPath = globalLibrariesDir.resolve(artifactPath)
                if (jarPath.exists()) {
                    extractJarContents(jarPath)
                } else {
                    log("Required LWJGL native JAR not found, this might cause a crash: ${jarPath.pathString}")
                }
            }
        }
    }

    private fun extractJarContents(jarPath: Path) {
        try {
            JarFile(jarPath.toFile()).use { jar ->
                jar.entries().asSequence()
                    .filterNot { it.isDirectory || it.name.contains("META-INF") }
                    .forEach { entry ->
                        val outFile = nativesDir.resolve(entry.name.substringAfterLast('/'))
                        Files.copy(jar.getInputStream(entry), outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        log("Extracted ${entry.name} from ${jarPath.name} to ${outFile.pathString}")
                    }
            }
        } catch (e: Exception) {
            log("Failed to extract ${jarPath.name}: ${e.message}")
        }
    }

    private fun buildLaunchCommand(account: Account, javaPath: String, maxRamMb: Int, customJavaArgs: String): List<String> {
        val classpath = buildClasspath()
        val replacements = createReplacementsMap(classpath, account)
        val osName = getOsName()
        val replacePlaceholders = { str: String ->
            var result = str
            replacements.forEach { (k, v) -> result = result.replace("\${$k}", v) }
            result
        }

        val finalCommand = mutableListOf<String>()
        finalCommand.add(javaPath.ifBlank { "java" })
        finalCommand.add("-Xmx${maxRamMb}M")
        processArguments(versionInfo.arguments?.jvm).map(replacePlaceholders).let { finalCommand.addAll(it) }
        if (customJavaArgs.isNotBlank()) {
            finalCommand.addAll(customJavaArgs.split(" ").map(replacePlaceholders))
        }
        finalCommand.add(versionInfo.mainClass)

        val gameArgsSource = versionInfo.arguments?.game ?: versionInfo.gameArguments?.split(" ")?.map { JsonElementWrapper.StringValue(it) }
        val processedGameArgs = processArguments(gameArgsSource).map(replacePlaceholders)
        finalCommand.addAll(filterUndesiredArgs(processedGameArgs))

        // Final cleanup: Force remove -XstartOnFirstThread on non-macOS
        if (osName != "osx") {
            finalCommand.removeAll { it == "-XstartOnFirstThread" }
        }

        log("--- FINAL LAUNCH COMMAND ---")
        log(finalCommand.joinToString(" "))
        log("----------------------------")

        return finalCommand
    }

    private fun buildClasspath(): String {
        val cpList = mutableListOf<String>()
        val osName = getOsName()
        val arch = getArch()
        val isLinuxArm = osName == "linux" && arch == "arm64"

        versionInfo.libraries.forEach { lib ->
            if (isRuleApplicable(lib.rules ?: emptyList())) {
                // On Linux ARM, skip all LWJGL libraries from metadata
                if (isLinuxArm && lib.name.startsWith("org.lwjgl")) {
                    return@forEach // continue
                }

                val path = lib.downloads?.artifact?.path ?: getArtifactPath(lib.name)
                cpList.add(globalLibrariesDir.resolve(path).toAbsolutePath().toString())
            }
        }

        // If on Linux ARM, manually add LWJGL 3.3.3 jars to the classpath
        if (isLinuxArm) {
            log("Linux ARM detected. Forcing LWJGL 3.3.3 on classpath.")
            val lwjglArtifacts = listOf(
                "lwjgl", "lwjgl-glfw", "lwjgl-jemalloc", "lwjgl-openal", "lwjgl-opengl", "lwjgl-stb", "lwjgl-tinyfd"
            )
            val lwjglVersion = "3.3.3"
            val nativeClassifier = "natives-linux-arm64"

            lwjglArtifacts.forEach { artifactName ->
                // Add main jar
                val mainJarPath = "org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion.jar"
                cpList.add(globalLibrariesDir.resolve(mainJarPath).toAbsolutePath().toString())
                // Add native jar for classpath as well, some mods need it.
                val nativeJarPath = "org/lwjgl/$artifactName/$lwjglVersion/$artifactName-$lwjglVersion-$nativeClassifier.jar"
                cpList.add(globalLibrariesDir.resolve(nativeJarPath).toAbsolutePath().toString())
            }
        }

        val gameVersionForJar = when (build.type) {
            BuildType.FABRIC -> build.version.split("-fabric-").first()
            BuildType.FORGE -> build.version.split("-forge-").first()
            BuildType.QUILT -> build.version.split("-quilt-").first()
            BuildType.NEOFORGE -> build.version.split("-neoforge-").first()
            else -> versionInfo.id
        }
        val clientJarPath = globalVersionsDir.resolve(gameVersionForJar).resolve("$gameVersionForJar.jar")
        cpList.add(clientJarPath.toAbsolutePath().toString())
        return cpList.joinToString(File.pathSeparator)
    }

    private fun createReplacementsMap(classpath: String, account: Account): Map<String, String> = mapOf(
        "natives_directory" to nativesDir.toAbsolutePath().toString(),
        "launcher_name" to "MateriaKraft",
        "launcher_version" to "1.0",
        "classpath" to classpath,
        "auth_player_name" to account.username,
        "version_name" to versionInfo.id,
        "game_directory" to gameDir.toAbsolutePath().toString(),
        "assets_root" to globalAssetsDir.toAbsolutePath().toString(),
        "assets_index_name" to versionInfo.assetIndex.id,
        "auth_uuid" to (account.uuid ?: UUID.nameUUIDFromBytes(account.username.toByteArray()).toString()),
        "auth_access_token" to (account.accessToken ?: "0"),
        "user_properties" to "{}",
        "user_type" to if (account.isLicensed) "msa" else "legacy",
        "version_type" to build.type.name,
        "clientid" to "clientId",
        "auth_xuid" to "xuid",
        "resolution_width" to "854",
        "resolution_height" to "480"
    )

    private fun processArguments(args: List<JsonElementWrapper>?): List<String> {
        val osName = getOsName()
        return args?.flatMap { wrapper ->
            when (wrapper) {
                is JsonElementWrapper.StringValue -> listOf(wrapper.value)
                is JsonElementWrapper.ObjectValue -> {
                    if (isRuleApplicable(wrapper.rules)) {
                        wrapper.getValues()
                    } else {
                        emptyList()
                    }
                }
            }
        } ?: emptyList()
    }

    private fun filterUndesiredArgs(args: List<String>): List<String> {
        val finalArgs = mutableListOf<String>()
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            when {
                arg.startsWith("--quickPlay") -> i += 2
                arg == "--demo" -> {
                    i++
                    if (i < args.size && (args[i].equals("true", true) || args[i].equals("false", true))) {
                        i++
                    }
                }
                else -> {
                    finalArgs.add(arg)
                    i++
                }
            }
        }
        return finalArgs
    }

    private fun getArtifactPath(name: String, classifier: String? = null): String {
        val parts = name.split(':')
        val groupPath = parts[0].replace('.', '/')
        val artifactName = parts[1]
        val version = parts[2]
        val classifierStr = if (classifier != null) "-$classifier" else ""
        return "$groupPath/$artifactName/$version/$artifactName-$version$classifierStr.jar"
    }

    private fun isRuleApplicable(rules: List<VersionInfo.Rule>): Boolean {
        if (rules.isEmpty()) return true
        var applies = false // Default to not applying if there are rules but none match
        var hasOsSpecificRule = false

        for (rule in rules) {
            if (rule.os != null && rule.os.name != null) {
                hasOsSpecificRule = true
                if (rule.os.name == getOsName()) {
                    return rule.action == "allow"
                }
            }
        }

        // If there are no OS-specific rules, check for general allow/disallow
        if (!hasOsSpecificRule) {
            for (rule in rules) {
                if (rule.os == null) {
                    return rule.action == "allow"
                }
            }
        }

        return !hasOsSpecificRule // If there were OS-specific rules but none matched, don't apply
    }

    private fun getOsName(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "windows"
        System.getProperty("os.name").lowercase().contains("mac") -> "osx"
        else -> "linux"
    }

    private fun getArch(): String = when (val arch = System.getProperty("os.arch").lowercase()) {
        "aarch64" -> "arm64"
        "x86_64", "amd64" -> "x64"
        "x86" -> "x86"
        else -> arch
    }
}
