package ru.marslab.ide.ride.model.orchestrator

/**
 * Шаг с циклическим выполнением
 */
data class LoopStep(
    /**
     * Базовый шаг для выполнения
     */
    val step: PlanStep,
    
    /**
     * Конфигурация цикла
     */
    val loopConfig: LoopConfig
)

/**
 * Конфигурация цикла
 */
data class LoopConfig(
    /**
     * Тип цикла
     */
    val type: LoopType,
    
    /**
     * Максимальное количество итераций
     */
    val maxIterations: Int = 10,
    
    /**
     * Условие продолжения цикла
     */
    val continueCondition: ((ExecutionContext, Any?) -> Boolean)? = null,
    
    /**
     * Условие выхода из цикла
     */
    val breakCondition: ((ExecutionContext, Any?) -> Boolean)? = null,
    
    /**
     * Коллекция для итерации (для FOR_EACH)
     */
    val collection: List<Any>? = null,
    
    /**
     * Переменная итератора
     */
    val iteratorVariable: String? = null
) {
    companion object {
        /**
         * Цикл while с условием
         */
        fun whileLoop(
            maxIterations: Int = 10,
            condition: (ExecutionContext, Any?) -> Boolean
        ) = LoopConfig(
            type = LoopType.WHILE,
            maxIterations = maxIterations,
            continueCondition = condition
        )
        
        /**
         * Цикл for-each по коллекции
         */
        fun forEach(
            collection: List<Any>,
            iteratorVariable: String = "item"
        ) = LoopConfig(
            type = LoopType.FOR_EACH,
            maxIterations = collection.size,
            collection = collection,
            iteratorVariable = iteratorVariable
        )
        
        /**
         * Цикл с фиксированным количеством итераций
         */
        fun repeat(count: Int) = LoopConfig(
            type = LoopType.REPEAT,
            maxIterations = count
        )
        
        /**
         * Цикл до успешного выполнения
         */
        fun untilSuccess(maxAttempts: Int = 5) = LoopConfig(
            type = LoopType.UNTIL_SUCCESS,
            maxIterations = maxAttempts,
            breakCondition = { _, result -> 
                result != null && result.toString().contains("success", ignoreCase = true)
            }
        )
    }
}

/**
 * Тип цикла
 */
enum class LoopType {
    /**
     * Цикл while (выполняется пока условие истинно)
     */
    WHILE,
    
    /**
     * Цикл for-each (итерация по коллекции)
     */
    FOR_EACH,
    
    /**
     * Цикл repeat (фиксированное количество итераций)
     */
    REPEAT,
    
    /**
     * Цикл до успешного выполнения
     */
    UNTIL_SUCCESS
}

/**
 * Результат выполнения цикла
 */
data class LoopResult(
    /**
     * Количество выполненных итераций
     */
    val iterations: Int,
    
    /**
     * Результаты каждой итерации
     */
    val iterationResults: List<Any?>,
    
    /**
     * Причина завершения цикла
     */
    val terminationReason: LoopTerminationReason,
    
    /**
     * Успешно ли завершился цикл
     */
    val success: Boolean
)

/**
 * Причина завершения цикла
 */
enum class LoopTerminationReason {
    /**
     * Достигнуто максимальное количество итераций
     */
    MAX_ITERATIONS_REACHED,
    
    /**
     * Условие продолжения стало ложным
     */
    CONDITION_FALSE,
    
    /**
     * Условие выхода стало истинным
     */
    BREAK_CONDITION_MET,
    
    /**
     * Коллекция полностью обработана
     */
    COLLECTION_EXHAUSTED,
    
    /**
     * Достигнут успешный результат
     */
    SUCCESS_ACHIEVED,
    
    /**
     * Ошибка выполнения
     */
    ERROR
}
