package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.marslab.ide.ride.model.orchestrator.*
import ru.marslab.ide.ride.model.tool.*

/**
 * Тесты для UserInteractionAgent
 */
class UserInteractionAgentTest {

    private lateinit var agent: UserInteractionAgent

    @Before
    fun setUp() {
        agent = UserInteractionAgent()
    }

    @Test
    fun `test agent type and capabilities`() {
        assertEquals(AgentType.USER_INTERACTION, agent.agentType)
        assertTrue(agent.toolCapabilities.contains("user_input"))
        assertTrue(agent.toolCapabilities.contains("confirmation"))
        assertTrue(agent.toolCapabilities.contains("choice_selection"))
    }

    @Test
    fun `test execute confirmation step`() = runBlocking {
        val step = ToolPlanStep(
            description = "Confirm action",
            agentType = AgentType.USER_INTERACTION,
            input = StepInput.of(
                "prompt_type" to "confirmation",
                "message" to "Продолжить выполнение?"
            )
        )

        val context = ExecutionContext()
        val result = agent.executeStep(step, context)

        assertFalse(result.success)
        assertTrue(result.requiresUserInput)
        assertNotNull(result.userPrompt)
        assertTrue(result.userPrompt!!.contains("Продолжить выполнение?"))
    }

    @Test
    fun `test execute choice step`() = runBlocking {
        val step = ToolPlanStep(
            description = "Select option",
            agentType = AgentType.USER_INTERACTION,
            input = StepInput.of(
                "prompt_type" to "choice",
                "message" to "Выберите действие:",
                "options" to listOf("Вариант 1", "Вариант 2", "Вариант 3")
            )
        )

        val context = ExecutionContext()
        val result = agent.executeStep(step, context)

        assertFalse(result.success)
        assertTrue(result.requiresUserInput)
        assertNotNull(result.userPrompt)
        assertTrue(result.userPrompt!!.contains("Вариант 1"))
    }

    @Test
    fun `test execute input step`() = runBlocking {
        val step = ToolPlanStep(
            description = "Get user input",
            agentType = AgentType.USER_INTERACTION,
            input = StepInput.of(
                "prompt_type" to "input",
                "message" to "Введите имя файла:",
                "default_value" to "default.txt"
            )
        )

        val context = ExecutionContext()
        val result = agent.executeStep(step, context)

        assertFalse(result.success)
        assertTrue(result.requiresUserInput)
        assertNotNull(result.userPrompt)
        assertTrue(result.userPrompt!!.contains("Введите имя файла:"))
        assertTrue(result.userPrompt!!.contains("default.txt"))
    }

    @Test
    fun `test process confirmation response - yes`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Подтвердите действие",
            options = listOf("Да", "Нет")
        )

        val result = agent.processUserResponse(prompt, "Да")

        assertTrue(result.success)
        assertEquals(true, result.output.get<Boolean>("processed_value"))
    }

    @Test
    fun `test process confirmation response - no`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Подтвердите действие",
            options = listOf("Да", "Нет")
        )

        val result = agent.processUserResponse(prompt, "Нет")

        assertTrue(result.success)
        assertEquals(false, result.output.get<Boolean>("processed_value"))
    }

    @Test
    fun `test process choice response - valid`() {
        val prompt = UserPrompt(
            type = InteractionType.CHOICE,
            message = "Выберите вариант",
            options = listOf("Вариант 1", "Вариант 2", "Вариант 3")
        )

        val result = agent.processUserResponse(prompt, "Вариант 2")

        assertTrue(result.success)
        assertEquals("Вариант 2", result.output.get<String>("processed_value"))
    }

    @Test
    fun `test process choice response - invalid`() {
        val prompt = UserPrompt(
            type = InteractionType.CHOICE,
            message = "Выберите вариант",
            options = listOf("Вариант 1", "Вариант 2", "Вариант 3")
        )

        val result = agent.processUserResponse(prompt, "Вариант 4")

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `test process multi choice response`() {
        val prompt = UserPrompt(
            type = InteractionType.MULTI_CHOICE,
            message = "Выберите варианты",
            options = listOf("A", "B", "C", "D")
        )

        val result = agent.processUserResponse(prompt, "A, C")

        assertTrue(result.success)
        val selected = result.output.get<List<String>>("processed_value")
        assertNotNull(selected)
        assertEquals(2, selected!!.size)
        assertTrue(selected.contains("A"))
        assertTrue(selected.contains("C"))
    }

    @Test
    fun `test process input response`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите текст"
        )

        val result = agent.processUserResponse(prompt, "test input")

        assertTrue(result.success)
        assertEquals("test input", result.output.get<String>("processed_value"))
    }

    @Test
    fun `test process input response with default value`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите текст",
            defaultValue = "default"
        )

        val result = agent.processUserResponse(prompt, "")

        assertTrue(result.success)
        assertEquals("default", result.output.get<String>("processed_value"))
    }

    @Test
    fun `test validate input - valid`() {
        val input = StepInput.of(
            "prompt_type" to "confirmation",
            "message" to "Test message"
        )

        val result = agent.validateInput(input)

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `test validate input - missing message`() {
        val input = StepInput.of(
            "prompt_type" to "confirmation"
        )

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `test validate input - invalid type`() {
        val input = StepInput.of(
            "prompt_type" to "invalid_type",
            "message" to "Test message"
        )

        val result = agent.validateInput(input)

        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `test can handle step`() {
        val step = ToolPlanStep(
            description = "Test",
            agentType = AgentType.USER_INTERACTION,
            input = StepInput.empty()
        )

        assertTrue(agent.canHandle(step))
    }

    @Test
    fun `test cannot handle other agent type`() {
        val step = ToolPlanStep(
            description = "Test",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
        )

        assertFalse(agent.canHandle(step))
    }
}
