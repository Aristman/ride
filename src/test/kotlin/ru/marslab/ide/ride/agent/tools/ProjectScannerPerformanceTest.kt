package ru.marslab.ide.ride.agent.tools

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
 * Интеграционные тесты для проверки производительности ProjectScannerToolAgent
 * на проектах разных размеров
 */
class ProjectScannerPerformanceTest {

    private lateinit var agent: ProjectScannerToolAgent

    @BeforeTest
    fun setup() {
        agent = ProjectScannerToolAgent()
    }

    @Test
    fun `should scan small project efficiently`() = runTest {
        val tempDir = createTempDirectory("performance-test-small")
        val numFiles = 50
        val numDirs = 5

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val step = ToolPlanStep(
                description = "Scan small project",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Scan should succeed: ${result.error}")
            }

            // Проверяем производительность для маленького проекта
            assertTrue(scanTime < 5000, "Small project scan should complete in <5 seconds, took ${scanTime}ms")
            println("Small project (${numFiles} files): ${scanTime}ms")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should scan medium project efficiently`() = runTest {
        val tempDir = createTempDirectory("performance-test-medium")
        val numFiles = 200
        val numDirs = 20

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val step = ToolPlanStep(
                description = "Scan medium project",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Scan should succeed: ${result.error}")
            }

            // Проверяем производительность для среднего проекта
            assertTrue(scanTime < 15000, "Medium project scan should complete in <15 seconds, took ${scanTime}ms")
            println("Medium project (${numFiles} files): ${scanTime}ms")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should handle large project within reasonable time`() = runTest {
        val tempDir = createTempDirectory("performance-test-large")
        val numFiles = 500 // Уменьшаем для теста, чтобы не занимать много времени
        val numDirs = 50

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val step = ToolPlanStep(
                description = "Scan large project",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Scan should succeed: ${result.error}")
            }

            // Проверяем производительность для большого проекта
            assertTrue(scanTime < 30000, "Large project scan should complete in <30 seconds, took ${scanTime}ms")
            println("Large project (${numFiles} files): ${scanTime}ms")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should provide accurate file count`() = runTest {
        val tempDir = createTempDirectory("accuracy-test")
        val numFiles = 100
        val numDirs = 10

        try {
            createTestProject(tempDir, numFiles, numDirs)

            val step = ToolPlanStep(
                description = "Test file counting accuracy",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())
            val result = agent.executeStep(step, context)

            assertTrue(result.success, "Scan should succeed: ${result.error}")

            val json = result.output.get<Map<String, Any>>("json")
            assertNotNull(json, "JSON should not be null")

            val stats = json?.get("stats") as? Map<String, Any>
            assertNotNull(stats, "Stats should not be null")

            val totalFiles = stats?.get("total_files") as? Number
            assertNotNull(totalFiles, "Total files should not be null")

            // Ожидаем найти все .kt файлы плюс конфигурационные файлы
            assertTrue(totalFiles!!.toInt() >= numFiles, "Should find at least $numFiles files, found ${totalFiles.toInt()}")

            println("Accuracy test: expected ≥$numFiles files, found ${totalFiles.toInt()} files")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should respect performance criteria from roadmap`() = runTest {
        val tempDir = createTempDirectory("performance-criteria-test")
        val numFiles = 150 // Для проверки критерия >10,000 файлов, делаем тест с меньшим числом

        try {
            createTestProject(tempDir, numFiles, 15)

            val step = ToolPlanStep(
                description = "Test performance criteria",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*.kt"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())

            val scanTime = measureTimeMillis {
                val result = agent.executeStep(step, context)
                assertTrue(result.success, "Scan should succeed: ${result.error}")

                val json = result.output.get<Map<String, Any>>("json")
                assertNotNull(json, "JSON should not be null")

                val metrics = json?.get("performance_metrics") as? Map<String, Any>
                assertNotNull(metrics, "Performance metrics should not be null")

                println("Performance metrics available: ${metrics.keys}")
            }

            // Проверяем, что сканирование соответствует критериям производительности
            // Для 150 файлов ожидаем время значительно меньше 30 секунд
            assertTrue(scanTime < 5000, "150 files should scan in <5 seconds, took ${scanTime}ms")

            // Экстраполируем результат для проверки критерия из roadmap (>10,000 файлов <30 секунд)
            val extrapolatedTimeFor10k = (scanTime * 10000.0 / numFiles).toLong()
            println("Extrapolated time for 10,000 files: ${extrapolatedTimeFor10k}ms")

            // Это не строгий тест, а приблизительная оценка
            assertTrue(
                extrapolatedTimeFor10k < 30000,
                "Extrapolated time for 10k files should be <30 seconds, estimated ${extrapolatedTimeFor10k}ms"
            )

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

        // Создаем структуру директорий
        val dirs = mutableListOf<File>()
        repeat(numDirectories) { i ->
            val dir = File(baseDir.toFile(), "src/main/dir$i")
            dir.mkdirs()
            dirs.add(dir)
        }

        // Создаем файлы
        repeat(numFiles) { i ->
            val dirIndex = i % numDirectories
            val file = File(dirs[dirIndex], "File$i.kt")
            file.writeText("""
                class File$i {
                    private val property = "test$i"

                    fun calculate(): Int {
                        return i * 2
                    }

                    fun getName(): String {
                        return "File${i}"
                    }

                    fun process(input: String): String {
                        return input.uppercase()
                    }
                }
            """.trimIndent())
        }

        // Создаем конфигурационные файлы
        File(baseDir.toFile(), "build.gradle.kts").writeText("""
            plugins {
                kotlin("jvm") version "1.9.0"
            }

            dependencies {
                implementation(kotlin("stdlib"))
            }
        """.trimIndent())

        File(baseDir.toFile(), "settings.gradle.kts").writeText("""
            rootProject.name = "test-project"
        """.trimIndent())
    }
}