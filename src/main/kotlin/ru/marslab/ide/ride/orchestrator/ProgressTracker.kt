package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.*
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanStep
import ru.marslab.ide.ride.model.orchestrator.StepStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * Трекер прогресса выполнения планов
 *
 * Отслеживает прогресс выполнения каждого шага плана, рассчитывает ETA,
 * предоставляет события для UI и хранит историю выполнения.
 */
class ProgressTracker {

    private val logger = Logger.getInstance(ProgressTracker::class.java)
    private val progressListeners = mutableListOf<ProgressListener>()
    private val planProgress = mutableMapOf<String, PlanProgress>()
    private val stepHistory = mutableMapOf<String, MutableList<StepHistoryEntry>>()
    private val mutex = Mutex()

    /**
     * Добавляет слушателя прогресса
     */
    fun addListener(listener: ProgressListener) {
        progressListeners.add(listener)
    }

    /**
     * Удаляет слушателя прогресса
     */
    fun removeListener(listener: ProgressListener) {
        progressListeners.remove(listener)
    }

    /**
     * Начинает отслеживание плана
     */
    suspend fun startTracking(plan: ExecutionPlan) {
        mutex.withLock {
            val progress = PlanProgress(
                planId = plan.id,
                totalSteps = plan.steps.size,
                startTime = Clock.System.now(),
                estimatedDurationMs = calculateEstimatedDuration(plan)
            )

            planProgress[plan.id] = progress
            stepHistory[plan.id] = mutableListOf()

            logger.info("Started tracking progress for plan ${plan.id}")
            notifyProgressStarted(plan, progress)
        }
    }

    /**
     * Обновляет прогресс выполнения шага
     */
    suspend fun updateStepProgress(
        planId: String,
        stepId: String,
        progress: Double,
        status: StepStatus? = null,
        message: String? = null
    ) {
        mutex.withLock {
            val currentProgress = planProgress[planId] ?: run {
                logger.warn("No progress tracking for plan $planId")
                return
            }

            val previousProgress = currentProgress.copy()
            val updatedProgress = currentProgress.copy(
                currentStepProgress = progress,
                lastUpdateTime = Clock.System.now()
            )

            // Обновляем статус шага если указан
            if (status != null) {
                updateStepStatus(planId, stepId, status)
            }

            planProgress[planId] = updatedProgress

            // Добавляем запись в историю
            addToHistory(planId, stepId, progress, status, message)

            // Рассчитываем общий прогресс
            val overallProgress = calculateOverallProgress(planId, updatedProgress)
            val eta = calculateETA(planId, updatedProgress)

            logger.debug("Updated progress for plan $planId, step $stepId: ${progress}%")

            // Уведомляем слушателей
            notifyStepProgressUpdated(planId, stepId, progress, overallProgress, eta, message)
        }
    }

    /**
     * Отмечает шаг как завершенный
     */
    suspend fun completeStep(planId: String, stepId: String, result: Any? = null) {
        mutex.withLock {
            val currentProgress = planProgress[planId] ?: return
            val previousProgress = currentProgress.copy()

            // Обновляем статистику
            val completedSteps = currentProgress.completedSteps + 1
            val now = Clock.System.now()
            val totalElapsed = now.minus(currentProgress.startTime).inWholeMilliseconds

            val updatedProgress = currentProgress.copy(
                completedSteps = completedSteps,
                currentStepProgress = 100.0,
                lastUpdateTime = now
            )

            planProgress[planId] = updatedProgress

            // Добавляем запись в историю
            addToHistory(planId, stepId, 100.0, StepStatus.COMPLETED, "Step completed")

            // Рассчитываем новый ETA
            val eta = calculateETA(planId, updatedProgress)
            val overallProgress = calculateOverallProgress(planId, updatedProgress)

            logger.info("Completed step $stepId for plan $planId ($completedSteps/${currentProgress.totalSteps})")

            // Уведомляем слушателей
            notifyStepCompleted(planId, stepId, result, overallProgress, eta)
        }
    }

