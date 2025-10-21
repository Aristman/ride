package ru.marslab.ide.ride.orchestrator

import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanState

/**
 * Интерфейс для хранения и управления планами выполнения
 *
 * Предоставляет методы для сохранения, загрузки, обновления и удаления планов,
 * а также для поиска планов по различным критериям.
 */
interface PlanStorage {

    /**
     * Сохраняет план в хранилище
     *
     * @param plan План для сохранения
     * @return ID сохраненного плана
     */
    suspend fun save(plan: ExecutionPlan): String

    /**
     * Загружает план по ID
     *
     * @param planId ID плана
     * @return План или null если не найден
     */
    suspend fun load(planId: String): ExecutionPlan?

    /**
     * Обновляет существующий план
     *
     * @param plan Обновленный план
     */
    suspend fun update(plan: ExecutionPlan)

    /**
     * Удаляет план по ID
     *
     * @param planId ID плана для удаления
     */
    suspend fun delete(planId: String)

    /**
     * Проверяет существование плана
     *
     * @param planId ID плана
     * @return true если план существует
     */
    suspend fun exists(planId: String): Boolean

    /**
     * Возвращает список всех активных планов
     * (не завершенных и не отмененных)
     *
     * @return Список активных планов
     */
    suspend fun listActive(): List<ExecutionPlan>

    /**
     * Возвращает список планов по указанному состоянию
     *
     * @param state Состояние для фильтрации
     * @return Список планов в указанном состоянии
     */
    suspend fun listByState(state: PlanState): List<ExecutionPlan>

    /**
     * Возвращает список завершенных планов
     *
     * @param limit Максимальное количество планов
     * @param offset Смещение для пагинации
     * @return Список завершенных планов
     */
    suspend fun listCompleted(limit: Int = 100, offset: Int = 0): List<ExecutionPlan>

    /**
     * Возвращает список неудачных планов
     *
     * @param limit Максимальное количество планов
     * @return Список неудачных планов
     */
    suspend fun listFailed(limit: Int = 100): List<ExecutionPlan>

    /**
     * Ищет планы по пользовательскому запросу
     *
     * @param userRequestId ID пользовательского запроса
     * @return Список планов для указанного запроса
     */
    suspend fun findByUserRequest(userRequestId: String): List<ExecutionPlan>

    /**
     * Ищет планы по содержимому оригинального запроса
     *
     * @param searchText Текст для поиска
     * @return Список планов, содержащих указанный текст
     */
    suspend fun searchByContent(searchText: String): List<ExecutionPlan>

    /**
     * Возвращает планы, созданные в указанном временном диапазоне
     *
     * @param fromTime Начальное время
     * @param toTime Конечное время
     * @return Список планов из указанного диапазона
     */
    suspend fun findByTimeRange(
        fromTime: kotlinx.datetime.Instant,
        toTime: kotlinx.datetime.Instant
    ): List<ExecutionPlan>

    /**
     * Очищает старые планы
     *
     * @param olderThan Время, старше которого планы будут удалены
     * @param states Состояния планов для очистки (по умолчанию только завершенные)
     * @return Количество удаленных планов
     */
    suspend fun cleanup(
        olderThan: kotlinx.datetime.Instant,
        states: Set<PlanState> = setOf(PlanState.COMPLETED, PlanState.CANCELLED)
    ): Int

    /**
     * Возвращает статистику по планам
     *
     * @return Статистика хранения
     */
    suspend fun getStorageStats(): StorageStats

    /**
     * Выполняет резервное копирование хранилища
     *
     * @param backupPath Путь для сохранения резервной копии
     * @return true если резервное копирование успешно
     */
    suspend fun backup(backupPath: String): Boolean

    /**
     * Восстанавливает хранилище из резервной копии
     *
     * @param backupPath Путь к резервной копии
     * @return true если восстановление успешно
     */
    suspend fun restore(backupPath: String): Boolean
}

/**
 * Статистика хранилища планов
 */
data class StorageStats(
    val totalPlans: Int,
    val activePlans: Int,
    val completedPlans: Int,
    val failedPlans: Int,
    val cancelledPlans: Int,
    val storageSizeBytes: Long,
    val oldestPlan: kotlinx.datetime.Instant?,
    val newestPlan: kotlinx.datetime.Instant?
)