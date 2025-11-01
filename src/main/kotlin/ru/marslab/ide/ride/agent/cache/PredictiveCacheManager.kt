package ru.marslab.ide.ride.agent.cache

import ru.marslab.ide.ride.agent.analyzer.ComplexityLevel
import ru.marslab.ide.ride.agent.analyzer.RequestComplexityAnalyzer
import ru.marslab.ide.ride.agent.analyzer.UncertaintyResult
import ru.marslab.ide.ride.model.chat.ChatContext
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Менеджер предиктивного кэширования на основе паттернов использования
 *
 * Анализирует паттерны запросов и предварительно загружает в кэш
 * результаты для вероятных следующих запросов.
 */
class PredictiveCacheManager(
    private val uncertaintyCache: UncertaintyAnalysisCache,
    private val complexityAnalyzer: RequestComplexityAnalyzer
) {
    private val logger = Logger.getInstance(PredictiveCacheManager::class.java)

    // Паттерны запросов и их статистика
    private val requestPatterns = ConcurrentHashMap<String, RequestPattern>()
    private val sequencePatterns = ConcurrentHashMap<String, SequencePattern>()

    // Планировщик для фоновой работы
    private val scheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(2)

    // Статистика
    private val predictiveHits = AtomicLong(0)
    private val predictiveMisses = AtomicLong(0)

    init {
        // Запускаем фоновые задачи
        startPatternAnalysis()
        startCacheWarmup()
    }

    /**
     * Регистрирует запрос для анализа паттернов
     */
    fun registerRequest(request: String, context: ChatContext, result: UncertaintyResult) {
        val normalizedRequest = normalizeRequest(request)
        val patternKey = generatePatternKey(normalizedRequest, result.complexity)

        // Обновляем паттерн запроса
        requestPatterns.compute(patternKey) { _, existing ->
            existing?.copy(
                count = existing.count + 1,
                lastUsed = System.currentTimeMillis(),
                avgComplexity = (existing.avgComplexity * existing.count + result.score) / (existing.count + 1)
            ) ?: RequestPattern(
                normalizedRequest = normalizedRequest,
                complexity = result.complexity,
                count = 1,
                firstUsed = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis(),
                avgComplexity = result.score
            )
        }

        // Анализируем последовательности (если есть история)
        analyzeSequence(normalizedRequest, context)
    }

    /**
     * Предсказывает и загружает вероятные следующие запросы
     */
    fun predictAndCache(request: String, context: ChatContext) {
        val normalizedRequest = normalizeRequest(request)
        val predictions = predictNextRequests(normalizedRequest)

        logger.debug("Generated ${predictions.size} predictions for request: ${request.take(30)}...")

        predictions.forEach { (predictedRequest, probability) ->
            if (probability > 0.3 && uncertaintyCache.get(predictedRequest, context) == null) {
                // Асинхронно вычисляем и кэшируем результат
                scheduler.submit {
                    try {
                        val result = complexityAnalyzer.analyzeUncertainty(predictedRequest, context)
                        uncertaintyCache.put(predictedRequest, context, result)
                        logger.debug("Pre-cached predicted request: ${predictedRequest.take(30)}...")
                    } catch (e: Exception) {
                        logger.warn("Failed to pre-cache predicted request", e)
                    }
                }
            }
        }
    }

    /**
     * Предсказывает следующие запросы на основе паттернов
     */
    private fun predictNextRequests(currentRequest: String): List<Pair<String, Double>> {
        val predictions = mutableListOf<Pair<String, Double>>()

        // 1. Поиск по последовательностям
        val sequenceKey = currentRequest.hashCode().toString()
        sequencePatterns[sequenceKey]?.let { pattern ->
            pattern.nextRequests.forEach { (nextRequest, count) ->
                val probability = count.toDouble() / pattern.totalSequences
                predictions.add(nextRequest to probability)
            }
        }

        // 2. Поиск по похожим паттернам запросов
        val similarPatterns = findSimilarPatterns(currentRequest)
        similarPatterns.forEach { pattern ->
            val probability = (pattern.count.toDouble() / getTotalRequestCount()) * 0.5
            predictions.add(pattern.normalizedRequest to probability)
        }

        // 3. Генерация вариаций текущего запроса
        generateVariations(currentRequest).forEach { variation ->
            val probability = 0.2 // Базовая вероятность для вариаций
            predictions.add(variation to probability)
        }

        return predictions.sortedByDescending { it.second }.take(5)
    }

    /**
     * Находит похожие паттерны запросов
     */
    private fun findSimilarPatterns(request: String): List<RequestPattern> {
        return requestPatterns.values.filter { pattern ->
            calculateSimilarity(request, pattern.normalizedRequest) > 0.7
        }.sortedByDescending { it.count }.take(3)
    }

    /**
     * Генерирует вариации запроса
     */
    private fun generateVariations(request: String): List<String> {
        val variations = mutableListOf<String>()

        // Добавляем разные вопросы для одного и того же контекста
        when {
            request.contains("как") -> {
                variations.add(request.replace("как", "почему"))
                variations.add(request.replace("как", "что"))
            }
            request.contains("почему") -> {
                variations.add(request.replace("почему", "как"))
                variations.add(request.replace("почему", "зачем"))
            }
            request.contains("что") -> {
                variations.add(request.replace("что", "как"))
                variations.add(request.replace("что", "почему"))
            }
        }

        // Добавляем уточняющие варианты
        if (!request.contains("подробнее") && !request.contains("детально")) {
            variations.add("$request подробно")
            variations.add("$request детально")
        }

        return variations.filter { it.isNotBlank() && it != request }
    }

    /**
     * Анализирует последовательности запросов
     */
    private fun analyzeSequence(request: String, context: ChatContext) {
        val history = context.history.takeLast(5) // Анализируем последние 5 запросов

        if (history.isNotEmpty()) {
            val previousRequest = normalizeRequest(history.last().content)
            val sequenceKey = previousRequest.hashCode().toString()

            sequencePatterns.compute(sequenceKey) { _, existing ->
                val nextRequests = existing?.nextRequests?.toMutableMap() ?: mutableMapOf()
                nextRequests[request] = nextRequests.getOrDefault(request, 0) + 1

                SequencePattern(
                    baseRequest = previousRequest,
                    nextRequests = nextRequests,
                    totalSequences = (existing?.totalSequences ?: 0) + 1,
                    lastUsed = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Нормализует запрос для анализа паттернов
     */
    private fun normalizeRequest(request: String): String {
        return request.lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^а-яё\\s]"), "") // Оставляем только русские буквы и пробелы
    }

    /**
     * Генерирует ключ паттерна
     */
    private fun generatePatternKey(request: String, complexity: ComplexityLevel): String {
        return "${request.hashCode()}_${complexity.name}"
    }

    /**
     * Вычисляет схожесть двух запросов
     */
    private fun calculateSimilarity(request1: String, request2: String): Double {
        val words1 = request1.split(" ").toSet()
        val words2 = request2.split(" ").toSet()
        val intersection = words1.intersect(words2)
        val union = words1.union(words2)

        return if (union.isNotEmpty()) {
            intersection.size.toDouble() / union.size
        } else {
            0.0
        }
    }

    /**
     * Возвращает общее количество запросов
     */
    private fun getTotalRequestCount(): Long {
        return requestPatterns.values.sumOf { it.count }
    }

    /**
     * Запускает фоновый анализ паттернов
     */
    private fun startPatternAnalysis() {
        scheduler.scheduleAtFixedRate({
            try {
                cleanupOldPatterns()
                logger.debug("Pattern analysis completed")
            } catch (e: Exception) {
                logger.warn("Pattern analysis failed", e)
            }
        }, 5, 5, TimeUnit.MINUTES)
    }

    /**
     * Запускает прогрев кэша
     */
    private fun startCacheWarmup() {
        scheduler.scheduleAtFixedRate({
            try {
                warmupCacheWithPopularPatterns()
                logger.debug("Cache warmup completed")
            } catch (e: Exception) {
                logger.warn("Cache warmup failed", e)
            }
        }, 10, 30, TimeUnit.MINUTES)
    }

    /**
     * Очищает старые паттерны
     */
    private fun cleanupOldPatterns() {
        val now = System.currentTimeMillis()
        val maxAge = 7 * 24 * 60 * 60 * 1000L // 7 дней

        requestPatterns.entries.removeIf { (_, pattern) ->
            now - pattern.lastUsed > maxAge
        }

        sequencePatterns.entries.removeIf { (_, pattern) ->
            now - pattern.lastUsed > maxAge
        }
    }

    /**
     * Прогревает кэш популярными паттернами
     */
    private fun warmupCacheWithPopularPatterns() {
        val popularPatterns = requestPatterns.values
            .sortedByDescending { it.count }
            .take(20)

        popularPatterns.forEach { pattern ->
            // Создаем базовый контекст для прогрева
            val context = ChatContext(
                project = null,
                history = emptyList()
            )

            if (uncertaintyCache.get(pattern.normalizedRequest, context) == null) {
                val result = complexityAnalyzer.analyzeUncertainty(pattern.normalizedRequest, context)
                uncertaintyCache.put(pattern.normalizedRequest, context, result)
            }
        }
    }

    /**
     * Возвращает статистику предиктивного кэширования
     */
    fun getPredictiveStats(): PredictiveCacheStats {
        return PredictiveCacheStats(
            requestPatternsCount = requestPatterns.size,
            sequencePatternsCount = sequencePatterns.size,
            predictiveHits = predictiveHits.get(),
            predictiveMisses = predictiveMisses.get(),
            predictiveHitRate = {
                val total = predictiveHits.get() + predictiveMisses.get()
                if (total > 0) predictiveHits.get().toDouble() / total else 0.0
            }()
        )
    }

    /**
     * Останавливает фоновые задачи
     */
    fun shutdown() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }
}

/**
 * Паттерн запроса
 */
data class RequestPattern(
    val normalizedRequest: String,
    val complexity: ComplexityLevel,
    val count: Long,
    val firstUsed: Long,
    val lastUsed: Long,
    val avgComplexity: Double
)

/**
 * Паттерн последовательности запросов
 */
data class SequencePattern(
    val baseRequest: String,
    val nextRequests: Map<String, Int>,
    val totalSequences: Int,
    val lastUsed: Long
)

/**
 * Статистика предиктивного кэширования
 */
data class PredictiveCacheStats(
    val requestPatternsCount: Int,
    val sequencePatternsCount: Int,
    val predictiveHits: Long,
    val predictiveMisses: Long,
    val predictiveHitRate: Double
)