package ru.marslab.ide.ride.model.orchestrator

import kotlinx.datetime.Instant
import java.util.*

/**
 * Состояния плана выполнения
 */
enum class PlanState {
    /** План создан */
    CREATED,

    /** План анализируется */
    ANALYZING,

    /** План выполняется */
    IN_PROGRESS,

    /** План приостановлен */
    PAUSED,

    /** Требуется ввод от пользователя */
    REQUIRES_INPUT,

    /** План возобновлен */
    RESUMED,

    /** План завершен */
    COMPLETED,

    /** План завершен с ошибкой */
    FAILED,

    /** План отменен */
    CANCELLED
}

/**
 * События для изменения состояния плана
 */
sealed class PlanEvent {
    data class Start(val analysis: RequestAnalysis) : PlanEvent()
    object Pause : PlanEvent()
    object Resume : PlanEvent()
    data class UserInputReceived(val input: String) : PlanEvent()
    data class StepCompleted(val stepId: String, val result: Any) : PlanEvent()
    data class StepFailed(val stepId: String, val error: String) : PlanEvent()
    data class Error(val error: String, val cause: Throwable? = null) : PlanEvent()
    object Complete : PlanEvent()
    object Cancel : PlanEvent()
}

/**
 * Статус выполнения шага плана
 */
enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Шаг плана выполнения
 */
data class PlanStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val agentType: AgentType,
    val input: Map<String, Any> = emptyMap(),
    val dependencies: Set<String> = emptySet(),
    var status: StepStatus = StepStatus.PENDING,
    var output: Any? = null,
    var error: String? = null,
    val estimatedDurationMs: Long = 0L,
    val actualDurationMs: Long = 0L,
    val createdAt: Instant = kotlinx.datetime.Clock.System.now()
)

/**
 * План выполнения задачи
 */
data class ExecutionPlan(
    val id: String = UUID.randomUUID().toString(),
    val userRequestId: String,
    val originalRequest: String,
    val analysis: RequestAnalysis,
    val steps: List<PlanStep>,
    var currentState: PlanState = PlanState.CREATED,
    val createdAt: Instant = kotlinx.datetime.Clock.System.now(),
    var startedAt: Instant? = null,
    var completedAt: Instant? = null,
    val version: Int = 1,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Возвращает шаги, которые готовы к выполнению (все зависимости выполнены)
     */
    fun getReadySteps(): List<PlanStep> {
        return steps.filter { step ->
            step.status == StepStatus.PENDING &&
            step.dependencies.all { depId ->
                steps.find { it.id == depId }?.status == StepStatus.COMPLETED
            }
        }
    }

    /**
     * Возвращает шаги по указанному статусу
     */
    fun getStepsByStatus(status: StepStatus): List<PlanStep> {
        return steps.filter { it.status == status }
    }

    /**
     * Обновляет статус шага
     */
    fun updateStepStatus(stepId: String, status: StepStatus, output: Any? = null, error: String? = null): ExecutionPlan {
        val updatedSteps = steps.map { step ->
            if (step.id == stepId) {
                step.copy(
                    status = status,
                    output = output,
                    error = error,
                    actualDurationMs = if (status == StepStatus.COMPLETED) {
                        kotlinx.datetime.Clock.System.now().minus(step.createdAt).inWholeMilliseconds
                    } else step.actualDurationMs
                )
            } else step
        }
        return copy(steps = updatedSteps)
    }

    /**
     * Проверяет, все ли шаги завершены
     */
    fun isAllStepsCompleted(): Boolean {
        return steps.all { it.status == StepStatus.COMPLETED || it.status == StepStatus.SKIPPED }
    }

    /**
     * Проверяет, есть ли ошибки в шагах
     */
    fun hasErrors(): Boolean {
        return steps.any { it.status == StepStatus.FAILED }
    }

    /**
     * Возвращает прогресс выполнения в процентах
     */
    fun getProgress(): Double {
        if (steps.isEmpty()) return 0.0
        val completedSteps = steps.count { it.status == StepStatus.COMPLETED || it.status == StepStatus.SKIPPED }
        return (completedSteps.toDouble() / steps.size) * 100
    }
}

/**
 * Exception для невалидных переходов состояний
 */
class InvalidStateTransitionException(
    val fromState: PlanState,
    val toState: PlanState,
    val event: PlanEvent
) : Exception("Invalid state transition from $fromState to $toState with event $event")