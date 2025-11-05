package ru.marslab.ide.ride.testing

import kotlinx.coroutines.delay

/**
 * Временная заглушка оркестратора: имитирует генерацию и запуск тестов.
 */
class StubTestingAgentOrchestrator : TestingAgentOrchestrator {
    override suspend fun generateAndRun(filePath: String): TestRunResult {
        // Имитация работы
        delay(300)
        val report = buildString {
            appendLine("Test run (stub) for: $filePath")
            appendLine("passed=0 failed=0 skipped=0 durationMs=0")
            appendLine("Generator not implemented yet")
        }
        return TestRunResult(
            success = true,
            passed = 0,
            failed = 0,
            skipped = 0,
            durationMs = 0,
            reportText = report,
            errors = emptyList()
        )
    }
}
