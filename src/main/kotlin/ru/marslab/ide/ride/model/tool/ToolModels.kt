package ru.marslab.ide.ride.model.tool

import kotlinx.datetime.Instant
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.StepStatus
import java.util.*

/**
 * Шаг плана для Tool Agent
 */
data class ToolPlanStep(
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    val agentType: AgentType,
    val input: StepInput,
    val dependencies: Set<String> = emptySet(),
    var status: StepStatus = StepStatus.PENDING,
    var output: StepOutput? = null,
    val createdAt: Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * Входные данные для шага
 */
data class StepInput(
    val data: Map<String, Any> = emptyMap()
) {
    companion object {
        fun empty() = StepInput()

        fun of(vararg pairs: Pair<String, Any>) = StepInput(pairs.toMap())
    }

    fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? T
    }

    fun getString(key: String): String? = get(key)

    fun getInt(key: String): Int? = get(key)

    fun getBoolean(key: String): Boolean? = get(key)

    fun <T> getList(key: String): List<T>? = get(key)

    fun set(key: String, value: Any): StepInput {
        return copy(data = data + (key to value))
    }
}

/**
 * Выходные данные шага
 */
data class StepOutput(
    val data: Map<String, Any> = emptyMap()
) {
    companion object {
        fun empty() = StepOutput()

        fun of(vararg pairs: Pair<String, Any>) = StepOutput(pairs.toMap())
    }

    fun <T> get(key: String): T? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? T
    }
}

/**
 * Результат выполнения шага
 */
data class StepResult(
    val success: Boolean,
    val output: StepOutput,
    val error: String? = null,
    val requiresUserInput: Boolean = false,
    val userPrompt: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(output: StepOutput, metadata: Map<String, Any> = emptyMap()) =
            StepResult(true, output, metadata = metadata)

        fun error(error: String, output: StepOutput = StepOutput.empty()) =
            StepResult(false, output, error)

        fun requiresInput(prompt: String, output: StepOutput = StepOutput.empty()) =
            StepResult(false, output, null, true, prompt)
    }
}

/**
 * Severity уровни для находок
 */
enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

/**
 * Находка в коде (баг, проблема качества и т.д.)
 */
data class Finding(
    val id: String = UUID.randomUUID().toString(),
    val file: String,
    val line: Int,
    val column: Int = 0,
    val severity: Severity,
    val category: String,
    val message: String,
    val description: String = "",
    val suggestion: String? = null,
    val code: String? = null
)