    /**
     * Отмечает шаг как неудачный
     */
    suspend fun failStep(planId: String, stepId: String, error: String) {
        mutex.withLock {
            val currentProgress = planProgress[planId] ?: return

            // Обновляем статистику
            val failedSteps = currentProgress.failedSteps + 1
            val now = Clock.System.now()

            val updatedProgress = currentProgress.copy(
                failedSteps = failedSteps,
                currentStepProgress = 0.0,
                lastUpdateTime = now
            )

            planProgress[planId] = updatedProgress

            // Добавляем запись в историю
            addToHistory(planId, stepId, 0.0, StepStatus.FAILED, error)

            val overallProgress = calculateOverallProgress(planId, updatedProgress)

            logger.error("Failed step $stepId for plan $planId: $error")

            // Уведомляем слушателей
            notifyStepFailed(planId, stepId, error, overallProgress)
        }
    }

    /**
     * Завершает отслеживание плана
     */
    suspend fun finishTracking(planId: String, success: Boolean) {
        mutex.withLock {
            val currentProgress = planProgress[planId] ?: return
            val now = Clock.System.now()
            val actualDuration = now.minus(currentProgress.startTime).inWholeMilliseconds

            val finalProgress = currentProgress.copy(
                endTime = now,
                isCompleted = true,
                success = success,
                actualDurationMs = actualDuration
            )

            planProgress[planId] = finalProgress

            logger.info("Finished tracking plan $planId (success: $success, duration: ${actualDuration}ms)")

            notifyProgressFinished(planId, finalProgress)
        }
    }

    /**
     * Возвращает текущий прогресс плана
     */
    suspend fun getProgress(planId: String): PlanProgress? {
        return mutex.withLock {
            planProgress[planId]?.copy()
        }
    }

    /**
     * Возвращает ETA для плана в миллисекундах
     */
    suspend fun getETA(planId: String): Long {
        return mutex.withLock {
            val progress = planProgress[planId] ?: return -1
            calculateETA(planId, progress)
        }
    }

    /**
     * Возвращает прогресс в процентах
     */
    suspend fun getProgressPercentage(planId: String): Double {
        return mutex.withLock {
            val progress = planProgress[planId] ?: return 0.0
            calculateOverallProgress(planId, progress)
        }
    }

    /**
     * Возвращает историю выполнения шагов
     */
    suspend fun getStepHistory(planId: String): List<StepHistoryEntry> {
        return mutex.withLock {
            stepHistory[planId]?.toList() ?: emptyList()
        }
    }

    /**
     * Очищает данные отслеживания для плана
     */
    suspend fun clearTracking(planId: String) {
        mutex.withLock {
            planProgress.remove(planId)
            stepHistory.remove(planId)
            logger.info("Cleared tracking data for plan $planId")
        }
    }

    // Приватные методы

    private fun updateStepStatus(planId: String, stepId: String, status: StepStatus) {
        stepHistory[planId]?.let { history ->
            val lastEntry = history.lastOrNull { it.stepId == stepId }
            if (lastEntry != null) {
                val updatedEntry = lastEntry.copy(status = status)
                val index = history.indexOf(lastEntry)
                if (index >= 0) {
                    history[index] = updatedEntry
                }
            }
        }
    }

    private fun addToHistory(
        planId: String,
        stepId: String,
        progress: Double,
        status: StepStatus?,
        message: String?
    ) {
        val history = stepHistory.getOrPut(planId) { mutableListOf() }
        history.add(
            StepHistoryEntry(
                stepId = stepId,
                progress = progress,
                status = status ?: StepStatus.PENDING,
                message = message,
                timestamp = Clock.System.now()
            )
        )
    }

    private fun calculateOverallProgress(planId: String, progress: PlanProgress): Double {
        val history = stepHistory[planId] ?: return 0.0

        if (progress.totalSteps == 0) return 0.0

        // Учитываем завершенные шаги
        val completedSteps = progress.completedSteps.toDouble()
        val failedSteps = progress.failedSteps.toDouble()
        val inProgressStepWeight = progress.currentStepProgress / 100.0

        val totalWeight = completedSteps + failedSteps + inProgressStepWeight
        return (totalWeight / progress.totalSteps) * 100.0
    }

