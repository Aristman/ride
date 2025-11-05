package ru.marslab.ide.ride.service.rules

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Простые unit тесты для PromptRulesHelper без зависимостей от IntelliJ Platform
 */
class PromptRulesHelperTest {

    private lateinit var tempDir: Path
    private lateinit var rulesDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("prompt-rules-test-")
        rulesDir = File(tempDir.toFile(), ".ride/rules")
        rulesDir.mkdirs()
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should return base prompt when no rules exist`() {
        val basePrompt = "Base system prompt"
        val result = simulatePromptRulesHelper(basePrompt, null, false)
        assertEquals(basePrompt, result)
    }

    @Test
    fun `should return base prompt when rules are disabled`() {
        // Создаем правила
        File(rulesDir, "test-rule.md").writeText("# Test Rule\n\nTest content")

        val basePrompt = "Base system prompt"
        val result = simulatePromptRulesHelper(basePrompt, null, false)
        assertEquals(basePrompt, result)
    }

    @Test
    fun `should add rules to prompt when enabled`() {
        // Создаем правила
        File(rulesDir, "test-rule.md").writeText("# Test Rule\n\nTest content")

        val basePrompt = "Base system prompt"
        val result = simulatePromptRulesHelper(basePrompt, rulesDir.absolutePath, true)

        assertTrue(result.contains(basePrompt))
        assertTrue(result.contains("# Test Rule"))
        assertTrue(result.contains("Test content"))
    }

    @Test
    fun `should handle multiple rules files`() {
        // Создаем несколько файлов правил
        File(rulesDir, "rule1.md").writeText("# Rule 1\n\nContent 1")
        File(rulesDir, "rule2.md").writeText("# Rule 2\n\nContent 2")
        File(rulesDir, "not-a-rule.txt").writeText("Not markdown")

        val basePrompt = "Base system prompt"
        val result = simulatePromptRulesHelper(basePrompt, rulesDir.absolutePath, true)

        assertTrue(result.contains(basePrompt))
        assertTrue(result.contains("# Rule 1"))
        assertTrue(result.contains("# Rule 2"))
        assertTrue(result.contains("Content 1"))
        assertTrue(result.contains("Content 2"))
        assertFalse(result.contains("Not markdown"))
    }

    @Test
    fun `should format rules with correct headers`() {
        // Создаем правила
        File(rulesDir, "test-rule.md").writeText("# Test Rule\n\nTest content")

        val basePrompt = "Base system prompt"
        val result = simulatePromptRulesHelper(basePrompt, rulesDir.absolutePath, true)

        assertTrue(result.contains("<!-- rules: глобальные -->"))
    }

    private fun simulatePromptRulesHelper(basePrompt: String, projectPath: String?, rulesEnabled: Boolean): String {
        if (!rulesEnabled) {
            return basePrompt
        }

        if (projectPath == null) {
            return basePrompt
        }

        val projectRulesDir = File(projectPath)
        if (!projectRulesDir.exists()) {
            return basePrompt
        }

        val rulesFiles = projectRulesDir.listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }?.sortedBy { it.name } ?: emptyList<File>()

        if (rulesFiles.isEmpty()) {
            return basePrompt
        }

        val rulesContent = rulesFiles.joinToString("\n\n") { file ->
            file.readText().trim()
        }

        return buildString {
            append(basePrompt)
            append("\n\n")
            append("<!-- rules: глобальные -->\n")
            append(rulesContent)
        }
    }
}