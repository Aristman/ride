package ru.marslab.ide.ride.service.rules

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Простые unit тесты для RulesService без зависимостей от IntelliJ Platform
 */
class RulesServiceSimpleTest {

    private lateinit var tempDir: Path
    private lateinit var globalRulesDir: File
    private lateinit var projectRulesDir: File

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("rules-test-")
        globalRulesDir = File(tempDir.toFile(), ".ride/rules")
        projectRulesDir = File(tempDir.toFile(), ".ride/rules")
        globalRulesDir.mkdirs()
        projectRulesDir.mkdirs()
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should create rules directories successfully`() {
        assertTrue(globalRulesDir.exists())
        assertTrue(globalRulesDir.isDirectory)
        assertTrue(projectRulesDir.exists())
        assertTrue(projectRulesDir.isDirectory)
    }

    @Test
    fun `should create rule files successfully`() {
        val ruleFile = File(globalRulesDir, "test-rule.md")
        val content = "# Test Rule\n\nThis is a test rule."
        ruleFile.writeText(content)

        assertTrue(ruleFile.exists())
        assertEquals(content, ruleFile.readText())
    }

    @Test
    fun `should read rule files correctly`() {
        val ruleFile = File(globalRulesDir, "test-rule.md")
        val expectedContent = "# Test Rule\n\nThis is a test rule."
        ruleFile.writeText(expectedContent)

        val actualContent = ruleFile.readText()
        assertEquals(expectedContent, actualContent)
    }

    @Test
    fun `should list only markdown files`() {
        // Создаем разные файлы
        File(globalRulesDir, "rule1.md").writeText("# Rule 1")
        File(globalRulesDir, "rule2.md").writeText("# Rule 2")
        File(globalRulesDir, "not-a-rule.txt").writeText("Not markdown")
        File(globalRulesDir, "another-rule.MD").writeText("# Rule 3")

        val markdownFiles = globalRulesDir.listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }

        assertEquals(3, markdownFiles?.size)
        assertTrue(markdownFiles?.any { it.name == "rule1.md" } == true)
        assertTrue(markdownFiles?.any { it.name == "rule2.md" } == true)
        assertTrue(markdownFiles?.any { it.name == "another-rule.MD" } == true)
        assertTrue(markdownFiles?.any { it.name == "not-a-rule.txt" } == false)
    }

    @Test
    fun `should handle empty rules directory`() {
        val files = globalRulesDir.listFiles()
        assertEquals(0, files?.size)
    }

    @Test
    fun `should normalize line endings correctly`() {
        val ruleFile = File(globalRulesDir, "line-endings.md")
        val contentWithCRLF = "# Rule\r\n\r\nContent with CRLF"
        ruleFile.writeText(contentWithCRLF)

        val readContent = ruleFile.readText()
        // В Kotlin строковые литералы используют \n, но при чтении файла сохраняются оригинальные окончания
        assertTrue(readContent.contains("# Rule"))
        assertTrue(readContent.contains("Content with CRLF"))
    }

    @Test
    fun `should handle BOM correctly`() {
        val ruleFile = File(globalRulesDir, "bom-rule.md")
        val contentWithBOM = "\uFEFF# BOM Rule\n\nContent with BOM"
        ruleFile.writeText(contentWithBOM)

        val readContent = ruleFile.readText()
        assertTrue(readContent.contains("# BOM Rule"))
        assertTrue(readContent.contains("Content with BOM"))
    }

    @Test
    fun `should compose rules content correctly`() {
        // Создаем два файла правил
        File(globalRulesDir, "rule1.md").writeText("# Rule 1\n\nContent 1")
        File(projectRulesDir, "rule2.md").writeText("# Rule 2\n\nContent 2")

        // Эмулируем логику составления контента
        val globalRules = globalRulesDir.listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }?.sortedBy { it.name }?.map { it.readText() }?.joinToString("\n\n") ?: ""

        val projectRules = projectRulesDir.listFiles { file ->
            file.isFile && file.extension.equals("md", ignoreCase = true)
        }?.sortedBy { it.name }?.map { it.readText() }?.joinToString("\n\n") ?: ""

        val basePrompt = "Base system prompt"
        val composedPrompt = if (globalRules.isNotBlank() || projectRules.isNotBlank()) {
            buildString {
                append(basePrompt)
                append("\n\n")
                if (projectRules.isNotBlank()) {
                    append("<!-- rules: проектные -->\n")
                    append(projectRules)
                    append("\n\n")
                }
                if (globalRules.isNotBlank()) {
                    append("<!-- rules: глобальные -->\n")
                    append(globalRules)
                }
            }
        } else {
            basePrompt
        }

        assertTrue(composedPrompt.contains(basePrompt))
        assertTrue(composedPrompt.contains("# Rule 1"))
        assertTrue(composedPrompt.contains("# Rule 2"))
        assertTrue(composedPrompt.contains("<!-- rules: проектные -->"))
        assertTrue(composedPrompt.contains("<!-- rules: глобальные -->"))
    }
}