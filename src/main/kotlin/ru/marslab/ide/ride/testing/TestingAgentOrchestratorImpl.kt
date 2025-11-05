package ru.marslab.ide.ride.testing

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Реализация оркестратора: анализ → генерация → сохранение → запуск.
 */
class TestingAgentOrchestratorImpl(
    private val structureProvider: ProjectStructureProvider = A2AProjectStructureProvider(),
    private val testRunner: TestRunner = TerminalBasedTestRunner(A2AProjectStructureProvider()),
    private val persister: TestPersister = FileSystemTestPersister(A2AProjectStructureProvider()),
    private val detectors: List<LanguageTestingAgent> = listOf(KotlinTestingAgent(), JavaTestingAgent()),
    private val frameworkDetector: TestFrameworkDetector = JvmTestFrameworkDetector(A2AProjectStructureProvider())
) : TestingAgentOrchestrator {

    override suspend fun generateAndRun(filePath: String): TestRunResult {
        val structure = structureProvider.getProjectStructure()
        val abs = structure.root.resolve(filePath).normalize()
        require(Files.exists(abs)) { "Файл не найден: $filePath" }

        val langAgent = detectors.firstOrNull { it.supports(filePath) }
            ?: return TestRunResult(
                success = false,
                passed = 0,
                failed = 0,
                skipped = 0,
                durationMs = 0,
                reportText = "Не поддерживаемый тип файла для генерации тестов",
                errors = listOf("Unsupported language for $filePath")
            )

        val source = Files.readString(abs)

        // TODO: на этапе анализа учитывать приватные элементы, но генерировать тесты на публичный API
        val tests = langAgent.generate(source)

        val savedPaths = persister.persist(tests)

        // Попробуем запустить только сгенерированный класс
        val scope = tests.firstOrNull()?.className
        val result = testRunner.run(scope)

        // Если фреймворк не найден — предложить инструкции
        if (!result.success) {
            val fw = frameworkDetector.detect()
            if (fw == TestFramework.NONE) {
                val note = frameworkDetector.suggestAddInstructions(TestFramework.JUNIT5)
                val augmented = result.reportText + "\n\n" + "Рекомендации по установке тестового фреймворка:\n" + note
                return result.copy(reportText = augmented)
            }
        }
        return result
    }
}
