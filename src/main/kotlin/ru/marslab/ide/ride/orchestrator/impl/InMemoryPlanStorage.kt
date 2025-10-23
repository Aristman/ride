package ru.marslab.ide.ride.orchestrator.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanState
import ru.marslab.ide.ride.orchestrator.PlanStorage
import ru.marslab.ide.ride.orchestrator.StorageStats
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory реализация хранилища планов для MVP
 *
 * Хранит планы в памяти с использованием потокобезопасных структур данных.
 * Подходит для разработки и тестирования, но не сохраняет данные между перезапусками.
 */
class InMemoryPlanStorage : PlanStorage {

    private val logger = Logger.getInstance(InMemoryPlanStorage::class.java)
    private val plans = ConcurrentHashMap<String, ExecutionPlan>()
    private val mutex = Mutex()

    override suspend fun save(plan: ExecutionPlan): String {
        return mutex.withLock {
            val planToSave = plan.copy(
                id = plan.id.ifBlank { generateId() }
            )

            plans[planToSave.id] = planToSave
            logger.info("Saved plan ${planToSave.id} with state ${planToSave.currentState}")
            planToSave.id
        }
    }

    override suspend fun load(planId: String): ExecutionPlan? {
        return mutex.withLock {
            plans[planId]?.also {
                logger.debug("Loaded plan $planId")
            }
        }
    }

    override suspend fun update(plan: ExecutionPlan) {
        mutex.withLock {
            val existing = plans[plan.id]
            if (existing != null) {
                plans[plan.id] = plan.copy(version = existing.version + 1)
                logger.debug("Updated plan ${plan.id} to version ${existing.version + 1}")
            } else {
                logger.warn("Attempted to update non-existent plan ${plan.id}")
                // Создаем новый план если не существует
                plans[plan.id] = plan
            }
        }
    }

    override suspend fun delete(planId: String) {
        mutex.withLock {
            plans.remove(planId)?.also {
                logger.info("Deleted plan $planId")
            }
        }
    }

    override suspend fun exists(planId: String): Boolean {
        return mutex.withLock {
            plans.containsKey(planId)
        }
    }

    override suspend fun listActive(): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { plan ->
                plan.currentState != PlanState.COMPLETED &&
                        plan.currentState != PlanState.CANCELLED &&
                        plan.currentState != PlanState.FAILED
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun listByState(state: PlanState): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { it.currentState == state }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun listCompleted(limit: Int, offset: Int): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { it.currentState == PlanState.COMPLETED }
                .sortedByDescending { it.completedAt ?: it.createdAt }
                .drop(offset)
                .take(limit)
        }
    }

    override suspend fun listFailed(limit: Int): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { it.currentState == PlanState.FAILED }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }
    }

    override suspend fun findByUserRequest(userRequestId: String): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { it.userRequestId == userRequestId }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun searchByContent(searchText: String): List<ExecutionPlan> {
        return mutex.withLock {
            val searchTextLower = searchText.lowercase()
            plans.values.filter { plan ->
                plan.originalRequest.lowercase().contains(searchTextLower) ||
                        plan.analysis.reasoning.lowercase().contains(searchTextLower)
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun findByTimeRange(
        fromTime: kotlinx.datetime.Instant,
        toTime: kotlinx.datetime.Instant
    ): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.filter { plan ->
                plan.createdAt >= fromTime && plan.createdAt <= toTime
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun cleanup(
        olderThan: kotlinx.datetime.Instant,
        states: Set<PlanState>
    ): Int {
        return mutex.withLock {
            val plansToDelete = plans.values.filter { plan ->
                plan.currentState in states && plan.createdAt < olderThan
            }

            plansToDelete.forEach { plan ->
                plans.remove(plan.id)
            }

            logger.info("Cleaned up ${plansToDelete.size} old plans")
            plansToDelete.size
        }
    }

    override suspend fun getStorageStats(): StorageStats {
        return mutex.withLock {
            val totalPlans = plans.size
            val activePlans = plans.values.count { plan ->
                plan.currentState != PlanState.COMPLETED &&
                        plan.currentState != PlanState.CANCELLED &&
                        plan.currentState != PlanState.FAILED
            }
            val completedPlans = plans.values.count { it.currentState == PlanState.COMPLETED }
            val failedPlans = plans.values.count { it.currentState == PlanState.FAILED }
            val cancelledPlans = plans.values.count { it.currentState == PlanState.CANCELLED }

            val timestamps = plans.values.map { it.createdAt }
            val oldestPlan = timestamps.minOrNull()
            val newestPlan = timestamps.maxOrNull()

            // Приблизительный размер в байтах (очень грубая оценка)
            val storageSizeBytes = plans.values.sumOf { plan ->
                plan.toString().length.toLong() * 2 // UTF-16
            }

            StorageStats(
                totalPlans = totalPlans,
                activePlans = activePlans,
                completedPlans = completedPlans,
                failedPlans = failedPlans,
                cancelledPlans = cancelledPlans,
                storageSizeBytes = storageSizeBytes,
                oldestPlan = oldestPlan,
                newestPlan = newestPlan
            )
        }
    }

    override suspend fun backup(backupPath: String): Boolean {
        logger.warn("InMemoryPlanStorage does not support backup to file: $backupPath")
        return false
    }

    override suspend fun restore(backupPath: String): Boolean {
        logger.warn("InMemoryPlanStorage does not support restore from file: $backupPath")
        return false
    }

    /**
     * Очищает все планы из хранилища
     */
    suspend fun clear() {
        mutex.withLock {
            plans.clear()
            logger.info("Cleared all plans from storage")
        }
    }

    /**
     * Возвращает количество планов в хранилище
     */
    suspend fun size(): Int {
        return mutex.withLock {
            plans.size
        }
    }

    /**
     * Проверяет, пусто ли хранилище
     */
    suspend fun isEmpty(): Boolean {
        return mutex.withLock {
            plans.isEmpty()
        }
    }

    /**
     * Возвращает все планы (для отладки)
     */
    suspend fun getAllPlans(): List<ExecutionPlan> {
        return mutex.withLock {
            plans.values.toList()
        }
    }

    private fun generateId(): String {
        return "plan_${Clock.System.now().epochSeconds}_${(0..9999).random()}"
    }

    companion object {
        /**
         * Создает экземпляр с предустановленными тестовыми данными
         */
        fun withTestData(): InMemoryPlanStorage {
            val storage = InMemoryPlanStorage()
            // Здесь можно добавить тестовые данные если нужно
            return storage
        }
    }
}