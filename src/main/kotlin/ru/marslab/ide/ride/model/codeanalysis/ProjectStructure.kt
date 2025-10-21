package ru.marslab.ide.ride.model.codeanalysis

/**
 * Структура проекта
 *
 * @property rootPackage Корневой пакет проекта
 * @property modules Модули проекта
 * @property layers Архитектурные слои
 * @property dependencies Зависимости между модулями
 */
data class ProjectStructure(
    val rootPackage: String,
    val modules: List<Module>,
    val layers: List<Layer>,
    val dependencies: List<Dependency>
)
