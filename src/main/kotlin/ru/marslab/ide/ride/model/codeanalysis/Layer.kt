package ru.marslab.ide.ride.model.codeanalysis

/**
 * Архитектурный слой проекта
 *
 * @property name Название слоя
 * @property modules Модули в этом слое
 */
data class Layer(
    val name: String,
    val modules: List<String>
)
