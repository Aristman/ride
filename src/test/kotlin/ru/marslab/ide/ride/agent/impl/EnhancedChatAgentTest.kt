package ru.marslab.ide.ride.agent.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.agent.UncertaintyAnalyzer
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.agent.AgentResponse
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.llm.LLMResponse
import ru.marslab.ide.ride.mcp.MCPServerManager
import ru.marslab.ide.ride.orchestrator.EnhancedAgentOrchestrator

@OptIn(ExperimentalCoroutinesApi::class)
class EnhancedChatAgentTest : BasePlatformTestCase() {

    private lateinit var mockLLMProvider: LLMProvider
    private lateinit var mockOrchestrator: EnhancedAgentOrchestrator
    private lateinit var mockApplication: Application
    private lateinit var mockMCPServerManager: MCPServerManager
    private lateinit var baseChatAgent: ChatAgent
    private lateinit var enhancedChatAgent: EnhancedChatAgent
    private val testDispatcher = StandardTestDispatcher()

    @Before
    override fun setUp() {
        super.setUp()
        Dispatchers.setMain(testDispatcher)
        // Мокируем ApplicationManager и MCPServerManager
        mockApplication = mockk(relaxed = true)
        mockMCPServerManager = mockk(relaxed = true) {
            every { isServerRunning() } returns true
            every { getServerUrl() } returns "http://localhost:3001"
        }

        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns mockApplication
        every { mockApplication.getService(MCPServerManager::class.java) } returns mockMCPServerManager

        mockLLMProvider = mockk(relaxed = true)
        mockOrchestrator = mockk(relaxed = true)

        // Настраиваем базовый LLM провайдер
        every { mockLLMProvider.isAvailable() } returns true
        every { mockLLMProvider.getProviderName() } returns "TestProvider"

        baseChatAgent = ChatAgent(mockLLMProvider)
        enhancedChatAgent = EnhancedChatAgent(
            baseChatAgent = baseChatAgent,
            orchestrator = mockOrchestrator,
            uncertaintyAnalyzer = UncertaintyAnalyzer
        )
    }

    @After
    override fun tearDown() {
        clearAllMocks()
        unmockkStatic(ApplicationManager::class)
        Dispatchers.resetMain()
        super.tearDown()
    }

