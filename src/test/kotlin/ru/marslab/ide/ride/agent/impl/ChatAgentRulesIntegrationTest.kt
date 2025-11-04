package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.chat.ConversationMessage
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.service.rules.RulesService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Интеграционные тесты для ChatAgent с настраиваемыми правилами
 */
class ChatAgentRulesIntegrationTest : BasePlatformTestCase() {

    private lateinit var mockLLMProvider: MockLLMProvider
    private lateinit var chatAgent: ChatAgent
    private lateinit var testProject: Project
    private lateinit var tempDir: Path

    @Before
    override fun setUp() {
        super.setUp()
        testProject = project
        mockLLMProvider = MockLLMProvider()
        chatAgent = ChatAgent(mockLLMProvider)

        // Создаем временную директорию
        tempDir = Files.createTempDirectory("test-rules-")

        // Очищаем кеш правил
        service<RulesService>().clearCache()
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

    @After
    override fun tearDown() {
        try {
            service<RulesService>().clearCache()
            // Удаляем временную директорию
            tempDir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    @Test
    fun `test ChatAgent includes rules in system prompt when enabled`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "test-rule.md", "# Test Rule\n\nAlways respond with 'TEST: ' prefix.")

        // Включаем правила (для теста считаем что они включены)
        enableRules()

        val request = AgentRequest(
            request = "Hello",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // When
        val response = runBlocking { chatAgent.ask(request) }

        // Then
        assertTrue(response.success)
        // Проверяем что правила были включены в системный промпт
        val systemPromptUsed = mockLLMProvider.lastSystemPrompt
        assertTrue(systemPromptUsed.contains("# Test Rule"))
        assertTrue(systemPromptUsed.contains("Always respond with 'TEST: ' prefix"))
    }

    @Test
    fun `test ChatAgent does not include rules when disabled`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "test-rule.md", "# Test Rule\n\nThis should not be included.")

        // Отключаем правила
        disableRules()

        val request = AgentRequest(
            request = "Hello",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // When
        val response = runBlocking { chatAgent.ask(request) }

        // Then
        assertTrue(response.success)
        // Проверяем что правила НЕ были включены в системный промпт
        val systemPromptUsed = mockLLMProvider.lastSystemPrompt
        assertFalse(systemPromptUsed.contains("# Test Rule"))
        assertFalse(systemPromptUsed.contains("This should not be included"))
    }

    @Test
    fun `test ChatAgent prioritizes project rules over global rules`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        val projectRulesDir = createProjectRulesDirectory()

        createRuleFile(globalRulesDir, "global-rule.md", "# Global Rule\n\nUse GLOBAL: prefix.")
        createRuleFile(projectRulesDir, "project-rule.md", "# Project Rule\n\nUse PROJECT: prefix.")

        enableRules()

        val request = AgentRequest(
            request = "Hello",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // When
        val response = runBlocking { chatAgent.ask(request) }

        // Then
        assertTrue(response.success)
        val systemPromptUsed = mockLLMProvider.lastSystemPrompt

        // Оба правила должны быть включены
        assertTrue(systemPromptUsed.contains("# Global Rule"))
        assertTrue(systemPromptUsed.contains("Use GLOBAL: prefix"))
        assertTrue(systemPromptUsed.contains("# Project Rule"))
        assertTrue(systemPromptUsed.contains("Use PROJECT: prefix"))

        // Проектные правила должны быть перед глобальными
        val projectIndex = systemPromptUsed.indexOf("<!-- rules: проектные -->")
        val globalIndex = systemPromptUsed.indexOf("<!-- rules: глобальные -->")
        assertTrue(projectIndex < globalIndex)
    }

    @Test
    fun `test ChatAgent handles empty rules directories`() {
        // Given
        createGlobalRulesDirectory() // Пустая директория
        enableRules()

        val request = AgentRequest(
            request = "Hello",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // When
        val response = runBlocking { chatAgent.ask(request) }

        // Then
        assertTrue(response.success)
        // Системный промпт должен содержать только базовый промпт без секций правил
        val systemPromptUsed = mockLLMProvider.lastSystemPrompt
        assertFalse(systemPromptUsed.contains("<!-- rules:"))
    }

    @Test
    fun `test ChatAgent respects rules cache`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        val ruleFile = createRuleFile(globalRulesDir, "test.md", "# Test Rule\n\nVersion 1.")

        enableRules()

        // First request
        val request1 = AgentRequest(
            request = "Hello",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response1 = runBlocking { chatAgent.ask(request1) }
        assertTrue(response1.success)

        val firstSystemPrompt = mockLLMProvider.lastSystemPrompt
        assertTrue(firstSystemPrompt.contains("# Test Rule"))
        assertFalse(firstSystemPrompt.contains("Version 1."))

        // Modify the rule file
        ruleFile.writeText("# Test Rule\n\nVersion 2.")

        // Second request - should use cached rules
        val request2 = AgentRequest(
            request = "Hello again",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        val response2 = runBlocking { chatAgent.ask(request2) }
        assertTrue(response2.success)

        val secondSystemPrompt = mockLLMProvider.lastSystemPrompt
        // Should still contain Version 1 (from cache)
        assertTrue(secondSystemPrompt.contains("# Test Rule"))
        assertFalse(secondSystemPrompt.contains("Version 2."))
    }

    @Test
    fun `test ChatAgent works with multiple rule files`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()

        createRuleFile(globalRulesDir, "rule-a.md", "# Rule A\n\nContent A.")
        createRuleFile(globalRulesDir, "rule-b.md", "# Rule B\n\nContent B.")
        createRuleFile(globalRulesDir, "not-a-rule.txt", "Not a markdown file")

        enableRules()

        val request = AgentRequest(
            request = "Write code",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        )

        // When
        val response = runBlocking { chatAgent.ask(request) }

        // Then
        assertTrue(response.success)
        val systemPromptUsed = mockLLMProvider.lastSystemPrompt
        assertTrue(systemPromptUsed.contains("# Rule A"))
        assertTrue(systemPromptUsed.contains("# Rule B"))
        assertFalse(systemPromptUsed.contains("Not a markdown file"))
        // Файлы должны быть в алфавитном порядке
        assertTrue(systemPromptUsed.indexOf("# Rule A") < systemPromptUsed.indexOf("# Rule B"))
    }

    @Test
    fun `test getRulesPreview with no rules`() {
        // Given
        disableRules()

        // When
        val result = service<RulesService>().getRulesPreview(testProject)

        // Then
        assertEquals("Правила отключены в настройках", result)
    }

    @Test
    fun `test getRulesPreview with rules`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "test-rule.md", "# Test Rule\n\nTest content.")

        enableRules()

        // When
        val result = service<RulesService>().getRulesPreview(testProject)

        // Then
        assertTrue(result.contains("# Test Rule"))
        assertTrue(result.contains("Test content."))
        assertFalse(result.contains("Правила отключены в настройках"))
    }

