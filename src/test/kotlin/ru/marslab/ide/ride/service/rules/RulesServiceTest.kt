package ru.marslab.ide.ride.service.rules

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Unit-тесты для RulesService
 */
class RulesServiceTest : BasePlatformTestCase() {

    private lateinit var rulesService: RulesService
    private lateinit var testProject: Project

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    override fun setUp() {
        super.setUp()
        rulesService = service<RulesService>()
        testProject = project

        // Очищаем кеш перед каждым тестом
        rulesService.clearCache()
    }

    @AfterEach
    override fun tearDown() {
        try {
            rulesService.clearCache()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with no rules and disabled`() {
        // Given
        val basePrompt = "Base system prompt"

        // Отключаем правила в настройках
        disableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertEquals(basePrompt, result)
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with no rules and enabled`() {
        // Given
        val basePrompt = "Base system prompt"

        // Включаем правила в настройках
        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertEquals(basePrompt, result)
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with global rules only`() {
        // Given
        val basePrompt = "Base system prompt"
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "global-rule.md", "# Global Rule\n\nThis is a global rule.")

        // Включаем правила в настройках
        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertTrue(result.contains(basePrompt))
        assertTrue(result.contains("<!-- rules: глобальные -->"))
        assertTrue(result.contains("# Global Rule"))
        assertTrue(result.contains("This is a global rule."))
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with project rules only`() {
        // Given
        val basePrompt = "Base system prompt"
        val projectRulesDir = createProjectRulesDirectory()
        createRuleFile(projectRulesDir, "project-rule.md", "# Project Rule\n\nThis is a project rule.")

        // Включаем правила в настройках
        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertTrue(result.contains(basePrompt))
        assertTrue(result.contains("<!-- rules: проектные -->"))
        assertTrue(result.contains("# Project Rule"))
        assertTrue(result.contains("This is a project rule."))
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with both global and project rules`() {
        // Given
        val basePrompt = "Base system prompt"
        val globalRulesDir = createGlobalRulesDirectory()
        val projectRulesDir = createProjectRulesDirectory()

        createRuleFile(globalRulesDir, "global-rule.md", "# Global Rule\n\nGlobal content.")
        createRuleFile(projectRulesDir, "project-rule.md", "# Project Rule\n\nProject content.")

        // Включаем правила в настройках
        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertTrue(result.contains(basePrompt))
        // Проектные правила должны быть первыми (выший приоритет)
        assertTrue(result.indexOf("<!-- rules: проектные -->") < result.indexOf("<!-- rules: глобальные -->"))
        assertTrue(result.contains("# Project Rule"))
        assertTrue(result.contains("# Global Rule"))
    }

    @Test
    fun `test composeSystemPromptWithActiveRules with multiple rule files`() {
        // Given
        val basePrompt = "Base system prompt"
        val globalRulesDir = createGlobalRulesDirectory()

        createRuleFile(globalRulesDir, "rule-a.md", "# Rule A\n\nContent A.")
        createRuleFile(globalRulesDir, "rule-b.md", "# Rule B\n\nContent B.")
        createRuleFile(globalRulesDir, "not-a-rule.txt", "Not a markdown file")

        // Включаем правила в настройках
        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertTrue(result.contains(basePrompt))
        assertTrue(result.contains("# Rule A"))
        assertTrue(result.contains("# Rule B"))
        assertFalse(result.contains("Not a markdown file"))
        // Файлы должны быть в алфавитном порядке
        assertTrue(result.indexOf("# Rule A") < result.indexOf("# Rule B"))
    }

    @Test
    fun `test getRulesPreview with no rules`() {
        // Given
        disableRulesInSettings()

        // When
        val result = rulesService.getRulesPreview(testProject)

        // Then
        assertEquals("Правила отключены в настройках", result)
    }

    @Test
    fun `test getRulesPreview with rules`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "test-rule.md", "# Test Rule\n\nTest content.")

        enableRulesInSettings()

        // When
        val result = rulesService.getRulesPreview(testProject)

        // Then
        assertTrue(result.contains("# Test Rule"))
        assertTrue(result.contains("Test content."))
        assertFalse(result.contains("Правила отключены в настройках"))
    }

    @Test
    fun `test getGlobalRulesDirectory returns correct path`() {
        // When
        val result = rulesService.getGlobalRulesDirectory()

        // Then
        assertTrue(result.absolutePath.contains(".ride"))
        assertTrue(result.absolutePath.contains("rules"))
    }

    @Test
    fun `test getProjectRulesDirectory with valid project`() {
        // When
        val result = rulesService.getProjectRulesDirectory(testProject)

        // Then
        assertNotNull(result)
        assertTrue(result!!.absolutePath.contains(".ride"))
        assertTrue(result.absolutePath.contains("rules"))
    }

    @Test
    fun `test ensureRulesDirectoryExists creates global directory`() {
        // When
        val result = rulesService.ensureRulesDirectoryExists(true)

        // Then
        assertTrue(result)
        val globalDir = rulesService.getGlobalRulesDirectory()
        assertTrue(globalDir.exists())
        assertTrue(globalDir.isDirectory)
    }

    @Test
    fun `test ensureRulesDirectoryExists creates project directory`() {
        // When
        val result = rulesService.ensureRulesDirectoryExists(false, testProject)

        // Then
        assertTrue(result)
        val projectDir = rulesService.getProjectRulesDirectory(testProject)
        assertNotNull(projectDir)
        assertTrue(projectDir!!.exists())
        assertTrue(projectDir.isDirectory)
    }

    @Test
    fun `test createRuleTemplate creates global template`() {
        // Given
        val globalDir = createGlobalRulesDirectory()

        // When
        val result = rulesService.createRuleTemplate(true)

        // Then
        assertNotNull(result)
        assertTrue(result!!.exists())
        assertTrue(result.name.contains("global"))
        assertTrue(result.readText().contains("Пример глобального правила"))
    }

    @Test
    fun `test createRuleTemplate creates project template`() {
        // Given
        val projectDir = createProjectRulesDirectory()

        // When
        val result = rulesService.createRuleTemplate(false, testProject)

        // Then
        assertNotNull(result)
        assertTrue(result!!.exists())
        assertTrue(result.name.contains("project"))
        assertTrue(result.readText().contains("Пример проектного правила"))
    }

    @Test
    fun `test createRuleTemplate does not overwrite existing file`() {
        // Given
        val globalDir = createGlobalRulesDirectory()
        val existingFile = createRuleFile(globalDir, "example-global-rule.md", "Existing content")

        // When
        val result = rulesService.createRuleTemplate(true)

        // Then
        assertNotNull(result)
        assertEquals(existingFile.absolutePath, result!!.absolutePath)
        assertEquals("Existing content", result.readText())
    }

    @Test
    fun `test clearCache clears cached rules`() {
        // Given
        val basePrompt = "Base prompt"
        val globalDir = createGlobalRulesDirectory()
        createRuleFile(globalDir, "test.md", "# Test")

        enableRulesInSettings()

        // Load rules to populate cache
        val firstResult = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)
        assertTrue(firstResult.contains("# Test"))

        // Modify the file
        createRuleFile(globalDir, "test.md", "# Modified Test")

        // Before clear cache - should still return old cached content
        val cachedResult = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)
        assertTrue(cachedResult.contains("# Test"))
        assertFalse(cachedResult.contains("# Modified Test"))

        // When
        rulesService.clearCache()

        // Then - should return new content
        val freshResult = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)
        assertTrue(freshResult.contains("# Modified Test"))
        assertFalse(freshResult.contains("# Test"))
    }

    @Test
    fun `test rules are trimmed and normalized`() {
        // Given
        val basePrompt = "Base prompt"
        val globalDir = createGlobalRulesDirectory()

        // Create file with BOM and various line endings
        val contentWithBom = "\uFEFF# Test Rule\r\n\r\nContent with\r\nmixed line endings.\n\n   "
        val file = File(globalDir, "test.md")
        file.writeText(contentWithBom)

        enableRulesInSettings()

        // When
        val result = rulesService.composeSystemPromptWithActiveRules(basePrompt, testProject)

        // Then
        assertTrue(result.contains("# Test Rule"))
        assertTrue(result.contains("Content with"))
        // BOM should be removed
        assertFalse(result.contains("\uFEFF"))
        // Should be normalized to single line endings
        assertFalse(result.contains("\r\n"))
    }

    // Helper methods

    private fun enableRulesInSettings() {
        // В реальном тесте нужно было бы мокировать PluginSettings
        // Для простоты пока считаем что правила включены
        // TODO: добавить мок для PluginSettings
    }

    private fun disableRulesInSettings() {
        // TODO: добавить мок для PluginSettings
    }

    private fun createGlobalRulesDirectory(): File {
        val dir = File(tempDir.toFile(), ".ride/rules")
        dir.mkdirs()
        return dir
    }

    private fun createProjectRulesDirectory(): File {
        val dir = File(tempDir.toFile(), ".ride/rules")
        dir.mkdirs()
        return dir
    }

    private fun createRuleFile(directory: File, name: String, content: String): File {
        val file = File(directory, name)
        file.writeText(content)
        return file
    }
}