    @Test
    fun `test simple question uses base ChatAgent`() = runBlocking(testDispatcher) {
        // Given: простой вопрос
        val simpleQuestion = "Что такое Kotlin?"
        val context = ChatContext(
            project = project,
            additionalContext = emptyMap()
        )
        val request = AgentRequest(
            request = simpleQuestion,
            context = context,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем ответ от LLM
        coEvery {
            mockLLMProvider.sendRequest(any(), any(), any(), any())
        } returns LLMResponse(
            content = "Kotlin - это современный язык программирования",
            success = true
        )

        // When: отправляем запрос
        val response = enhancedChatAgent.ask(request)

        // Then: используется базовый ChatAgent (не оркестратор)
        assertTrue(response.success)
        assertFalse(response.content.contains("Выполненные шаги"))
        coVerify(exactly = 0) { mockOrchestrator.processEnhanced(any(), any()) }
    }

    @Test
    fun `test complex task uses orchestrator`() = runBlocking(testDispatcher) {
        // Given: сложная задача
        val complexTask = "Проанализируй весь проект и найди все баги в коде"
        val context = ChatContext(
            project = project,
            additionalContext = emptyMap()
        )
        val request = AgentRequest(
            request = complexTask,
            context = context,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем ответ от оркестратора
        coEvery {
            mockOrchestrator.processEnhanced(any(), any())
        } returns AgentResponse.success(
            content = "Анализ завершён. Найдено 5 багов.",
            metadata = mapOf("bugs_found" to 5)
        )

        // When: отправляем запрос
        val response = enhancedChatAgent.ask(request)

        // Then: используется оркестратор
        assertTrue(response.success)
        assertTrue(response.content.contains("Результат выполнения задачи"))
        coVerify(exactly = 1) { mockOrchestrator.processEnhanced(any(), any()) }
    }

    @Test
    fun `test resume plan with user input`() = runBlocking(testDispatcher) {
        // Given: запрос на возобновление плана
        val planId = "test-plan-123"
        val userInput = "Да, продолжай"
        val contextWithPlanId = ChatContext(
            project = project,
            additionalContext = mapOf("resume_plan_id" to planId)
        )
        val request = AgentRequest(
            request = userInput,
            context = contextWithPlanId,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем возобновление плана
        coEvery {
            mockOrchestrator.resumePlanWithCallback(planId, userInput, any())
        } returns AgentResponse.success(
            content = "План продолжает выполнение",
            metadata = mapOf("plan_id" to planId)
        )

        // When: отправляем запрос на возобновление
        val response = enhancedChatAgent.ask(request)

        // Then: план возобновлён
        assertTrue(response.success)
        assertTrue(response.content.contains("План возобновлён"))
        assertEquals(planId, response.metadata["plan_id"])
        coVerify(exactly = 1) { mockOrchestrator.resumePlanWithCallback(planId, userInput, any()) }
    }

    @Test
    fun `test task complexity analysis for bug detection`() = runBlocking(testDispatcher) {
        // Given: запрос на поиск багов
        val bugRequest = "Найди ошибки в файле Main.kt"
        val context = ChatContext(
            project = project,
            additionalContext = emptyMap()
        )
        val request = AgentRequest(
            request = bugRequest,
            context = context,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем оркестратор
        coEvery {
            mockOrchestrator.processEnhanced(any(), any())
        } returns AgentResponse.success(
            content = "Анализ завершён",
            metadata = emptyMap()
        )

        // When: отправляем запрос
        val response = enhancedChatAgent.ask(request)

        // Then: используется оркестратор (сложная задача)
        assertTrue(response.success)
        coVerify(exactly = 1) { mockOrchestrator.processEnhanced(any(), any()) }
    }

    @Test
    fun `test task complexity analysis for code quality`() = runBlocking(testDispatcher) {
        // Given: запрос на проверку качества кода
        val qualityRequest = "Проверь качество кода в проекте"
        val context = ChatContext(
            project = project,
            additionalContext = emptyMap()
        )
        val request = AgentRequest(
            request = qualityRequest,
            context = context,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем оркестратор
        coEvery {
            mockOrchestrator.processEnhanced(any(), any())
        } returns AgentResponse.success(
            content = "Проверка качества завершена",
            metadata = emptyMap()
        )

        // When: отправляем запрос
        val response = enhancedChatAgent.ask(request)

        // Then: используется оркестратор
        assertTrue(response.success)
        coVerify(exactly = 1) { mockOrchestrator.processEnhanced(any(), any()) }
    }

    @Test
    fun `test capabilities include orchestration`() {
        // When: проверяем capabilities
        val capabilities = enhancedChatAgent.capabilities

        // Then: включает оркестрацию
        assertTrue(capabilities.tools.contains("orchestration"))
        assertTrue(capabilities.tools.contains("user_interaction"))
        assertTrue(capabilities.tools.contains("plan_management"))
        assertTrue(capabilities.stateful)
        assertTrue(capabilities.reasoning)
    }

    @Test
    fun `test factory method creates agent correctly`() {
        // When: создаём агента через фабрику
        val agent = EnhancedChatAgent.create(mockLLMProvider)

        // Then: агент создан корректно
        assertNotNull(agent)
        assertEquals(3, agent.capabilities.tools.size)
    }

    @Test
    fun `test error handling when plan not found`() = runBlocking(testDispatcher) {
        // Given: несуществующий план
        val planId = "non-existent-plan"
        val userInput = "Продолжай"
        val contextWithPlanId = ChatContext(
            project = project,
            additionalContext = mapOf("resume_plan_id" to planId)
        )
        val request = AgentRequest(
            request = userInput,
            context = contextWithPlanId,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем ошибку
        coEvery {
            mockOrchestrator.resumePlanWithCallback(planId, userInput, any())
        } returns AgentResponse.error(
            error = "План не найден",
            content = "План $planId не существует"
        )

        // When: пытаемся возобновить несуществующий план
        val response = enhancedChatAgent.ask(request)

        // Then: получаем ошибку
        assertFalse(response.success)
        assertTrue(response.content.contains("не существует"))
    }

    @Test
    fun `test long request with file mention triggers orchestrator`() = runBlocking(testDispatcher) {
        // Given: длинный запрос с упоминанием файлов
        val longRequest = "Пожалуйста, проанализируй все файлы в проекте, " +
                "найди потенциальные проблемы с производительностью, " +
                "проверь соответствие code style и создай детальный отчёт"
        val context = ChatContext(
            project = project,
            additionalContext = emptyMap()
        )
        val request = AgentRequest(
            request = longRequest,
            context = context,
            parameters = LLMParameters.BALANCED
        )

        // Мокируем оркестратор
        coEvery {
            mockOrchestrator.processEnhanced(any(), any())
        } returns AgentResponse.success(
            content = "Анализ завершён",
            metadata = emptyMap()
        )

        // When: отправляем запрос
        val response = enhancedChatAgent.ask(request)

        // Then: используется оркестратор
        assertTrue(response.success)
        coVerify(exactly = 1) { mockOrchestrator.processEnhanced(any(), any()) }
    }
}