    private fun calculateETA(planId: String, progress: PlanProgress): Long {
        if (progress.completedSteps == 0) return progress.estimatedDurationMs

        val now = Clock.System.now()
        val elapsedMs = now.minus(progress.startTime).inWholeMilliseconds
        val averageStepTime = elapsedMs / progress.completedSteps

        val remainingSteps = progress.totalSteps - progress.completedSteps - progress.failedSteps
        return remainingSteps * averageStepTime
    }

    private fun calculateEstimatedDuration(plan: ExecutionPlan): Long {
        // Базовая оценка на основе сложности и количества шагов
        val baseTimePerStep = when (plan.analysis.estimatedComplexity) {
            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.LOW -> 30_000L      // 30 секунд
            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.MEDIUM -> 60_000L   // 1 минута
            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.HIGH -> 120_000L    // 2 минуты
            ru.marslab.ide.ride.model.orchestrator.ComplexityLevel.VERY_HIGH -> 300_000L // 5 минут
        }

        // Учитываем предполагаемую длительность каждого шага
        val stepBasedTime = plan.steps.sumOf { step ->
            step.estimatedDurationMs.takeIf { it > 0 } ?: baseTimePerStep
        }

        return stepBasedTime
    }

    // Методы уведомления слушателей

    private fun notifyProgressStarted(plan: ExecutionPlan, progress: PlanProgress) {
        progressListeners.forEach { listener ->
            try {
                listener.onProgressStarted(plan, progress)
            } catch (e: Exception) {
                logger.error("Error notifying progress listener", e)
            }
        }
    }

    private fun notifyStepProgressUpdated(
        planId: String,
        stepId: String,
        stepProgress: Double,
        overallProgress: Double,
        eta: Long,
        message: String?
    ) {
        progressListeners.forEach { listener ->
            try {
                listener.onStepProgressUpdated(planId, stepId, stepProgress, overallProgress, eta, message)
            } catch (e: Exception) {
                logger.error("Error notifying progress listener", e)
            }
        }
    }

    private fun notifyStepCompleted(
        planId: String,
        stepId: String,
        result: Any?,
        overallProgress: Double,
        eta: Long
    ) {
        progressListeners.forEach { listener ->
            try {
                listener.onStepCompleted(planId, stepId, result, overallProgress, eta)
            } catch (e: Exception) {
                logger.error("Error notifying progress listener", e)
            }
        }
    }

    private fun notifyStepFailed(planId: String, stepId: String, error: String, overallProgress: Double) {
        progressListeners.forEach { listener ->
            try {
                listener.onStepFailed(planId, stepId, error, overallProgress)
            } catch (e: Exception) {
                logger.error("Error notifying progress listener", e)
            }
        }
    }

    private fun notifyProgressFinished(planId: String, progress: PlanProgress) {
        progressListeners.forEach { listener ->
            try {
                listener.onProgressFinished(planId, progress)
            } catch (e: Exception) {
                logger.error("Error notifying progress listener", e)
            }
        }
    }
}

/**
 * Данные о прогрессе выполнения плана
 */
data class PlanProgress(
    val planId: String,
    val totalSteps: Int,
    val completedSteps: Int = 0,
    val failedSteps: Int = 0,
    val currentStepProgress: Double = 0.0,
    val startTime: Instant,
    val lastUpdateTime: Instant = Clock.System.now(),
    val endTime: Instant? = null,
    val isCompleted: Boolean = false,
    val success: Boolean = false,
    val estimatedDurationMs: Long = 0L,
    val actualDurationMs: Long = 0L
)

/**
 * Запись в истории выполнения шага
 */
data class StepHistoryEntry(
    val stepId: String,
    val progress: Double,
    val status: StepStatus,
    val message: String?,
    val timestamp: Instant
)

/**
 * Интерфейс слушателя прогресса
 */
interface ProgressListener {
    fun onProgressStarted(plan: ExecutionPlan, progress: PlanProgress) {}
    fun onStepProgressUpdated(
        planId: String,
        stepId: String,
        stepProgress: Double,
        overallProgress: Double,
        eta: Long,
        message: String?
    ) {}

    fun onStepCompleted(
        planId: String,
        stepId: String,
        result: Any?,
        overallProgress: Double,
        eta: Long
    ) {}

    fun onStepFailed(planId: String, stepId: String, error: String, overallProgress: Double) {}
    fun onProgressFinished(planId: String, progress: PlanProgress) {}
}