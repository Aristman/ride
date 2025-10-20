package ru.marslab.ide.ride.codeanalysis

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import ru.marslab.ide.ride.codeanalysis.analyzer.BugDetectionAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.codeanalysis.FindingType
import ru.marslab.ide.ride.model.codeanalysis.Severity
import ru.marslab.ide.ride.model.llm.LLMResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для BugDetectionAnalyzer
 */
class BugDetectionAnalyzerTest {

    @Test
    fun `should detect null pointer exception`() = runBlocking {
        val mockLLMProvider = mockk<LLMProvider>()
        val analyzer = BugDetectionAnalyzer(mockLLMProvider)

        val code = """
            fun process(user: User?) {
                println(user.name) // NPE here
            }
        """.trimIndent()

        coEvery { mockLLMProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse.success(
            """
            LINE: 2
            SEVERITY: CRITICAL
            TITLE: Potential NullPointerException
            DESCRIPTION: user может быть null, но используется без проверки
            SUGGESTION: Добавьте null-check или используйте safe call operator
            ---
            """.trimIndent()
        )

        val findings = analyzer.analyze(code, "test.kt")

        assertEquals(1, findings.size)
        assertEquals(FindingType.BUG, findings[0].type)
        assertEquals(Severity.CRITICAL, findings[0].severity)
        assertEquals(2, findings[0].line)
        assertTrue(findings[0].title.contains("NullPointerException", ignoreCase = true))
    }

    @Test
    fun `should return empty list when no issues found`() = runBlocking {
        val mockLLMProvider = mockk<LLMProvider>()
        val analyzer = BugDetectionAnalyzer(mockLLMProvider)

        val code = """
            fun process(user: User) {
                println(user.name)
            }
        """.trimIndent()

        coEvery { mockLLMProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse.success(
            "NO_ISSUES_FOUND"
        )

        val findings = analyzer.analyze(code, "test.kt")

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `should handle multiple findings`() = runBlocking {
        val mockLLMProvider = mockk<LLMProvider>()
        val analyzer = BugDetectionAnalyzer(mockLLMProvider)

        val code = "some code with multiple issues"

        coEvery { mockLLMProvider.sendRequest(any(), any(), any(), any()) } returns LLMResponse.success(
            """
            LINE: 1
            SEVERITY: HIGH
            TITLE: Issue 1
            DESCRIPTION: Description 1
            SUGGESTION: Fix 1
            ---
            LINE: 2
            SEVERITY: MEDIUM
            TITLE: Issue 2
            DESCRIPTION: Description 2
            SUGGESTION: Fix 2
            ---
            """.trimIndent()
        )

        val findings = analyzer.analyze(code, "test.kt")

        assertEquals(2, findings.size)
        assertEquals(Severity.HIGH, findings[0].severity)
        assertEquals(Severity.MEDIUM, findings[1].severity)
    }
}
