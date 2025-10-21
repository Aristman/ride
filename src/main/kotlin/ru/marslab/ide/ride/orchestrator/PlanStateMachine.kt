package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.orchestrator.*

/**
 * Машина состояний для управления жизненным циклом планов выполнения
 *
 * Обеспечивает валидацию переходов между состояниями и уведомляет слушателей
 * об изменениях состояния плана.
 */
class PlanStateMachine {

    private val logger = Logger.getInstance(PlanStateMachine::class.java)
    private val listeners = mutableListOf<StateChangeListener>()
    private val stateHistory = mutableListOf<StateTransition>()

    /**
     * Добавляет слушателя изменений состояния
     */
    fun addListener(listener: StateChangeListener) {
        listeners.add(listener)
    }

    /**
     * Удаляет слушателя изменений состояния
     */
    fun removeListener(listener: StateChangeListener) {
        listeners.remove(listener)
    }

    /**
     * Выполняет переход между состояниями плана
     *
     * @param plan План выполнения
     * @param event Событие, вызывающее переход
     * @return Обновленный план с новым состоянием
     * @throws InvalidStateTransitionException если переход невалиден
     */
    fun transition(plan: ExecutionPlan, event: PlanEvent): ExecutionPlan {
        val fromState = plan.currentState
        val toState = calculateTargetState(fromState, event)

        if (!isValidTransition(fromState, toState, event)) {
            val error = InvalidStateTransitionException(fromState, toState, event)
            logger.error("Invalid state transition: ${error.message}")
            throw error
        }

        logger.info("Transitioning plan ${plan.id} from $fromState to $toState with event $event")

        // Создаем переход в истории
        val transition = StateTransition(
            fromState = fromState,
            toState = toState,
            event = event,
            timestamp = kotlinx.datetime.Clock.System.now()
        )
        stateHistory.add(transition)

        // Обновляем план
        val updatedPlan = when (toState) {
            PlanState.IN_PROGRESS -> {
                plan.copy(
                    currentState = PlanState.IN_PROGRESS,
                    startedAt = kotlinx.datetime.Clock.System.now()
                )
            }
            PlanState.COMPLETED -> {
                plan.copy(
                    currentState = toState,
                    completedAt = kotlinx.datetime.Clock.System.now()
                )
            }
            else -> {
                plan.copy(currentState = toState)
            }
        }

        // Уведомляем слушателей
        notifyStateChange(updatedPlan, fromState, toState, event)

        return updatedPlan
    }

    /**
     * Проверяет, является ли переход валидным
     */
    fun isValidTransition(fromState: PlanState, toState: PlanState, event: PlanEvent): Boolean {
        return when (fromState) {
            PlanState.CREATED -> {
                toState == PlanState.ANALYZING && event is PlanEvent.Start
            }
            PlanState.ANALYZING -> {
                when (toState) {
                    PlanState.IN_PROGRESS -> event is PlanEvent.Start
                    PlanState.REQUIRES_INPUT -> event is PlanEvent.Start
                    PlanState.FAILED -> event is PlanEvent.Error
                    PlanState.CANCELLED -> event is PlanEvent.Cancel
                    else -> false
                }
            }
            PlanState.IN_PROGRESS -> {
                when (toState) {
                    PlanState.PAUSED -> event is PlanEvent.Pause
                    PlanState.REQUIRES_INPUT -> event is PlanEvent.UserInputReceived
                    PlanState.COMPLETED -> event is PlanEvent.Complete
                    PlanState.FAILED -> event is PlanEvent.Error || event is PlanEvent.StepFailed
                    PlanState.CANCELLED -> event is PlanEvent.Cancel
                    else -> false
                }
            }
            PlanState.PAUSED -> {
                when (toState) {
                    PlanState.RESUMED -> event is PlanEvent.Resume
                    PlanState.CANCELLED -> event is PlanEvent.Cancel
                    else -> false
                }
            }
            PlanState.RESUMED -> {
                toState == PlanState.IN_PROGRESS && event is PlanEvent.Resume
            }
            PlanState.REQUIRES_INPUT -> {
                when (toState) {
                    PlanState.IN_PROGRESS -> event is PlanEvent.UserInputReceived
                    PlanState.CANCELLED -> event is PlanEvent.Cancel
                    else -> false
                }
            }
            PlanState.COMPLETED -> {
                // Завершенное состояние не может быть изменено
                false
            }
            PlanState.FAILED -> {
                // Можно только повторить попытку или отменить
                when (toState) {
                    PlanState.ANALYZING -> event is PlanEvent.Start
                    PlanState.CANCELLED -> event is PlanEvent.Cancel
                    else -> false
                }
            }
            PlanState.CANCELLED -> {
                // Отмененное состояние не может быть изменено
                false
            }
            else -> false
        }
    }

