package ru.marslab.ide.ride.orchestrator

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import ru.marslab.ide.ride.agent.tools.ProjectScannerAgentBridge
import ru.marslab.ide.ride.agent.tools.DeltaUpdate
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanStep
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.tool.StepInput
import java.util.concurrent.ConcurrentHashMap

/**
 * Интеграция ProjectScannerToolAgent с оркестратором для сложных задач
 *
 * Предоставляет следующие возможности:
 * - Автоматическое сканирование проектов при начале анализа
 * - Инкрементальные обновления при изменениях файлов
 * - Кэширование результатов сканирования между шагами оркестрации
 * - Подписка на дельты для long-running задач
 */
class ProjectScannerOrchestratorIntegration(
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val logger = Logger.getInstance(ProjectScannerOrchestratorIntegration::class.java)

    // Кэш результатов сканирования между шагами оркестрации
    private val scanCache = ConcurrentHashMap<String, CachedScanResult>()

    // Активные подписки на дельты для планов
    private val activeSubscriptions = ConcurrentHashMap<String, String>() // planId -> subscriptionId

    /**
     * Подготавливает сканирование проекта для плана оркестрации
     */
    suspend fun prepareProjectScan(
        plan: ExecutionPlan,
        projectPath: String,
        forceRescan: Boolean = false
    ): ScanPreparationResult {
        return try {
            logger.info("Preparing project scan for plan ${plan.id} on $projectPath")

            // Проверяем наличие кэша
            val cacheKey = "${plan.id}:$projectPath"
            val cached = scanCache[cacheKey]

            if (!forceRescan && cached != null && !cached.isExpired()) {
                logger.info("Using cached scan result for plan ${plan.id}")
                return ScanPreparationResult.Success(
                    cached.files,
                    cached.statistics,
                    cached.timestamp,
                    fromCache = true
                )
            }

            // Выполняем сканирование
            val scanResult = ProjectScannerAgentBridge.scanProject(
                projectPath = projectPath,
                forceRescan = true,
                pageSize = 1000
            )

            if (scanResult.success) {
                val files = scanResult.output.get<List<String>>("files") ?: emptyList()
                val stats = scanResult.output.get<Map<String, Any>>("stats") ?: emptyMap()
                val timestamp = System.currentTimeMillis()

                // Сохраняем в кэш
                scanCache[cacheKey] = CachedScanResult(
                    files = files,
                    statistics = stats,
                    timestamp = timestamp,
                    planId = plan.id,
                    projectPath = projectPath
                )

                logger.info("Project scan completed for plan ${plan.id}: ${files.size} files")

                ScanPreparationResult.Success(
                    files,
                    stats,
                    timestamp,
                    fromCache = false
                )
            } else {
                logger.error("Project scan failed for plan ${plan.id}: ${scanResult.error}")
                ScanPreparationResult.Error(scanResult.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            logger.error("Error preparing project scan for plan ${plan.id}", e)
            ScanPreparationResult.Error(e.message ?: "Exception occurred")
        }
    }

    /**
     * Создает подписку на дельты для long-running плана
     */
    fun createDeltaSubscriptionForPlan(
        plan: ExecutionPlan,
        projectPath: String,
        onFileChange: suspend (DeltaUpdate) -> Unit
    ): Boolean {
        return try {
            // Отменяем предыдущую подписку если есть
            activeSubscriptions[plan.id]?.let { oldSubscriptionId ->
                ProjectScannerAgentBridge.unsubscribeFromFileChanges(oldSubscriptionId)
            }

            val subscriptionId = ProjectScannerAgentBridge.subscribeToFileChanges(
                agentId = "orchestrator:${plan.id}",
                projectPath = projectPath
            ) { deltaUpdate ->
                // Запускаем обработку дельты в корутине
                coroutineScope.launch {
                    try {
                        onFileChange(deltaUpdate)
                        logger.info("Processed delta update for plan ${plan.id}: ${deltaUpdate.changedFiles.size} files changed")
                    } catch (e: Exception) {
                        logger.error("Error processing delta update for plan ${plan.id}", e)
                    }
                }
            }

            activeSubscriptions[plan.id] = subscriptionId
            logger.info("Created delta subscription for plan ${plan.id}: $subscriptionId")
            true
        } catch (e: Exception) {
            logger.error("Error creating delta subscription for plan ${plan.id}", e)
            false
        }
    }

    /**
     * Отменяет подписку на дельты для плана
     */
    fun cancelDeltaSubscriptionForPlan(plan: ExecutionPlan): Boolean {
        return try {
            val subscriptionId = activeSubscriptions.remove(plan.id)
            if (subscriptionId != null) {
                val cancelled = ProjectScannerAgentBridge.unsubscribeFromFileChanges(subscriptionId)
                if (cancelled) {
                    logger.info("Cancelled delta subscription for plan ${plan.id}")
                }
                cancelled
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Error cancelling delta subscription for plan ${plan.id}", e)
            false
        }
    }

    /**
     * Получает статистику проекта из кэша или выполняет быстрое сканирование
     */
    suspend fun getProjectStatistics(
        plan: ExecutionPlan,
        projectPath: String
    ): Map<String, Any> {
        val cacheKey = "${plan.id}:$projectPath"
        val cached = scanCache[cacheKey]

        return if (cached != null && !cached.isExpired()) {
            cached.statistics
        } else {
            val result = ProjectScannerAgentBridge.getProjectStatistics(projectPath)
            if (result.success) {
                result.output.get<Map<String, Any>>("stats") ?: emptyMap()
            } else {
                emptyMap()
            }
        }
    }

    /**
     * Фильтрует файлы по языкам программирования для анализа
     */
    fun filterFilesByLanguage(
        files: List<String>,
        languages: Set<String>
    ): List<String> {
        if (languages.isEmpty()) return files

        return files.filter { filePath ->
            val extension = filePath.substringAfterLast('.').lowercase()
            extension in languages
        }
    }

    /**
     * Создает ToolPlanStep для сканирования проекта
     */
    fun createScanStep(
        projectPath: String,
        forceRescan: Boolean = false,
        includePatterns: List<String> = emptyList(),
        excludePatterns: List<String> = emptyList()
    ): ToolPlanStep {
        return ToolPlanStep(
            description = "Scan project structure and files",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("force_rescan", forceRescan)
                .set("batch_size", 1000)
                .let { input ->
                    if (includePatterns.isNotEmpty()) input.set("include_patterns", includePatterns) else input
                }
                .let { input ->
                    if (excludePatterns.isNotEmpty()) input.set("exclude_patterns", excludePatterns) else input
                }
        )
    }

    /**
     * Создает ToolPlanStep для получения дельты изменений
     */
    fun createDeltaStep(
        projectPath: String,
        sinceTs: Long
    ): ToolPlanStep {
        return ToolPlanStep(
            description = "Get incremental changes since timestamp",
            agentType = AgentType.PROJECT_SCANNER,
            input = StepInput.empty()
                .set("project_path", projectPath)
                .set("since_ts", sinceTs)
                .set("batch_size", 2000)
        )
    }

    /**
     * Очищает кэш и подписки для плана
     */
    fun cleanupForPlan(plan: ExecutionPlan) {
        try {
            // Отменяем подписку
            cancelDeltaSubscriptionForPlan(plan)

            // Удаляем кэш
            scanCache.entries.removeAll { (key, _) ->
                key.startsWith("${plan.id}:")
            }

            logger.info("Cleaned up resources for plan ${plan.id}")
        } catch (e: Exception) {
            logger.error("Error cleaning up resources for plan ${plan.id}", e)
        }
    }

    /**
     * Получает текущее состояние кэша и подписок
     */
    fun getIntegrationStatus(): Map<String, Any> {
        return mapOf(
            "cached_scans" to scanCache.size,
            "active_subscriptions" to activeSubscriptions.size,
            "cache_entries" to scanCache.keys,
            "subscription_plans" to activeSubscriptions.keys
        )
    }
}

/**
 * Результат подготовки сканирования проекта
 */
sealed class ScanPreparationResult {
    data class Success(
        val files: List<String>,
        val statistics: Map<String, Any>,
        val timestamp: Long,
        val fromCache: Boolean
    ) : ScanPreparationResult()

    data class Error(val message: String) : ScanPreparationResult()
}

/**
 * Кэшированный результат сканирования
 */
private data class CachedScanResult(
    val files: List<String>,
    val statistics: Map<String, Any>,
    val timestamp: Long,
    val planId: String,
    val projectPath: String
) {
    fun isExpired(): Boolean {
        val ttl = 10 * 60 * 1000L // 10 минут
        return System.currentTimeMillis() - timestamp > ttl
    }
}