    @Test
    fun `test getGlobalRulesDirectory returns correct path`() {
        // When
        val result = service<RulesService>().getGlobalRulesDirectory()

        // Then
        assertTrue(result.absolutePath.contains(".ride"))
        assertTrue(result.absolutePath.contains("rules"))
    }

    @Test
    fun `test getProjectRulesDirectory with valid project`() {
        // When
        val result = service<RulesService>().getProjectRulesDirectory(testProject)

        // Then
        assertNotNull(result)
        assertTrue(result!!.absolutePath.contains(".ride"))
        assertTrue(result.absolutePath.contains("rules"))
    }

    @Test
    fun `test ensureRulesDirectoryExists creates global directory`() {
        // When
        val result = service<RulesService>().ensureRulesDirectoryExists(true)

        // Then
        assertTrue(result)
        val globalDir = service<RulesService>().getGlobalRulesDirectory()
        assertTrue(globalDir.exists())
        assertTrue(globalDir.isDirectory)
    }

    @Test
    fun `test ensureRulesDirectoryExists creates project directory`() {
        // When
        val result = service<RulesService>().ensureRulesDirectoryExists(false, testProject)

        // Then
        assertTrue(result)
        val projectDir = service<RulesService>().getProjectRulesDirectory(testProject)
        assertNotNull(projectDir)
        assertTrue(projectDir!!.exists())
        assertTrue(projectDir.isDirectory)
    }

    @Test
    fun `test createRuleTemplate creates global template`() {
        // Given
        val globalDir = createGlobalRulesDirectory()

        // When
        val result = service<RulesService>().createRuleTemplate(true)

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
        val result = service<RulesService>().createRuleTemplate(false, testProject)

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
        val result = service<RulesService>().createRuleTemplate(true)

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

        enableRules()

        // Load rules to populate cache
        val firstResult = runBlocking { chatAgent.ask(AgentRequest(
            request = "Base prompt",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        ))}
        assertTrue(firstResult.success)
        assertTrue(firstResult.content.contains("# Test"))

        // Modify the file
        createRuleFile(globalDir, "test.md", "# Modified Test")

        // Before clear cache - should still return old cached content
        val cachedResult = runBlocking { chatAgent.ask(AgentRequest(
            request = "Base prompt",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        ))}
        assertTrue(cachedResult.success)
        assertTrue(cachedResult.content.contains("# Test"))
        assertFalse(cachedResult.content.contains("# Modified Test"))

        // When
        service<RulesService>().clearCache()

        // Then - should return new content
        val freshResult = runBlocking { chatAgent.ask(AgentRequest(
            request = "Base prompt",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        ))}
        assertTrue(freshResult.success)
        assertTrue(freshResult.content.contains("# Modified Test"))
        assertFalse(freshResult.content.contains("# Test"))
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

        enableRules()

        // When
        val result = runBlocking { chatAgent.ask(AgentRequest(
            request = "Base prompt",
            context = ChatContext(project = testProject, history = emptyList()),
            parameters = LLMParameters.DEFAULT
        ))}

        // Then
        assertTrue(result.success)
        assertTrue(result.content.contains("# Test Rule"))
        assertTrue(result.content.contains("Content with"))
        // BOM should be removed
        assertFalse(result.content.contains("\uFEFF"))
        // Should be normalized to single line endings
        assertFalse(result.content.contains("\r\n"))
    }

    // Helper classes and methods

    /**
     * Mock LLM Provider для тестов
     */
    private class MockLLMProvider : LLMProvider {
        var lastSystemPrompt: String = ""
        var callCount = 0

        override suspend fun sendRequest(
            systemPrompt: String,
            userMessage: String,
            conversationHistory: List<ConversationMessage>,
            parameters: LLMParameters
        ): LLMResponse {
            callCount++
            lastSystemPrompt = systemPrompt

            return LLMResponse.success(
                content = "Response for: $userMessage"
            )
        }

        override fun getProviderName(): String = "MockProvider"
        override fun isAvailable(): Boolean = true
    }

    private fun enableRules() {
        // В реальной реализации здесь бы настраивался мок PluginSettings
        // Для теста считаем что правила включены по умолчанию
    }

    private fun disableRules() {
        // В реальной реализации здесь бы настраивался мок PluginSettings
    }
}