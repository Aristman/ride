package ru.marslab.ide.ride.orchestrator.impl

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.model.orchestrator.ExecutionPlan
import ru.marslab.ide.ride.model.orchestrator.PlanState
import ru.marslab.ide.ride.orchestrator.PlanStorage
import ru.marslab.ide.ride.orchestrator.StorageStats
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Постоянное хранилище планов с сохранением на диск
 *
 * Использует JSON формат для сериализации планов и обеспечивает персистентность
 * между перезапусками приложения. Поддерживает версионирование и очистку старых данных.
 */
class PersistentPlanStorage(
    private val storageDirectory: String,
    private val maxPlansPerFile: Int = 1000
) : PlanStorage {

    private val logger = Logger.getInstance(PersistentPlanStorage::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val memoryCache = ConcurrentHashMap<String, ExecutionPlan>()
    private val mutex = Mutex()
    private val storagePath: Path = Paths.get(storageDirectory).toAbsolutePath()

    init {
        initializeStorage()
    }

    private fun initializeStorage() {
        try {
            Files.createDirectories(storagePath)
            logger.info("Initialized plan storage at: $storagePath")
            loadExistingPlans()
        } catch (e: Exception) {
            logger.error("Failed to initialize storage directory", e)
            throw RuntimeException("Cannot initialize plan storage", e)
        }
    }

    private fun loadExistingPlans() {
        try {
            val storageFiles = getStorageFiles()
            var totalLoaded = 0

            storageFiles.forEach { file ->
                try {
                    val plans = loadFromFile(file)
                    plans.forEach { plan ->
                        memoryCache[plan.id] = plan
                    }
                    totalLoaded += plans.size
                    logger.debug("Loaded ${plans.size} plans from ${file.name}")
                } catch (e: Exception) {
                    logger.warn("Failed to load plans from ${file.name}", e)
                }
            }

            logger.info("Loaded $totalLoaded existing plans from storage")
        } catch (e: Exception) {
            logger.error("Error loading existing plans", e)
        }
    }

    override suspend fun save(plan: ExecutionPlan): String {
        return mutex.withLock {
            val planToSave = plan.copy(
                id = plan.id.ifBlank { generateId() }
            )

            memoryCache[planToSave.id] = planToSave
            saveToFile(planToSave)
            logger.info("Saved plan ${planToSave.id} to persistent storage")
            planToSave.id
        }
    }

    override suspend fun load(planId: String): ExecutionPlan? {
        return mutex.withLock {
            memoryCache[planId]?.also {
                logger.debug("Loaded plan $planId from cache")
            }
        }
    }

    override suspend fun update(plan: ExecutionPlan) {
        mutex.withLock {
            val existing = memoryCache[plan.id]
            val updatedPlan = if (existing != null) {
                plan.copy(version = existing.version + 1)
            } else {
                plan.copy(version = 1)
            }

            memoryCache[plan.id] = updatedPlan
            saveToFile(updatedPlan)
            logger.debug("Updated plan ${plan.id} to version ${updatedPlan.version}")
        }
    }

    override suspend fun delete(planId: String) {
        mutex.withLock {
            memoryCache.remove(planId)?.also {
                try {
                    deletePlanFromFile(planId)
                    logger.info("Deleted plan $planId from storage")
                } catch (e: Exception) {
                    logger.warn("Failed to delete plan file for $planId", e)
                }
            }
        }
    }

    override suspend fun exists(planId: String): Boolean {
        return mutex.withLock {
            memoryCache.containsKey(planId)
        }
    }

    override suspend fun listActive(): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { plan ->
                plan.currentState != PlanState.COMPLETED &&
                plan.currentState != PlanState.CANCELLED &&
                plan.currentState != PlanState.FAILED
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun listByState(state: PlanState): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { it.currentState == state }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun listCompleted(limit: Int, offset: Int): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { it.currentState == PlanState.COMPLETED }
                .sortedByDescending { it.completedAt ?: it.createdAt }
                .drop(offset)
                .take(limit)
        }
    }

    override suspend fun listFailed(limit: Int): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { it.currentState == PlanState.FAILED }
                .sortedByDescending { it.createdAt }
                .take(limit)
        }
    }

    override suspend fun findByUserRequest(userRequestId: String): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { it.userRequestId == userRequestId }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun searchByContent(searchText: String): List<ExecutionPlan> {
        return mutex.withLock {
            val searchTextLower = searchText.lowercase()
            memoryCache.values.filter { plan ->
                plan.originalRequest.lowercase().contains(searchTextLower) ||
                plan.analysis.reasoning.lowercase().contains(searchTextLower)
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun findByTimeRange(
        fromTime: kotlinx.datetime.Instant,
        toTime: kotlinx.datetime.Instant
    ): List<ExecutionPlan> {
        return mutex.withLock {
            memoryCache.values.filter { plan ->
                plan.createdAt >= fromTime && plan.createdAt <= toTime
            }.sortedByDescending { it.createdAt }
        }
    }

    override suspend fun cleanup(
        olderThan: kotlinx.datetime.Instant,
        states: Set<PlanState>
    ): Int {
        return mutex.withLock {
            val plansToDelete = memoryCache.values.filter { plan ->
                plan.currentState in states && plan.createdAt < olderThan
            }

            var deletedCount = 0
            plansToDelete.forEach { plan ->
                try {
                    memoryCache.remove(plan.id)
                    deletePlanFromFile(plan.id)
                    deletedCount++
                } catch (e: Exception) {
                    logger.warn("Failed to delete plan ${plan.id} during cleanup", e)
                }
            }

            // Оптимизируем файлы хранилища после очистки
            optimizeStorageFiles()

            logger.info("Cleaned up $deletedCount old plans")
            deletedCount
        }
    }

    override suspend fun getStorageStats(): StorageStats {
        return mutex.withLock {
            val totalPlans = memoryCache.size
            val activePlans = memoryCache.values.count { plan ->
                plan.currentState != PlanState.COMPLETED &&
                plan.currentState != PlanState.CANCELLED &&
                plan.currentState != PlanState.FAILED
            }
            val completedPlans = memoryCache.values.count { it.currentState == PlanState.COMPLETED }
            val failedPlans = memoryCache.values.count { it.currentState == PlanState.FAILED }
            val cancelledPlans = memoryCache.values.count { it.currentState == PlanState.CANCELLED }

            val timestamps = memoryCache.values.map { it.createdAt }
            val oldestPlan = timestamps.minOrNull()
            val newestPlan = timestamps.maxOrNull()

            // Реальный размер файлов хранилища
            val storageSizeBytes = getStorageFiles().sumOf { it.length() }

            StorageStats(
                totalPlans = totalPlans,
                activePlans = activePlans,
                completedPlans = completedPlans,
                failedPlans = failedPlans,
                cancelledPlans = cancelledPlans,
                storageSizeBytes = storageSizeBytes,
                oldestPlan = oldestPlan,
                newestPlan = newestPlan
            )
        }
    }

    override suspend fun backup(backupPath: String): Boolean {
        return mutex.withLock {
            try {
                val backupDir = Paths.get(backupPath)
                Files.createDirectories(backupDir)

                getStorageFiles().forEach { file ->
                    val backupFile = backupDir.resolve(file.name)
                    Files.copy(file.toPath(), backupFile, StandardCopyOption.REPLACE_EXISTING)
                }

                // Также создаем полную резервную копию в JSON
                val fullBackupFile = backupDir.resolve("full_backup_${Clock.System.now().epochSeconds}.json")
                val allPlans = memoryCache.values.toList()
                Files.writeString(fullBackupFile, json.encodeToString(allPlans))

                logger.info("Created backup at: $backupPath")
                true
            } catch (e: Exception) {
                logger.error("Failed to create backup", e)
                false
            }
        }
    }

    override suspend fun restore(backupPath: String): Boolean {
        return mutex.withLock {
            try {
                val backupDir = Paths.get(backupPath)
                if (!Files.exists(backupDir) || !Files.isDirectory(backupDir)) {
                    logger.error("Backup directory does not exist: $backupPath")
                    return false
                }

                // Очищаем текущее хранилище
                memoryCache.clear()
                getStorageFiles().forEach { Files.deleteIfExists(it.toPath()) }

                // Восстанавливаем из файлов
                loadExistingPlans()

                logger.info("Restored storage from backup: $backupPath")
                true
            } catch (e: Exception) {
                logger.error("Failed to restore from backup", e)
                false
            }
        }
    }

    // Приватные методы для работы с файлами

    private fun getStorageFiles(): List<File> {
        val storageDir = storagePath.toFile()
        return if (storageDir.exists()) {
            storageDir.listFiles { file ->
                file.isFile && file.name.startsWith("plans_") && file.name.endsWith(".json")
            }?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun loadFromFile(file: File): List<ExecutionPlan> {
        val content = Files.readString(file.toPath())
        return json.decodeFromString<List<ExecutionPlan>>(content)
    }

    private fun saveToFile(plan: ExecutionPlan) {
        val currentFile = getCurrentStorageFile()

        // Добавляем план к существующим в файле
        val existingPlans = if (currentFile.exists()) {
            try {
                loadFromFile(currentFile).toMutableList()
            } catch (e: Exception) {
                logger.warn("Failed to load existing plans from ${currentFile.name}, creating new file", e)
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        // Обновляем или добавляем план
        val existingIndex = existingPlans.indexOfFirst { it.id == plan.id }
        if (existingIndex >= 0) {
            existingPlans[existingIndex] = plan
        } else {
            existingPlans.add(plan)
        }

        // Если файл слишком большой, создаем новый
        if (existingPlans.size >= maxPlansPerFile) {
            val newFile = createNewStorageFile()
            Files.writeString(newFile.toPath(), json.encodeToString(listOf(plan)))
            logger.info("Created new storage file: ${newFile.name}")
        } else {
            Files.writeString(currentFile.toPath(), json.encodeToString(existingPlans))
        }
    }

    private fun deletePlanFromFile(planId: String) {
        getStorageFiles().forEach { file ->
            try {
                val plans = loadFromFile(file).toMutableList()
                val removed = plans.removeAll { it.id == planId }

                if (removed) {
                    if (plans.isEmpty()) {
                        Files.deleteIfExists(file.toPath())
                    } else {
                        Files.writeString(file.toPath(), json.encodeToString(plans))
                    }
                    return
                }
            } catch (e: Exception) {
                logger.warn("Failed to delete plan $planId from ${file.name}", e)
            }
        }
    }

    private fun getCurrentStorageFile(): File {
        val files = getStorageFiles()
        return if (files.isNotEmpty()) {
            files.first() // Самый свежий файл
        } else {
            createNewStorageFile()
        }
    }

    private fun createNewStorageFile(): File {
        val timestamp = Clock.System.now().epochSeconds
        val fileName = "plans_$timestamp.json"
        return storagePath.resolve(fileName).toFile()
    }

    private fun optimizeStorageFiles() {
        try {
            // Если у нас много файлов с малым количеством планов, объединяем их
            val files = getStorageFiles()
            if (files.size > 5) {
                val allPlans = files.flatMap { loadFromFile(it) }
                files.forEach { Files.deleteIfExists(it.toPath()) }

                val newFile = createNewStorageFile()
                Files.writeString(newFile.toPath(), json.encodeToString(allPlans))
                logger.info("Optimized storage: combined ${files.size} files into one")
            }
        } catch (e: Exception) {
            logger.warn("Failed to optimize storage files", e)
        }
    }

    private fun generateId(): String {
        return "plan_${Clock.System.now().epochSeconds}_${(0..9999).random()}"
    }
}