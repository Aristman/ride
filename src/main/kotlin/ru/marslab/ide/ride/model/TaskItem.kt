package ru.marslab.ide.ride.model

import kotlinx.serialization.Serializable

/**
 * Отдельная задача в плане
 *
 * @property id Уникальный идентификатор задачи
 * @property title Краткое название задачи
 * @property description Подробное описание задачи
 * @property prompt Промпт для ExecutorAgent
 */
@Serializable
data class TaskItem(
    val id: Int,
    val title: String,
    val description: String,
    val prompt: String
)
