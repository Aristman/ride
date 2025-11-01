package ru.marslab.ide.ride.agent.planner

import kotlinx.datetime.Instant
import ru.marslab.ide.ride.model.tool.StepOutput

/**
 * Результат выполнения шага плана
 */
data class PlanStepResult(
    /** ID шага */
    val stepId: String,

    /** Успешность выполнения */
    val success: Boolean,

    /** Выходные данные шага */
    val output: StepOutput,

    /** Ошибка если была */
    val error: String? = null,

    /** Время выполнения в миллисекундах */
    val executionTimeMs: Long,

    /** Метаданные результата */
    val metadata: Map<String, Any> = emptyMap(),

    /** Время завершения */
    val completedAt: Instant = kotlinx.datetime.Clock.System.now()
) {
    /**
     * Возвращает основные данные из вывода
     */
    inline fun <reified T> getOutputData(key: String): T? {
        return output.get<T>(key)
    }

    /**
     * Проверяет есть ли в выводе определенный ключ
     */
    fun hasOutputData(key: String): Boolean {
        return output.data.containsKey(key)
    }

    /**
     * Возвращает все ключи из вывода
     */
    fun getOutputKeys(): Set<String> {
        return output.data.keys
    }

    companion object {
        /**
         * Создает успешный результат
         */
        fun success(
            stepId: String,
            output: StepOutput,
            executionTimeMs: Long = 0,
            metadata: Map<String, Any> = emptyMap()
        ): PlanStepResult {
            return PlanStepResult(
                stepId = stepId,
                success = true,
                output = output,
                executionTimeMs = executionTimeMs,
                metadata = metadata
            )
        }

        /**
         * Создает результат с ошибкой
         */
        fun error(
            stepId: String,
            error: String,
            output: StepOutput = StepOutput.empty(),
            executionTimeMs: Long = 0,
            metadata: Map<String, Any> = emptyMap()
        ): PlanStepResult {
            return PlanStepResult(
                stepId = stepId,
                success = false,
                output = output,
                error = error,
                executionTimeMs = executionTimeMs,
                metadata = metadata
            )
        }
    }
}

/**
 * Расширенные метаданные для шагов плана
 */
data class PlanStepMetadata(
    /** Предполагаемое время выполнения */
    val estimatedDurationMs: Long,

    /** Фактическое время выполнения */
    val actualDurationMs: Long = 0,

    /** Процент выполнения прогресса */
    val progressPercentage: Double = 0.0,

    /** Дополнительные данные для UI */
    val uiData: Map<String, Any> = emptyMap(),

    /** Статус для отображения пользователю */
    val displayStatus: String? = null,

    /** Подсказки для следующего шага */
    val nextStepHints: List<String> = emptyList(),

    /** Требования к вводу пользователя */
    val userInputRequirements: List<String> = emptyList()
) {
    /**
     * Возвращает эффективность выполнения (время / предполагаемое время)
     */
    fun getEfficiencyRatio(): Double {
        return if (estimatedDurationMs > 0) {
            actualDurationMs.toDouble() / estimatedDurationMs
        } else 1.0
    }

    /**
     * Проверяет, выполнялся ли шаг быстрее предполагаемого времени
     */
    fun isFasterThanEstimated(): Boolean {
        return getEfficiencyRatio() < 1.0
    }

    /**
     * Создает копию с обновленным временем выполнения
     */
    fun withActualDuration(durationMs: Long): PlanStepMetadata {
        return copy(actualDurationMs = durationMs)
    }

    /**
     * Создает копию с обновленным прогрессом
     */
    fun withProgress(percentage: Double): PlanStepMetadata {
        return copy(progressPercentage = percentage.coerceIn(0.0, 100.0))
    }
}