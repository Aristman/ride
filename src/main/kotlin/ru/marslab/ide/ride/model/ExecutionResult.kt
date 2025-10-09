package ru.marslab.ide.ride.model

/**
 * Результат выполнения задачи ExecutorAgent
 *
 * @property taskId Идентификатор выполненной задачи
 * @property success Флаг успешности выполнения
 * @property content Содержимое результата
 * @property error Сообщение об ошибке (если success = false)
 */
data class ExecutionResult(
    val taskId: Int,
    val success: Boolean,
    val content: String,
    val error: String? = null
) {
    companion object {
        /**
         * Создает успешный результат
         */
        fun success(taskId: Int, content: String): ExecutionResult {
            return ExecutionResult(
                taskId = taskId,
                success = true,
                content = content
            )
        }
        
        /**
         * Создает результат с ошибкой
         */
        fun error(taskId: Int, error: String): ExecutionResult {
            return ExecutionResult(
                taskId = taskId,
                success = false,
                content = "",
                error = error
            )
        }
    }
}
