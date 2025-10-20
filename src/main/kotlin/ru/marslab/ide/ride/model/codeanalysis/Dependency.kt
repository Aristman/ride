package ru.marslab.ide.ride.model.codeanalysis

/**
 * Зависимость между модулями
 *
 * @property from Модуль-источник
 * @property to Модуль-назначение
 * @property type Тип зависимости
 */
data class Dependency(
    val from: String,
    val to: String,
    val type: String = "uses"
)
