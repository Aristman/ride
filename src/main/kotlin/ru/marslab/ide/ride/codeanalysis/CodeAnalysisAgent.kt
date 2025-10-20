package ru.marslab.ide.ride.codeanalysis

import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.model.codeanalysis.*

/**
 * Агент для анализа кода проекта
 * 
 * Специализированный агент для:
 * - Поиска багов и потенциальных проблем
 * - Анализа архитектуры проекта
 * - Оценки качества кода
 * - Поиска уязвимостей безопасности
 * - Анализа зависимостей
 */
interface CodeAnalysisAgent : Agent {
    /**
     * Анализирует проект по указанному пути
     * 
     * @param request Запрос на анализ с параметрами
     * @return Результат анализа
     */
    suspend fun analyzeProject(request: CodeAnalysisRequest): CodeAnalysisResult

    /**
     * Анализирует конкретный файл
     * 
     * @param filePath Путь к файлу
     * @param analysisTypes Типы анализа для выполнения
     * @return Список найденных проблем
     */
    suspend fun analyzeFile(filePath: String, analysisTypes: Set<AnalysisType>): List<Finding>

    /**
     * Строит структуру проекта
     * 
     * @param projectPath Путь к проекту
     * @return Структура проекта
     */
    suspend fun buildProjectStructure(projectPath: String): ProjectStructure

    /**
     * Генерирует отчет в указанном формате
     * 
     * @param result Результат анализа
     * @param format Формат отчета
     * @return Отчет в виде строки
     */
    fun generateReport(result: CodeAnalysisResult, format: ReportFormat): String
}