    /**
     * Вычисляет целевое состояние на основе текущего состояния и события
     */
    private fun calculateTargetState(currentState: PlanState, event: PlanEvent): PlanState {
        return when (currentState) {
            PlanState.CREATED -> {
                when (event) {
                    is PlanEvent.Start -> PlanState.ANALYZING
                    else -> currentState
                }
            }
            PlanState.ANALYZING -> {
                when (event) {
                    is PlanEvent.Start -> {
                        if (event.analysis.requiresUserInput) {
                            PlanState.REQUIRES_INPUT
                        } else {
                            PlanState.IN_PROGRESS
                        }
                    }
                    is PlanEvent.Error -> PlanState.FAILED
                    is PlanEvent.Cancel -> PlanState.CANCELLED
                    else -> currentState
                }
            }
            PlanState.IN_PROGRESS -> {
                when (event) {
                    is PlanEvent.Pause -> PlanState.PAUSED
                    is PlanEvent.UserInputReceived -> PlanState.REQUIRES_INPUT
                    is PlanEvent.Complete -> PlanState.COMPLETED
                    is PlanEvent.Error, is PlanEvent.StepFailed -> PlanState.FAILED
                    is PlanEvent.Cancel -> PlanState.CANCELLED
                    else -> currentState
                }
            }
            PlanState.PAUSED -> {
                when (event) {
                    is PlanEvent.Resume -> PlanState.RESUMED
                    is PlanEvent.Cancel -> PlanState.CANCELLED
                    else -> currentState
                }
            }
            PlanState.RESUMED -> {
                when (event) {
                    is PlanEvent.Resume -> PlanState.IN_PROGRESS
                    else -> currentState
                }
            }
            PlanState.REQUIRES_INPUT -> {
                when (event) {
                    is PlanEvent.UserInputReceived -> PlanState.IN_PROGRESS
                    is PlanEvent.Cancel -> PlanState.CANCELLED
                    else -> currentState
                }
            }
            PlanState.FAILED -> {
                when (event) {
                    is PlanEvent.Start -> PlanState.ANALYZING
                    is PlanEvent.Cancel -> PlanState.CANCELLED
                    else -> currentState
                }
            }
            PlanState.COMPLETED, PlanState.CANCELLED -> currentState
        }
    }

    /**
     * Уведомляет всех слушателей об изменении состояния
     */
    private fun notifyStateChange(plan: ExecutionPlan, fromState: PlanState, toState: PlanState, event: PlanEvent) {
        listeners.forEach { listener ->
            try {
                listener.onStateChanged(plan, fromState, toState, event)
            } catch (e: Exception) {
                logger.error("Error notifying state change listener", e)
            }
        }
    }

    /**
     * Возвращает историю переходов состояний для плана
     */
    fun getStateHistory(planId: String): List<StateTransition> {
        return stateHistory.filter {
            // TODO: добавить planId в StateTransition
            true
        }
    }

    /**
     * Очищает историю состояний
     */
    fun clearHistory() {
        stateHistory.clear()
    }

    /**
     * Возвращает все возможные переходы из указанного состояния
     */
    fun getPossibleTransitions(fromState: PlanState): List<PlanState> {
        return when (fromState) {
            PlanState.CREATED -> listOf(PlanState.ANALYZING)
            PlanState.ANALYZING -> listOf(PlanState.IN_PROGRESS, PlanState.REQUIRES_INPUT, PlanState.FAILED, PlanState.CANCELLED)
            PlanState.IN_PROGRESS -> listOf(PlanState.PAUSED, PlanState.REQUIRES_INPUT, PlanState.COMPLETED, PlanState.FAILED, PlanState.CANCELLED)
            PlanState.PAUSED -> listOf(PlanState.RESUMED, PlanState.CANCELLED)
            PlanState.RESUMED -> listOf(PlanState.IN_PROGRESS)
            PlanState.REQUIRES_INPUT -> listOf(PlanState.IN_PROGRESS, PlanState.CANCELLED)
            PlanState.FAILED -> listOf(PlanState.ANALYZING, PlanState.CANCELLED)
            PlanState.COMPLETED, PlanState.CANCELLED -> emptyList()
        }
    }

    /**
     * Проверяет, может ли план быть отменен в текущем состоянии
     */
    fun canCancel(plan: ExecutionPlan): Boolean {
        return plan.currentState != PlanState.COMPLETED && plan.currentState != PlanState.CANCELLED
    }

    /**
     * Проверяет, может ли план быть приостановлен в текущем состоянии
     */
    fun canPause(plan: ExecutionPlan): Boolean {
        return plan.currentState == PlanState.IN_PROGRESS
    }

    /**
     * Проверяет, может ли план быть возобновлен в текущем состоянии
     */
    fun canResume(plan: ExecutionPlan): Boolean {
        return plan.currentState == PlanState.PAUSED
    }
}

/**
 * Интерфейс слушателя изменений состояния
 */
interface StateChangeListener {
    /**
     * Вызывается при изменении состояния плана
     */
    fun onStateChanged(plan: ExecutionPlan, fromState: PlanState, toState: PlanState, event: PlanEvent)
}

/**
 * Запись о переходе между состояниями
 */
data class StateTransition(
    val fromState: PlanState,
    val toState: PlanState,
    val event: PlanEvent,
    val timestamp: kotlinx.datetime.Instant
)