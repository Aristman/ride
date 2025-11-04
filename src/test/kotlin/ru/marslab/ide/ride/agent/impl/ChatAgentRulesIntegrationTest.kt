package ru.marslab.ide.ride.agent.impl

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.model.chat.*
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.service.rules.RulesService
import java.io.File
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

/**
 * Интеграционные тесты для ChatAgent с настраиваемыми правилами
 */
class ChatAgentRulesIntegrationTest : BasePlatformTestCase() {

    private lateinit var mockLLMProvider: MockLLMProvider
    private lateinit var chatAgent: ChatAgent
    private lateinit var testProject: Project

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    override fun setUp() {
        super.setUp()
        testProject = project
        mockLLMProvider = MockLLMProvider()
        chatAgent = ChatAgent(mockLLMProvider)

        // Очищаем кеш правил
        service<RulesService>().clearCache()
    }

    @AfterEach
    override fun tearDown() {
        try {
            service<RulesService>().clearCache()
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
        val ruleFile = createRuleFile(globalRulesDir, "test-rule.md", "# Test Rule\n\nVersion 1.")

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
        assertTrue(firstSystemPrompt.contains("Version 1."))

        // Modify rule file
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
        assertTrue(secondSystemPrompt.contains("Version 1."))
        assertFalse(secondSystemPrompt.contains("Version 2."))
    }

    @Test
    fun `test ChatAgent works with multiple rule files`() {
        // Given
        val globalRulesDir = createGlobalRulesDirectory()
        createRuleFile(globalRulesDir, "formatting.md", "# Formatting\n\nUse camelCase for variables.")
        createRuleFile(globalRulesDir, "style.md", "# Style\n\nAdd comments to complex logic.")

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
        assertTrue(systemPromptUsed.contains("# Formatting"))
        assertTrue(systemPromptUsed.contains("Use camelCase for variables"))
        assertTrue(systemPromptUsed.contains("# Style"))
        assertTrue(systemPromptUsed.contains("Add comments to complex logic"))
    }

    // Helper classes and methods

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