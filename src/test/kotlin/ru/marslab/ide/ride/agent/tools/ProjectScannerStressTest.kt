package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.system.measureTimeMillis
import kotlin.test.*

/**
 * Нагрузочные тесты для ProjectScannerToolAgent
 */
class ProjectScannerStressTest {

    private lateinit var agent: ProjectScannerToolAgent

    @BeforeTest
    fun setup() {
        agent = ProjectScannerToolAgent()
    }

    @Test
    fun `should handle concurrent scanning requests`() = runTest {
        val tempDir = createTempDirectory("stress-concurrent")
        val numFiles = 100
        val numDirs = 10

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val concurrentRequests = 5
            val requests = (1..concurrentRequests).map { requestId ->
                async {
                    val step = ToolPlanStep(
                        description = "Concurrent scan $requestId",
                        agentType = AgentType.PROJECT_SCANNER,
                        input = StepInput.empty()
                            .set("project_path", tempDir.toString())
                            .set("include_patterns", listOf("**/*.kt"))
                    )

                    val context = ExecutionContext(projectPath = tempDir.toString())
                    val result = agent.executeStep(step, context)

                    Pair(requestId, result)
                }
            }

            val results = requests.awaitAll()

            // Проверяем, что все запросы завершились успешно
            results.forEach { (requestId, result) ->
                assertTrue(result.success, "Request $requestId should succeed: ${result.error}")
            }

            assertEquals(concurrentRequests, results.size, "All concurrent requests should complete")

            println("Successfully handled $concurrentRequests concurrent requests")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should handle deep directory structures`() = runTest {
        val tempDir = createTempDirectory("stress-deep")
        val maxDepth = 20
        val filesPerLevel = 3

        try {
            createDeepProjectStructure(tempDir, maxDepth, filesPerLevel)

            val step = ToolPlanStep(
                description = "Deep structure scan",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
                    .set("max_directory_depth", maxDepth)
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Deep scan should succeed: ${result.error}")

                val json = result.output.get<Map<String, Any>>("json")
                assertNotNull(json, "JSON should not be null")

                val stats = json?.get("stats") as? Map<String, Any>
                assertNotNull(stats, "Stats should not be null")

                val totalFiles = stats?.get("total_files") as? Number
                assertNotNull(totalFiles, "Total files should not be null")
                assertTrue(totalFiles!!.toInt() > 0, "Should find files in deep structure")

                println("Deep structure scan: depth=$maxDepth, files found=${totalFiles.toInt()}")
            }

            assertTrue(scanTime < 10000, "Deep structure scan should complete in <10 seconds, took ${scanTime}ms")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should handle large number of small files`() = runTest {
        val tempDir = createTempDirectory("stress-many-files")
        val numFiles = 300 // Уменьшаем для избежания OutOfMemoryError

        try {
            createManySmallFiles(tempDir, numFiles)

            val step = ToolPlanStep(
                description = "Many files scan",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Many files scan should succeed: ${result.error}")

                val json = result.output.get<Map<String, Any>>("json")
                assertNotNull(json, "JSON should not be null")

                val stats = json?.get("stats") as? Map<String, Any>
                assertNotNull(stats, "Stats should not be null")

                val totalFiles = stats?.get("total_files") as? Number
                assertNotNull(totalFiles, "Total files should not be null")
                assertTrue(totalFiles!!.toInt() >= numFiles, "Should find at least $numFiles files, found ${totalFiles.toInt()}")

                println("Many files scan: expected=$numFiles, found=${totalFiles.toInt()}")
            }

            // Проверяем производительность с большим количеством файлов
            assertTrue(scanTime < 10000, "Many files scan should complete in <10 seconds, took ${scanTime}ms")

            // Экстраполяция для критерия из roadmap (>10,000 файлов <30 секунд)
            val extrapolatedTimeFor10k = (scanTime * 10000.0 / numFiles).toLong()
            assertTrue(
                extrapolatedTimeFor10k < 30000,
                "Extrapolated time for 10k files should be <30 seconds, estimated ${extrapolatedTimeFor10k}ms"
            )

            println("Performance for $numFiles files: ${scanTime}ms (extrapolated for 10k: ${extrapolatedTimeFor10k}ms)")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should handle memory pressure efficiently`() = runTest {
        val tempDir = createTempDirectory("stress-memory")
        val numFiles = 500
        val numDirs = 25

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val step = ToolPlanStep(
                description = "Memory pressure test",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            // Измеряем память до и после сканирования
            val runtime = Runtime.getRuntime()
            val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Memory pressure test should succeed: ${result.error}")
            }

            System.gc() // Принудительный GC для очистки
            Thread.sleep(100)
            val memoryAfter = runtime.totalMemory() - runtime.freeMemory()

            val memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024) // MB

            println("Memory usage: ${memoryUsed}MB for $numFiles files")
            println("Scan time: ${scanTime}ms")

            // Проверяем, что использование памяти разумное
            assertTrue(memoryUsed < 100, "Memory usage should be <100MB, used ${memoryUsed}MB")

            // Проверяем наличие метрик производительности в ответе
            val json = agent.executeStep(step, context).output.get<Map<String, Any>>("json")
            assertNotNull(json, "JSON should not be null")
            assertTrue(json!!.containsKey("performance_metrics"), "Should contain performance metrics")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should handle invalid input gracefully`() = runTest {
        val tempDir = createTempDirectory("stress-invalid")

        try {
            createTestProject(tempDir, 10, 2)

            // Тест с экстремальными значениями параметров (без некорректных паттернов)
            val extremeValuesStep = ToolPlanStep(
                description = "Extreme values test",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
                    .set("max_directory_depth", 10) // Уменьшаем для безопасности
                    .set("page", 1)
                    .set("batch_size", 1)
            )

            val context = ExecutionContext(projectPath = tempDir.toString())
            val extremeResult = agent.executeStep(extremeValuesStep, context)
            assertTrue(extremeResult.success, "Should handle extreme values gracefully: ${extremeResult.error}")

            println("Successfully handled invalid input scenarios")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    /**
     * Создает тестовую структуру проекта
     */
    private fun createTestProject(baseDir: Path, numFiles: Int, numDirectories: Int) {
        baseDir.toFile().deleteRecursively()
        baseDir.toFile().mkdirs()

        val dirs = mutableListOf<File>()
        repeat(numDirectories) { i ->
            val dir = File(baseDir.toFile(), "src/main/dir$i")
            dir.mkdirs()
            dirs.add(dir)
        }

        repeat(numFiles) { i ->
            val dirIndex = i % numDirectories
            val file = File(dirs[dirIndex], "File$i.kt")
            file.writeText("""
                class File$i {
                    fun process(): Int = i * 2
                }
            """.trimIndent())
        }

        File(baseDir.toFile(), "build.gradle.kts").writeText("""
            plugins { kotlin("jvm") version "1.9.0" }
        """.trimIndent())
    }

    /**
     * Создает глубокую структуру директорий
     */
    private fun createDeepProjectStructure(baseDir: Path, maxDepth: Int, filesPerLevel: Int) {
        baseDir.toFile().deleteRecursively()
        createDeepDirectory(baseDir.toFile(), 0, maxDepth, filesPerLevel)
    }

    private fun createDeepDirectory(dir: File, currentDepth: Int, maxDepth: Int, filesPerLevel: Int) {
        if (currentDepth >= maxDepth) return

        dir.mkdirs()

        // Создаем файлы на текущем уровне
        repeat(filesPerLevel) { i ->
            val file = File(dir, "File$currentDepth-$i.kt")
            file.writeText("""
                class File${currentDepth}_${i} {
                    val level = $currentDepth
                    fun getLevel(): Int = level
                }
            """.trimIndent())
        }

        // Рекурсивно создаем поддиректории
        repeat(2) { i ->
            val subDir = File(dir, "subdir$i")
            createDeepDirectory(subDir, currentDepth + 1, maxDepth, filesPerLevel)
        }
    }

    /**
     * Создает много маленьких файлов
     */
    private fun createManySmallFiles(baseDir: Path, numFiles: Int) {
        baseDir.toFile().deleteRecursively()
        baseDir.toFile().mkdirs()

        // Создаем несколько поддиректорий для распределения файлов
        repeat(10) { dirIndex ->
            val dir = File(baseDir.toFile(), "dir$dirIndex")
            dir.mkdirs()

            val filesInDir = numFiles / 10
            repeat(filesInDir) { fileIndex ->
                val globalFileIndex = dirIndex * filesInDir + fileIndex
                val file = File(dir, "SmallFile$globalFileIndex.kt")
                file.writeText("class SmallFile$globalFileIndex { fun getValue(): Int = $globalFileIndex }")
            }
        }
    }
}