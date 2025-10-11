package ru.marslab.ide.ride.model

import kotlinx.serialization.Serializable

/**
 * План задач, созданный PlannerAgent
 *
 * @property description Описание общей цели плана
 * @property tasks Список задач для выполнения
 */
@Serializable
data class TaskPlan(
    val description: String,
    val tasks: List<TaskItem>
) {
    /**
     * Проверяет, пуст ли план
     */
    fun isEmpty(): Boolean = tasks.isEmpty()
    
    /**
     * Возвращает количество задач в плане
     */
    fun size(): Int = tasks.size
}
