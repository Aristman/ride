package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import ru.marslab.ide.ride.agent.ToolAgentRegistry
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.util.*

/**
 * Мост для доступа к ProjectScannerToolAgent из других агентов
 *
 * Предоставляет удобный API для:
 * - Создания подписок на дельты изменений
 * - Запроса сканирования проектов
 * - Получения статистики и метаданных
 */
object ProjectScannerAgentBridge {

    private val logger = Logger.getInstance(ProjectScannerAgentBridge::class.java)

    // Fallback сканер на случай, если ToolAgentRegistry не инициализирован в данном контексте (например, из Settings UI)
    private val fallbackScannerAgent: ProjectScannerToolAgent by lazy { ProjectScannerToolAgent() }

    /**
     * Создает подписку на дельты изменений файлов для указанного проекта
     *
     * @param agentId Идентификатор агента-подписчика
     * @param projectPath Путь к проекту для отслеживания
     * @param callback Callback для обработки дельт
     * @return ID подписки для отмены
     */
    fun subscribeToFileChanges(
        agentId: String,
        projectPath: String,
        callback: (DeltaUpdate) -> Unit
    ): String {
        val scannerAgent = getScannerAgent() ?: error("ProjectScannerToolAgent not available")
        return scannerAgent.createDeltaSubscription(agentId, projectPath, callback)
    }

    /**
     * Отменяет подписку на дельты изменений
     *
     * @param subscriptionId ID подписки, полученный при создании
     * @return true если подписка успешно отменена
     */
    fun unsubscribeFromFileChanges(subscriptionId: String): Boolean {
        val scannerAgent = getScannerAgent() ?: return false
        return scannerAgent.cancelDeltaSubscription(subscriptionId)
    }

    /**
     * Запускает сканирование проекта с указанными параметрами
     *
     * @param projectPath Путь к проекту
     * @param forceRescan Принудительное пересканирование (игнорировать кэш)
     * @param pageSize Размер страницы для пагинации
     * @param includePatterns Дополнительные include паттерны
     * @param excludePatterns Дополнительные exclude паттерны
     * @return StepResult с данными сканирования
     */
    suspend fun scanProject(
        projectPath: String,
        forceRescan: Boolean = false,
        pageSize: Int = 500,
        includePatterns: List<String> = emptyList(),
        excludePatterns: List<String> = emptyList()
    ): StepResult {
        val scannerAgent = getScannerAgent() ?: error("ProjectScannerToolAgent not available")

        val step = ToolPlanStep(
            description = "Scan project structure and files",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("force_rescan", forceRescan)
                .set("batch_size", pageSize)
                .let { input ->
                    if (includePatterns.isNotEmpty()) input.set("include_patterns", includePatterns) else input
                }
                .let { input ->
                    if (excludePatterns.isNotEmpty()) input.set("exclude_patterns", excludePatterns) else input
                }
        )

        return scannerAgent.executeStep(step, ru.marslab.ide.ride.model.orchestrator.ExecutionContext(projectPath))
    }

    /**
     * Получает инкрементальные изменения с указанной метки времени
     *
     * @param projectPath Путь к проекту
     * @param sinceTs Метка времени для получения изменений (UNIX ms)
     * @return StepResult с дельтой изменений
     */
    suspend fun getDeltaChanges(
        projectPath: String,
        sinceTs: Long
    ): StepResult {
        val scannerAgent = getScannerAgent() ?: error("ProjectScannerToolAgent not available")

        val step = ToolPlanStep(
            description = "Get incremental changes since timestamp",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("since_ts", sinceTs)
                .set("batch_size", 1000) // Большой размер для дельт
        )

        return scannerAgent.executeStep(step, ru.marslab.ide.ride.model.orchestrator.ExecutionContext(projectPath))
    }

    /**
     * Получает статистику проекта без полного сканирования (из кэша если доступно)
     *
     * @param projectPath Путь к проекту
     * @return StepResult со статистикой или ошибка если кэш недоступен
     */
    suspend fun getProjectStatistics(projectPath: String): StepResult {
        val scannerAgent = getScannerAgent() ?: error("ProjectScannerToolAgent not available")

        val step = ToolPlanStep(
            description = "Get project statistics from cache",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("force_rescan", false) // Не сканировать, только из кэша
                .set("batch_size", 1) // Минимальный размер для статистики
        )

        return scannerAgent.executeStep(step, ru.marslab.ide.ride.model.orchestrator.ExecutionContext(projectPath))
    }

    /**
     * Проверяет доступность ProjectScannerToolAgent
     */
    fun isAvailable(): Boolean {
        return getScannerAgent() != null
    }

    /**
     * Получает экземпляр ProjectScannerToolAgent из реестра
     */
    private fun getScannerAgent(): ProjectScannerToolAgent? {
        return try {
            val registry = ToolAgentRegistry()
            val agentType = AgentType.valueOf("PROJECT_SCANNER")
            val fromRegistry = registry.get(agentType) as? ProjectScannerToolAgent
            fromRegistry ?: fallbackScannerAgent
        } catch (e: Exception) {
            // Если не удалось получить из реестра — используем fallback
            fallbackScannerAgent
        }
    }
}

/**
 * Удобные extension функции для работы с ProjectScannerToolAgent
 */

/**
 * Создает подписку на изменения файлов с автоматической отменой при dispose
 */
fun ProjectScannerAgentBridge.autoSubscribe(
    agentId: String,
    projectPath: String,
    project: Project,
    callback: (DeltaUpdate) -> Unit
): String {
    val subscriptionId = subscribeToFileChanges(agentId, projectPath, callback)

    // Автоматически отписываемся при закрытии проекта
    project.getMessageBus().connect().subscribe(
        com.intellij.openapi.project.ProjectCloseListener.TOPIC,
        object : com.intellij.openapi.project.ProjectCloseListener {
            override fun projectClosed(closedProject: Project) {
                if (closedProject == project) {
                    unsubscribeFromFileChanges(subscriptionId)
                }
            }
        }
    )

    return subscriptionId
}

/**
 * Запускает полное сканирование с результатом в удобном формате
 */
suspend fun ProjectScannerAgentBridge.fullScan(
    projectPath: String,
    callback: suspend (files: List<String>, stats: Map<String, Any>) -> Unit
) {
    val result = scanProject(projectPath, forceRescan = true)
    if (result.success) {
        val files = result.output.get<List<String>>("files") ?: emptyList()
        val stats = result.output.get<Map<String, Any>>("stats") ?: emptyMap()
        callback(files, stats)
    } else {
        // Используем статический метод логгера для доступа из extension функции
        Logger.getInstance(ProjectScannerAgentBridge::class.java)
            .error("Full scan failed: ${result.error}")
    }
}