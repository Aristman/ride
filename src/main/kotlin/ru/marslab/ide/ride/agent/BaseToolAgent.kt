package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.flow.Flow
import ru.marslab.ide.ride.model.agent.*
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep

/**
 * Базовая реализация ToolAgent с общей логикой
 */
abstract class BaseToolAgent(
    override val agentType: AgentType,
    override val toolCapabilities: Set<String>
) : ToolAgent {

    protected val logger = Logger.getInstance(this::class.java)

    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            stateful = false,
            streaming = false,
            reasoning = false,
            tools = toolCapabilities,
            systemPrompt = getSystemPrompt(),
            responseRules = getResponseRules()
        )

    /**
     * Возвращает описание агента
     */
    protected abstract fun getDescription(): String

    /**
     * Возвращает системный промпт агента
     */
    protected open fun getSystemPrompt(): String? = null

    /**
     * Возвращает правила формирования ответов
     */
    protected open fun getResponseRules(): List<String> = emptyList()

    /**
     * Основная логика выполнения шага
     */
    protected abstract suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult

    override suspend fun executeStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        logger.info("Executing step ${step.id} with agent $agentType")

        return try {
            // Валидируем входные данные
            val validation = validateInput(step.input)
            if (!validation.isValid) {
                return StepResult.error("Validation failed: ${validation.errors.joinToString(", ")}")
            }

            // Выполняем шаг
            val result = doExecuteStep(step, context)

            logger.info("Step ${step.id} completed with success=${result.success}")
            result

        } catch (e: Exception) {
            logger.error("Error executing step ${step.id}", e)
            StepResult.error(e.message ?: "Unknown error")
        }
    }

    override fun canHandle(step: ToolPlanStep): Boolean {
        return step.agentType == agentType
    }

    // Базовая реализация Agent интерфейса

    override suspend fun ask(req: AgentRequest): AgentResponse {
        return AgentResponse.error(
            error = "Direct ask() not supported for ToolAgent",
            content = "Use executeStep() instead"
        )
    }

    override fun start(req: AgentRequest): Flow<AgentEvent>? {
        return null // Streaming не поддерживается для Tool Agents
    }

    override fun updateSettings(settings: AgentSettings) {
        // Tool Agents обычно не требуют настроек
        logger.debug("Settings update called for $agentType, but not implemented")
    }

    override fun dispose() {
        logger.info("Disposing $agentType agent")
    }

    /**
     * Логирует промпты, отправляемые в LLM, с безопасным усечением.
     */
    protected fun logUserPrompt(
        action: String,
        systemPrompt: String?,
        userPrompt: String,
        extraMeta: Map<String, Any> = emptyMap()
    ) {
        val maxLen = 2000
        fun truncate(s: String?): String {
            if (s == null) return ""
            return if (s.length > maxLen) s.take(maxLen) + "\n... [truncated ${s.length - maxLen} chars]" else s
        }

        val sys = truncate(systemPrompt)
        val usr = truncate(userPrompt)
        val meta = buildString {
            append("agentType=${agentType.name}, action=$action")
            if (extraMeta.isNotEmpty()) {
                append(", meta=")
                append(extraMeta.entries.joinToString(prefix = "{", postfix = "}") { "${it.key}=${it.value}" })
            }
        }
        logger.info("[PROMPT] $meta\n[SYSTEM]\n$sys\n[USER]\n$usr")
    }
}
