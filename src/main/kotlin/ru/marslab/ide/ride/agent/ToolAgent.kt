package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*

/**
 * Интерфейс для специализированных агентов-инструментов
 * 
 * ToolAgent расширяет базовый Agent и добавляет возможность
 * выполнения отдельных шагов плана с поддержкой capabilities
 */
interface ToolAgent : Agent {
    /**
     * Тип агента
     */
    val agentType: AgentType
    
    /**
     * Набор возможностей агента
     */
    val toolCapabilities: Set<String>
    
    /**
     * Выполняет шаг плана
     * 
     * @param step Шаг для выполнения
     * @param context Контекст выполнения
     * @return Результат выполнения шага
     */
    suspend fun executeStep(step: ToolPlanStep, context: ExecutionContext): StepResult
    
    /**
     * Проверяет, может ли агент обработать данный шаг
     * 
     * @param step Шаг для проверки
     * @return true, если агент может обработать шаг
     */
    fun canHandle(step: ToolPlanStep): Boolean
    
    /**
     * Валидирует входные данные для шага
     * 
     * @param input Входные данные
     * @return Результат валидации
     */
    fun validateInput(input: StepInput): ValidationResult {
        return ValidationResult.success()
    }
}

/**
 * Результат валидации входных данных
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success() = ValidationResult(true)
        fun failure(vararg errors: String) = ValidationResult(false, errors.toList())
    }
}
