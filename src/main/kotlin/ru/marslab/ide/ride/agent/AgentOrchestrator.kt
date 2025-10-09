package ru.marslab.ide.ride.agent

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.agent.impl.ExecutorAgent
import ru.marslab.ide.ride.agent.impl.PlannerAgent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.*

/**
 * Оркестратор для координации работы PlannerAgent и ExecutorAgent
 * 
 * Управляет процессом:
 * 1. PlannerAgent создает план задач
 * 2. ExecutorAgent выполняет каждую задачу по очереди
 * 3. Результаты каждого шага отправляются через callback
 *
 * @property llmProvider Провайдер для взаимодействия с LLM
 */
class AgentOrchestrator(
    private val llmProvider: LLMProvider
) {
    private val logger = Logger.getInstance(AgentOrchestrator::class.java)
    private val plannerAgent = PlannerAgent(llmProvider)
    private val executorAgent = ExecutorAgent(llmProvider)

    /**
     * Обрабатывает запрос пользователя через систему двух агентов
     * 
     * @param request Запрос к агенту
     * @param onStepComplete Callback для каждого шага выполнения
     * @return Итоговый результат выполнения
     */
    suspend fun process(
        request: AgentRequest,
        onStepComplete: suspend (OrchestratorStep) -> Unit
    ): AgentResponse {
        logger.info("Starting orchestration for request")
        
        try {
            // Шаг 1: PlannerAgent создает план
            logger.info("Step 1: Creating task plan with PlannerAgent")
            val planResponse = plannerAgent.ask(request)
            
            // Отправляем результат планирования в чат
            onStepComplete(
                OrchestratorStep.PlanningComplete(
                    agentName = "PlannerAgent",
                    content = planResponse.content,
                    success = planResponse.success,
                    error = planResponse.error
                )
            )
            
            // Если планирование не удалось, возвращаем ошибку
            if (!planResponse.success) {
                logger.warn("Planning failed: ${planResponse.error}")
                return AgentResponse.error(
                    error = "Не удалось создать план задач: ${planResponse.error}",
                    content = planResponse.content
                )
            }
            
            // Извлекаем план из ответа
            val parsedPlan = planResponse.parsedContent as? TaskPlanData
            if (parsedPlan == null) {
                logger.error("Failed to extract task plan from response")
                return AgentResponse.error(
                    error = "Не удалось извлечь план задач из ответа",
                    content = "Внутренняя ошибка при обработке плана"
                )
            }
            
            val plan = parsedPlan.plan
            logger.info("Plan created with ${plan.size()} tasks")
            
            // Шаг 2: ExecutorAgent выполняет каждую задачу
            val executionResults = mutableListOf<ExecutionResult>()
            
            for (task in plan.tasks) {
                logger.info("Executing task ${task.id}: ${task.title}")
                
                // Создаем запрос для ExecutorAgent с промптом задачи
                val executorRequest = AgentRequest(
                    request = task.prompt,
                    context = request.context,
                    parameters = request.parameters
                )
                
                // Выполняем задачу
                val executorResponse = executorAgent.ask(executorRequest)
                
                // Создаем результат выполнения
                val result = if (executorResponse.success) {
                    ExecutionResult.success(task.id, executorResponse.content)
                } else {
                    ExecutionResult.error(task.id, executorResponse.error ?: "Неизвестная ошибка")
                }
                
                executionResults.add(result)
                
                // Отправляем результат выполнения задачи в чат
                onStepComplete(
                    OrchestratorStep.TaskComplete(
                        agentName = "ExecutorAgent",
                        taskId = task.id,
                        taskTitle = task.title,
                        content = executorResponse.content,
                        success = executorResponse.success,
                        error = executorResponse.error
                    )
                )
                
                // Если задача не выполнена, показываем ошибку но продолжаем
                if (!executorResponse.success) {
                    logger.warn("Task ${task.id} failed: ${executorResponse.error}")
                }
            }
            
            // Формируем итоговый результат
            val successfulTasks = executionResults.count { it.success }
            val totalTasks = executionResults.size
            
            val summaryContent = buildString {
                appendLine("✅ **Выполнение завершено**")
                appendLine()
                appendLine("**Статистика:**")
                appendLine("- Всего задач: $totalTasks")
                appendLine("- Успешно выполнено: $successfulTasks")
                if (successfulTasks < totalTasks) {
                    appendLine("- Ошибок: ${totalTasks - successfulTasks}")
                }
            }
            
            // Отправляем итоговую сводку
            onStepComplete(
                OrchestratorStep.AllComplete(
                    content = summaryContent,
                    totalTasks = totalTasks,
                    successfulTasks = successfulTasks
                )
            )
            
            return AgentResponse.success(
                content = summaryContent,
                metadata = mapOf(
                    "totalTasks" to totalTasks,
                    "successfulTasks" to successfulTasks,
                    "failedTasks" to (totalTasks - successfulTasks)
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error during orchestration", e)
            val errorStep = OrchestratorStep.Error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при выполнении задач"
            )
            onStepComplete(errorStep)
            
            return AgentResponse.error(
                error = e.message ?: "Неизвестная ошибка",
                content = "Произошла ошибка при координации агентов"
            )
        }
    }
    
    /**
     * Освобождает ресурсы
     */
    fun dispose() {
        logger.info("Disposing AgentOrchestrator")
        plannerAgent.dispose()
        executorAgent.dispose()
    }
}

/**
 * Шаг выполнения оркестратора
 */
sealed class OrchestratorStep {
    /**
     * Планирование завершено
     */
    data class PlanningComplete(
        val agentName: String,
        val content: String,
        val success: Boolean,
        val error: String?
    ) : OrchestratorStep()
    
    /**
     * Задача выполнена
     */
    data class TaskComplete(
        val agentName: String,
        val taskId: Int,
        val taskTitle: String,
        val content: String,
        val success: Boolean,
        val error: String?
    ) : OrchestratorStep()
    
    /**
     * Все задачи выполнены
     */
    data class AllComplete(
        val content: String,
        val totalTasks: Int,
        val successfulTasks: Int
    ) : OrchestratorStep()
    
    /**
     * Ошибка выполнения
     */
    data class Error(
        val error: String,
        val content: String
    ) : OrchestratorStep()
}
