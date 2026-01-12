package funlauncher

import funlauncher.game.VersionManager
import funlauncher.managers.BuildManager
import funlauncher.managers.CacheManager
import funlauncher.managers.PathManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.jupiter.api.Timeout
import java.nio.file.Files

class LauncherIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var pathManager: PathManager
    private lateinit var buildManager: BuildManager
    private lateinit var versionManager: VersionManager
    private lateinit var cacheManager: CacheManager

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        // Инициализируем менеджеры с временной директорией для изоляции теста
        pathManager = PathManager(tempDir)
        buildManager = BuildManager(pathManager)
        versionManager = VersionManager(pathManager)
        cacheManager = CacheManager(pathManager)
        // Создаем необходимые директории внутри временной папки
        pathManager.createRequiredDirectories()

        // --- Создаем фейковые файлы кэша для теста ---
        val cacheDir = pathManager.getCacheDir()
        Files.createDirectories(cacheDir)

        // Фейковый vanilla_versions.json
        val vanillaVersionsJson = """
            {
              "latest": { "release": "1.20.4", "snapshot": "24w14a" },
              "versions": [
                { "id": "1.20.4", "type": "release", "url": "", "time": "", "releaseTime": "" },
                { "id": "1.19.4", "type": "release", "url": "", "time": "", "releaseTime": "" }
              ]
            }
        """.trimIndent()
        Files.write(cacheDir.resolve("vanilla_versions.json"), vanillaVersionsJson.toByteArray())

        // Фейковый forge_versions.json
        val forgeVersionsJson = """
            {
              "promos": {
                "1.20.4-latest": "49.0.25",
                "1.20.4-recommended": "49.0.25"
              }
            }
        """.trimIndent()
        Files.write(cacheDir.resolve("forge_versions.json"), forgeVersionsJson.toByteArray())

        // Фейковый fabric_versions.json (не используется напрямую, но для полноты)
        // VersionManager запрашивает его по сети, но мы можем обойти это, если понадобится
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // Устанавливаем таймаут на случай проблем с сетью
    fun `full build lifecycle test - fetch versions, create and delete all build types`() = runBlocking {
        // --- Фаза 1: Получение и проверка списков версий ---
        println("--- Phase 1: Fetching and Verifying Version Lists ---")

        // cacheManager.updateAllCaches() // Этот метод больше не нужен, так как мы создаем кэш вручную

        val mcVersions = versionManager.getMinecraftVersions().filter { it.type == "release" }.map { it.id }
        val forgeVersions = versionManager.getForgeVersions()
        // Fabric версии запрашиваются по сети, оставим как есть, так как это важная часть теста
        val fabricGameVersions = versionManager.getFabricGameVersions()


        assertAll("Version fetching",
            { assertTrue(mcVersions.isNotEmpty(), "Minecraft versions list should not be empty.") },
            { assertTrue(forgeVersions.isNotEmpty(), "Forge versions list should not be empty.") },
            { assertTrue(fabricGameVersions.isNotEmpty(), "Fabric supported game versions list should not be empty.") }
        )

        val targetMcVersion = "1.20.4" // Выбираем конкретную версию для теста
        println("Target Minecraft version for tests: $targetMcVersion")
        assertTrue(mcVersions.contains(targetMcVersion), "Target version $targetMcVersion not found in fetched list.")

        // --- Фаза 2: Создание и проверка сборок ---
        println("\n--- Phase 2: Creating and Verifying Builds ---")

        // 2.1 Vanilla
        val vanillaBuildName = "Test-Vanilla-1.20.4"
        println("Creating Vanilla build: $vanillaBuildName")
        buildManager.addBuild(vanillaBuildName, targetMcVersion, BuildType.VANILLA, null)
        var builds = buildManager.loadBuilds()
        assertEquals(1, builds.size)
        assertEquals(vanillaBuildName, builds[0].name)
        assertTrue((tempDir / "instances" / vanillaBuildName).exists(), "Vanilla build directory should exist.")
        println("Vanilla build created successfully.")

        // 2.2 Fabric
        val fabricLoaderVersions = versionManager.getFabricLoaderVersions(targetMcVersion)
        val latestFabricLoader = fabricLoaderVersions.firstOrNull { it.stable }?.version
        assertNotNull(latestFabricLoader, "Could not find a stable Fabric loader for $targetMcVersion")
        val fabricBuildName = "Test-Fabric-1.20.4"
        val fabricVersionString = "$targetMcVersion-fabric-$latestFabricLoader"
        println("Creating Fabric build: $fabricBuildName with version $fabricVersionString")
        buildManager.addBuild(fabricBuildName, fabricVersionString, BuildType.FABRIC, null)
        builds = buildManager.loadBuilds()
        assertEquals(2, builds.size)
        assertNotNull(builds.find { it.name == fabricBuildName }, "Fabric build should be in the list.")
        assertTrue((tempDir / "instances" / fabricBuildName).exists(), "Fabric build directory should exist.")
        println("Fabric build created successfully.")

        // 2.3 Forge
        val latestForge = forgeVersions.firstOrNull { it.mcVersion == targetMcVersion && it.isLatest }
        assertNotNull(latestForge, "Could not find a latest Forge version for $targetMcVersion")
        val forgeBuildName = "Test-Forge-1.20.4"
        val forgeVersionString = "$targetMcVersion-forge-${latestForge!!.forgeVersion}"
        println("Creating Forge build: $forgeBuildName with version $forgeVersionString")
        buildManager.addBuild(forgeBuildName, forgeVersionString, BuildType.FORGE, null)
        builds = buildManager.loadBuilds()
        assertEquals(3, builds.size)
        assertNotNull(builds.find { it.name == forgeBuildName }, "Forge build should be in the list.")
        assertTrue((tempDir / "instances" / forgeBuildName).exists(), "Forge build directory should exist.")
        println("Forge build created successfully.")

        // --- Фаза 3: Удаление и проверка ---
        println("\n--- Phase 3: Deleting and Verifying Builds ---")

        println("Deleting all created builds...")
        buildManager.deleteBuild(vanillaBuildName)
        buildManager.deleteBuild(fabricBuildName)
        buildManager.deleteBuild(forgeBuildName)

        builds = buildManager.loadBuilds()
        assertAll("Build deletion",
            { assertTrue(builds.isEmpty(), "Builds list should be empty after deletion.") },
            { assertFalse((tempDir / "instances" / vanillaBuildName).exists(), "Vanilla build directory should be deleted.") },
            { assertFalse((tempDir / "instances" / fabricBuildName).exists(), "Fabric build directory should be deleted.") },
            { assertFalse((tempDir / "instances" / forgeBuildName).exists(), "Forge build directory should be deleted.") }
        )
        println("All builds deleted successfully.")
        println("\n--- Integration Test Completed Successfully! ---")
    }
}
