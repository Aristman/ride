package ru.marslab.ide.ride.agent.cache

import ru.marslab.ide.ride.agent.analyzer.ComplexityLevel
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.model.chat.ChatContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Кэш для результатов анализа неопределенности запросов
 *
 * Особенности реализации:
 * - Thread-safe с использованием ReentrantReadWriteLock
 * - LRU eviction политика для контроля памяти
 * - TTL (time-to-live) для автоматического устаревания записей
 * - Статистика использования для мониторинга эффективности
 */
class UncertaintyAnalysisCache(
    private val maxSize: Int = 1000,
    private val ttlMs: Long = 30 * 60 * 1000 // 30 минут
) {
    private val logger = com.intellij.openapi.diagnostic.Logger.getInstance(UncertaintyAnalysisCache::class.java)

    private data class CacheEntry(
        val result: UncertaintyResult,
        val timestamp: Long,
        var accessCount: Long = 0,
        var lastAccessTime: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val lock = ReentrantReadWriteLock()

    // Статистика
    private var hits = 0L
    private var misses = 0L
    private var evictions = 0L

    /**
     * Генерирует ключ кэша на основе запроса и контекста
     */
    private fun generateKey(request: String, context: ChatContext): String {
        val contextHash = context.hashCode()
        val requestHash = request.hashCode()
        return "${requestHash}_${contextHash}"
    }

    /**
     * Получает результат из кэша
     */
    fun get(request: String, context: ChatContext): UncertaintyResult? {
        val key = generateKey(request, context)

        return lock.read {
            val entry = cache[key]
            if (entry == null) {
                misses++
                return@read null
            }

            val now = System.currentTimeMillis()

            // Проверяем TTL
            if (now - entry.timestamp > ttlMs) {
                // Запись устарела, удаляем ее
                lock.write {
                    cache.remove(key)
                }
                misses++
                return@read null
            }

            // Обновляем статистику доступа
            entry.accessCount++
            entry.lastAccessTime = now
            hits++

            logger.debug("Cache hit for request: ${request.take(50)}...")
            entry.result
        }
    }

    /**
     * Сохраняет результат в кэш
     */
    fun put(request: String, context: ChatContext, result: UncertaintyResult) {
        val key = generateKey(request, context)
        val now = System.currentTimeMillis()

        lock.write {
            // Проверяем размер кэша и выполняем evict если нужно
            if (cache.size >= maxSize && !cache.containsKey(key)) {
                evictLeastRecentlyUsed()
            }

            val entry = CacheEntry(
                result = result,
                timestamp = now,
                accessCount = 1,
                lastAccessTime = now
            )

            cache[key] = entry
            logger.debug("Cached result for request: ${request.take(50)}..., complexity: ${result.complexity}")
        }
    }

    /**
     * Удаляет least recently used записи
     */
    private fun evictLeastRecentlyUsed() {
        val lruKey = cache.minByOrNull { it.value.lastAccessTime }?.key
        if (lruKey != null) {
            cache.remove(lruKey)
            evictions++
            logger.debug("Evicted LRU cache entry: $lruKey")
        }
    }

    /**
     * Очищает кэш
     */
    fun clear() {
        lock.write {
            val size = cache.size
            cache.clear()
            logger.info("Cleared cache, removed $size entries")
        }
    }

    /**
     * Удаляет устаревшие записи
     */
    fun cleanup() {
        val now = System.currentTimeMillis()

        lock.write {
            val expiredKeys = cache.filter { (_, entry) ->
                now - entry.timestamp > ttlMs
            }.keys

            expiredKeys.forEach { key ->
                cache.remove(key)
            }

            if (expiredKeys.isNotEmpty()) {
                logger.debug("Cleaned up ${expiredKeys.size} expired cache entries")
            }
        }
    }

    /**
     * Возвращает статистику кэша
     */
    fun getStats(): CacheStats {
        return lock.read {
            CacheStats(
                size = cache.size,
                maxSize = maxSize,
                hits = hits,
                misses = misses,
                evictions = evictions,
                hitRate = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0,
                ttlMs = ttlMs
            )
        }
    }

    /**
     * Возвращает детальную статистику по сложности запросов
     */
    fun getComplexityStats(): Map<ComplexityLevel, Int> {
        return lock.read {
            cache.values.groupingBy { it.result.complexity }.eachCount()
        }
    }

    /**
     * Предварительно прогревает кэш для популярных запросов
     */
    fun warmup(popularRequests: List<Pair<String, ChatContext>>, analyzer: (String, ChatContext) -> UncertaintyResult) {
        logger.info("Warming up cache with ${popularRequests.size} popular requests")

        popularRequests.forEach { (request, context) ->
            if (get(request, context) == null) {
                val result = analyzer(request, context)
                put(request, context, result)
            }
        }

        logger.info("Cache warmup completed")
    }
}

/**
 * Статистика кэша
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val hitRate: Double,
    val ttlMs: Long
) {
    val efficiency: Double get() = if (hits > 0) (hits.toDouble() / (hits + evictions)) else 0.0
    val utilization: Double get() = size.toDouble() / maxSize
}