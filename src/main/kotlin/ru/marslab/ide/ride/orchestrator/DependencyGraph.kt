package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.orchestrator.PlanStep

/**
 * Граф зависимостей для планов выполнения (DAG - Directed Acyclic Graph)
 * 
 * Используется для:
 * - Определения порядка выполнения шагов
 * - Выявления независимых шагов для параллельного выполнения
 * - Обнаружения циклических зависимостей
 */
class DependencyGraph(private val steps: List<PlanStep>) {
    private val logger = Logger.getInstance(DependencyGraph::class.java)
    
    /**
     * Граф зависимостей: stepId -> Set<dependencyIds>
     */
    private val graph: Map<String, Set<String>> = buildGraph()
    
    /**
     * Обратный граф: stepId -> Set<dependentIds> (кто зависит от этого шага)
     */
    private val reverseGraph: Map<String, Set<String>> = buildReverseGraph()
    
    /**
     * Строит граф зависимостей из списка шагов
     */
    private fun buildGraph(): Map<String, Set<String>> {
        return steps.associate { step ->
            step.id to step.dependencies
        }
    }
    
    /**
     * Строит обратный граф (кто зависит от каждого шага)
     */
    private fun buildReverseGraph(): Map<String, Set<String>> {
        val reverse = mutableMapOf<String, MutableSet<String>>()
        
        graph.forEach { (stepId, dependencies) ->
            dependencies.forEach { depId ->
                reverse.getOrPut(depId) { mutableSetOf() }.add(stepId)
            }
        }
        
        return reverse
    }
    
    /**
     * Выполняет топологическую сортировку и возвращает батчи для параллельного выполнения
     * 
     * Каждый батч содержит шаги, которые можно выполнять параллельно
     * (т.е. шаги без зависимостей или с уже выполненными зависимостями)
     * 
     * @return Список батчей, где каждый батч - список ID шагов для параллельного выполнения
     * @throws CircularDependencyException если обнаружена циклическая зависимость
     */
    fun topologicalSort(): List<List<String>> {
        logger.info("Starting topological sort for ${steps.size} steps")
        
        val result = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inDegree = calculateInDegree()
        
        var iteration = 0
        while (visited.size < graph.size) {
            iteration++
            
            // Находим все узлы с нулевой входящей степенью (готовые к выполнению)
            val batch = graph.keys.filter { key ->
                key !in visited && (inDegree[key] ?: 0) == 0
            }
            
            if (batch.isEmpty()) {
                // Если нет узлов с нулевой степенью, но еще есть непосещенные - циклическая зависимость
                val remaining = graph.keys.filter { it !in visited }
                logger.warn("Circular dependency detected. Remaining steps: $remaining")
                logger.warn("In-degrees: ${inDegree.filter { it.key in remaining }}")
                throw CircularDependencyException(
                    "Circular dependency detected among steps: $remaining"
                )
            }
            
            logger.info("Batch $iteration: ${batch.size} steps - $batch")
            result.add(batch)
            visited.addAll(batch)
            
            // Уменьшаем входящую степень для зависимых узлов
            batch.forEach { node ->
                reverseGraph[node]?.forEach { dependent ->
                    inDegree[dependent] = (inDegree[dependent] ?: 0) - 1
                }
            }
        }
        
        logger.info("Topological sort completed: ${result.size} batches")
        return result
    }
    
    /**
     * Вычисляет входящую степень для каждого узла
     * (количество зависимостей, которые должны быть выполнены перед этим шагом)
     */
    private fun calculateInDegree(): MutableMap<String, Int> {
        return graph.keys.associateWith { key ->
            graph[key]?.size ?: 0
        }.toMutableMap()
    }
    
    /**
     * Проверяет, есть ли циклические зависимости в графе
     */
    fun hasCycles(): Boolean {
        return try {
            topologicalSort()
            false
        } catch (e: CircularDependencyException) {
            true
        }
    }
    
    /**
     * Возвращает все шаги, от которых зависит данный шаг (прямые зависимости)
     */
    fun getDependencies(stepId: String): Set<String> {
        return graph[stepId] ?: emptySet()
    }
    
    /**
     * Возвращает все шаги, которые зависят от данного шага
     */
    fun getDependents(stepId: String): Set<String> {
        return reverseGraph[stepId] ?: emptySet()
    }
    
    /**
     * Возвращает все транзитивные зависимости (включая зависимости зависимостей)
     */
    fun getTransitiveDependencies(stepId: String): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque(getDependencies(stepId))
        
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current !in result) {
                result.add(current)
                queue.addAll(getDependencies(current))
            }
        }
        
        return result
    }
    
    /**
     * Проверяет, можно ли выполнить шаг (все его зависимости выполнены)
     */
    fun canExecute(stepId: String, completedSteps: Set<String>): Boolean {
        val dependencies = getDependencies(stepId)
        return dependencies.all { it in completedSteps }
    }
}

/**
 * Исключение для циклических зависимостей
 */
class CircularDependencyException(message: String) : Exception(message)
