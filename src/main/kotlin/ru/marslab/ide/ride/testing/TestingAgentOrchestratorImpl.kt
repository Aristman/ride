package ru.marslab.ide.ride.testing

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.intellij.openapi.diagnostic.Logger

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
    private val logger = Logger.getInstance(TestingAgentOrchestratorImpl::class.java)

    override suspend fun generateAndRun(filePath: String): TestRunResult {
        logger.debug("TestingOrchestrator: start generateAndRun filePath='$filePath'")
        val t0 = System.currentTimeMillis()
        val structure = structureProvider.getProjectStructure().also {
            logger.debug("TestingOrchestrator: structure buildSystem=${it.buildSystem} root='${it.root}' testDirs=${it.testSourceDirs}")
        }
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
        logger.debug("TestingOrchestrator: selected languageAgent='${langAgent::class.simpleName}'")

        val source = Files.readString(abs)
        logger.debug("TestingOrchestrator: source loaded, length=${source.length}")

        // TODO: на этапе анализа учитывать приватные элементы, но генерировать тесты на публичный API
        val tGen0 = System.currentTimeMillis()
        val tests = langAgent.generate(source)
        logger.debug("TestingOrchestrator: generated tests count=${tests.size} in ${System.currentTimeMillis()-tGen0}ms")

        val tSave0 = System.currentTimeMillis()
        val savedPaths = persister.persist(tests)
        logger.debug("TestingOrchestrator: saved tests to ${savedPaths.joinToString()} in ${System.currentTimeMillis()-tSave0}ms")

        // Попробуем запустить только сгенерированный класс
        val scope = tests.firstOrNull()?.className
        val tRun0 = System.currentTimeMillis()
        val result = testRunner.run(scope)
        logger.debug("TestingOrchestrator: test run done in ${System.currentTimeMillis()-tRun0}ms, success=${result.success}")

        // Если фреймворк не найден — предложить инструкции
        if (!result.success) {
            val fw = frameworkDetector.detect()
            if (fw == TestFramework.NONE) {
                val note = frameworkDetector.suggestAddInstructions(TestFramework.JUNIT5)
                val augmented = result.reportText + "\n\n" + "Рекомендации по установке тестового фреймворка:\n" + note
                val total = System.currentTimeMillis()-t0
                logger.debug("TestingOrchestrator: completed with suggestions in ${total}ms")
                return result.copy(reportText = augmented)
            }
        }
        val total = System.currentTimeMillis()-t0
        logger.debug("TestingOrchestrator: completed ok in ${total}ms")
        return result
    }
}
