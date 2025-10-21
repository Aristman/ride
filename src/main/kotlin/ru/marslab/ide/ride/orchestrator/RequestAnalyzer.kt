package ru.marslab.ide.ride.orchestrator

import ru.marslab.ide.ride.model.orchestrator.RequestAnalysis
import ru.marslab.ide.ride.model.orchestrator.UserRequest

/**
 * Интерфейс для анализа пользовательских запросов
 *
 * Определяет тип задачи, необходимые инструменты, контекст и параметры
 * для выполнения запроса пользователя в системе оркестрации.
 */
interface RequestAnalyzer {

    /**
     * Анализирует пользовательский запрос и определяет как его выполнить
     *
     * @param request Пользовательский запрос для анализа
     * @return Результат анализа с типом задачи, инструментами и параметрами
     */
    suspend fun analyze(request: UserRequest): RequestAnalysis

    /**
     * Проверяет, может ли анализатор обработать данный тип запроса
     *
     * @param request Запрос для проверки
     * @return true, если запрос может быть обработан
     */
    fun canHandle(request: UserRequest): Boolean

    /**
     * Возвращает название анализатора
     */
    val name: String

    /**
     * Возвращает версию анализатора
     */
    val version: String
}