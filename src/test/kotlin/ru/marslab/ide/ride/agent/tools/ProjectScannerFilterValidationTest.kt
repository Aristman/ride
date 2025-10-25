package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.test.runTest
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

/**
 * Тесты для валидации корректности фильтрации в ProjectScannerToolAgent
 */
class ProjectScannerFilterValidationTest {

    private lateinit var agent: ProjectScannerToolAgent

    @BeforeTest
    fun setup() {
        agent = ProjectScannerToolAgent()
    }

    @Test
    fun `should validate current filtering behavior`() = runTest {
        val tempDir = createTempDirectory("filter-validation-test")

        try {
            // Создаем смесь файлов, чтобы проверить текущее поведение фильтрации
            createTestFiles(tempDir, listOf(
                ".vscode/settings.json",
                "build.gradle.kts",
                "src/main.kt",
                "temp.tmp",
                "README.md",
                "src/test/Test.kt"
            ))

            val step = ToolPlanStep(
                description = "Validate current filtering",
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

            val files = json?.get("files") as? List<String>
            assertNotNull(files, "Files list should not be null")

            // Просто проверяем, что фильтрация работает (находит только .kt файлы)
            val ktFiles = files!!.filter { it.endsWith(".kt") }
            val nonKtFiles = files.filter { !it.endsWith(".kt") }

            assertTrue(ktFiles.isNotEmpty(), "Should find Kotlin files")
            assertTrue(nonKtFiles.isEmpty(), "Should not find non-Kotlin files with include pattern: $nonKtFiles")

            println("Current filtering validation: ${files.size} Kotlin files found")
            println("Found files: ${files.joinToString(", ")}")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `should test exclude patterns functionality`() = runTest {
        val tempDir = createTempDirectory("filter-exclude-test")

        try {
            createTestFiles(tempDir, listOf(
                "src/main/File.kt",
                "src/test/Test.kt",
                "build/tmp/temp.tmp",
                "build/generated/Gen.java",
                "README.md"
            ))

            val step = ToolPlanStep(
                description = "Test exclude patterns",
                agentType = AgentType.PROJECT_SCANNER,
                input = StepInput.empty()
                    .set("project_path", tempDir.toString())
                    .set("include_patterns", listOf("**/*"))
                    .set("exclude_patterns", listOf("build/**", "*.tmp", "README.md"))
            )

            val context = ExecutionContext(projectPath = tempDir.toString())
            val result = agent.executeStep(step, context)

            assertTrue(result.success, "Scan should succeed: ${result.error}")

            val json = result.output.get<Map<String, Any>>("json")
            assertNotNull(json, "JSON should not be null")

            val files = json?.get("files") as? List<String>
            assertNotNull(files, "Files list should not be null")

            // Проверяем, что exclude паттерны работают
            val hasBuildFiles = files.any { it.contains("build/") }
            val hasTmpFiles = files.any { it.endsWith(".tmp") }
            val hasReadme = files.any { it.contains("README.md") }

            assertFalse(hasBuildFiles, "Build files should be excluded")
            assertFalse(hasTmpFiles, "Temp files should be excluded")
            assertFalse(hasReadme, "README.md should be excluded")

            // Проверяем, что Kotlin файлы остались
            val hasKotlinFiles = files.any { it.endsWith(".kt") }
            assertTrue(hasKotlinFiles, "Kotlin files should not be excluded")

            println("Exclude patterns test passed: ${files.size} files found after exclusion")

        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    /**
     * Создает тестовые файлы в указанной директории
     */
    private fun createTestFiles(baseDir: Path, filePaths: List<String>) {
        baseDir.toFile().deleteRecursively()
        baseDir.toFile().mkdirs()

        filePaths.forEach { filePath ->
            val file = File(baseDir.toFile(), filePath)
            file.parentFile?.mkdirs()
            file.writeText("Test content for $filePath")
        }
    }
}