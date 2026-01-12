/*
 * Copyright 2025 Chokopieum Software
 *
 * НЕ ЯВЛЯЕТСЯ ОФИЦИАЛЬНЫМ ПРОДУКТОМ MINECRAFT. НЕ ОДОБРЕНО И НЕ СВЯЗАНО С КОМПАНИЕЙ MOJANG ИЛИ MICROSOFT.
 * Распространяется по лицензии MIT.
 * GITHUB: https://github.com/Chokopieum-Software/MateriaKraft-Launcher
 */

package funlauncher.managers

import funlauncher.JavaInfo
import funlauncher.JavaInstallations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.div

class JavaManager(pathManager: PathManager) {
    private val jdksDir = pathManager.getAppDataDirectory().resolve("jdks").toFile()

    fun getRecommendedJavaVersion(minecraftVersion: String): Int {
        val majorVersion = minecraftVersion.split(".")[1].toIntOrNull() ?: 0
        return when {
            majorVersion >= 21 -> 21
            majorVersion >= 18 -> 17
            else -> 8
        }
    }

    suspend fun deleteLauncherJre(javaInfo: JavaInfo) {
        if (!javaInfo.isManagedByLauncher) {
            throw IllegalArgumentException("Нельзя удалить системную версию Java.")
        }
        withContext(Dispatchers.IO) {
            val javaFile = File(javaInfo.path)
            var current: File? = javaFile.parentFile
            while (current != null && current.parentFile != jdksDir) {
                current = current.parentFile
            }
            if (current != null && current.parentFile == jdksDir) {
                if (!current.deleteRecursively()) {
                    throw IllegalStateException("Не удалось полностью удалить директорию: ${current.path}")
                }
            } else {
                throw IllegalStateException("Не удалось определить корневую директорию JRE для удаления: ${javaInfo.path}")
            }
        }
    }

    suspend fun findJavaInstallations(): JavaInstallations = withContext(Dispatchers.IO) {
        val launcherJavas = mutableListOf<JavaInfo>()
        val systemJavas = mutableMapOf<Int, JavaInfo>()

        if (jdksDir.exists()) {
            jdksDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    val javaPath = findJavaExecutable(dir)
                    if (javaPath != null) {
                        getJavaInfo(javaPath.absolutePath, isManaged = true)?.let { launcherJavas.add(it) }
                    }
                }
            }
        }

        val searchPaths = getSystemSearchPaths()
        for (path in searchPaths) {
            val file = File(path)
            if (file.exists()) {
                getJavaInfo(file.absolutePath, isManaged = false)?.let { info ->
                    if (!systemJavas.containsKey(info.version) || isHigherPriority(info.path, systemJavas[info.version]!!.path)) {
                        systemJavas[info.version] = info
                    }
                }
            }
        }

        JavaInstallations(
            system = systemJavas.values.sortedByDescending { it.version },
            launcher = launcherJavas.sortedByDescending { it.version }
        )
    }

    private fun findJavaExecutable(dir: File): File? {
        return dir.walk().find { it.name == "java" || it.name == "java.exe" }
    }

    private fun getSystemSearchPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        val os = System.getProperty("os.name").lowercase()

        if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            paths.add("/usr/bin/java")
        }

        System.getenv("JAVA_HOME")?.let { paths.add(File(it, "bin/java").absolutePath) }
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { p ->
            val javaFile = File(p, "java")
            if (javaFile.exists()) paths.add(javaFile.absolutePath)
            val javaExeFile = File(p, "java.exe")
            if (javaExeFile.exists()) paths.add(javaExeFile.absolutePath)
        }

        val searchDirs = when {
            os.contains("win") -> listOf("C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium")
            os.contains("mac") -> listOf("/Library/Java/JavaVirtualMachines")
            else -> listOf("/usr/lib/jvm", "/usr/java")
        }
        searchDirs.forEach { dirPath ->
            File(dirPath).walk().maxDepth(4).filter { it.name == "java" || it.name == "java.exe" }.forEach {
                paths.add(it.absolutePath)
            }
        }
        return paths
    }

    private fun isHigherPriority(newPath: String, oldPath: String): Boolean {
        if (newPath.startsWith("/usr/bin")) return true
        if (oldPath.startsWith("/usr/bin")) return false
        return false
    }

    suspend fun getJavaInfo(javaPath: String, isManaged: Boolean = false): JavaInfo? = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            withTimeoutOrNull(3000) {
                process = ProcessBuilder(javaPath, "-XshowSettings:properties", "-version").start()
                val output = process!!.errorStream.bufferedReader().readText()
                process!!.waitFor()

                val versionString = output.lines().firstOrNull { it.contains("java.version") }?.substringAfter("=").orEmpty().trim()
                val vendorString = output.lines().firstOrNull { it.contains("java.vendor") }?.substringAfter("=").orEmpty().trim()
                val archString = output.lines().firstOrNull { it.contains("sun.arch.data.model") }?.substringAfter("=").orEmpty().trim()

                if (versionString.isEmpty()) return@withTimeoutOrNull null

                val majorVersion = if (versionString.startsWith("1.")) {
                    versionString.substring(2, 3).toInt()
                } else {
                    versionString.split(".")[0].toInt()
                }

                JavaInfo(
                    path = javaPath,
                    version = majorVersion,
                    vendor = vendorString,
                    is64Bit = archString == "64",
                    isManagedByLauncher = isManaged
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            process?.destroyForcibly()
        }
    }
}
