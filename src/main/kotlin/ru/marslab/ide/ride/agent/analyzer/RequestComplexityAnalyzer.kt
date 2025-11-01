package ru.marslab.ide.ride.agent.analyzer

import com.intellij.openapi.diagnostic.Logger
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.model.orchestrator.ComplexityLevel
import kotlin.math.max
import kotlin.math.min

/**
 * Результат анализа неопределенности запроса
 */
data class UncertaintyResult(
    /** Оценка неопределенности от 0.0 (полная уверенность) до 1.0 (полная неопределенность) */
    val score: Double,

    /** Уровень сложности */
    val complexity: ComplexityLevel,

    /** Рекомендуемые действия */
    val suggestedActions: List<String>,

    /** Обоснование оценки */
    val reasoning: String,

    /** Признаки сложности, которые были обнаружены */
    val detectedFeatures: List<String> = emptyList()
)

/**
 * Анализатор неопределенности запросов пользователя
 *
 * Определяет сложность запроса на основе:
 * - Лексических паттернов и ключевых слов
 * - Длины запроса
 * - Наличия вопросов о коде
 * - Контекста диалога
 * - Специфических маркеров сложности
 */
class RequestComplexityAnalyzer {

    private val logger = Logger.getInstance(RequestComplexityAnalyzer::class.java)

    /**
     * Анализирует запрос и оценивает неопределенность
     */
    fun analyzeUncertainty(request: String, context: ChatContext): UncertaintyResult {
        val requestLower = request.lowercase().trim()

        // Быстрые паттерны для простых запросов
        val simpleResult = analyzeSimplePatterns(requestLower)
        if (simpleResult != null) {
            logger.debug("Simple query detected: ${simpleResult.reasoning}")
            return simpleResult
        }

        // Анализ сложности запроса
        val complexityScore = calculateComplexityScore(request, requestLower, context)
        val complexity = determineComplexityLevel(complexityScore)

        // Оценка неопределенности на основе сложности
        val uncertaintyScore = calculateUncertaintyScore(complexityScore, request, context)

        // Формирование результата
        val (reasoning, features) = generateReasoningAndFeatures(complexityScore, requestLower, context)
        val suggestedActions = generateSuggestedActions(complexity, uncertaintyScore)

        return UncertaintyResult(
            score = uncertaintyScore,
            complexity = complexity,
            suggestedActions = suggestedActions,
            reasoning = reasoning,
            detectedFeatures = features
        )
    }

    /**
     * Проверяет на простые паттерны и возвращает результат если найдено совпадение
     */
    private fun analyzeSimplePatterns(requestLower: String): UncertaintyResult? {
        // Очень короткие вопросы
        if (requestLower.length < 20 && requestLower.matches(Regex("^(как|что|когда|где|почему|кто).*\\??$"))) {
            return UncertaintyResult(
                score = 0.05,
                complexity = ComplexityLevel.LOW,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Очень короткий общий вопрос",
                detectedFeatures = listOf("короткий_вопрос", "общий_вопрос")
            )
        }

        // Вопросы о времени
        if (requestLower.matches(Regex(".*который час.*|.*сколько времени.*|.*текущее время.*"))) {
            return UncertaintyResult(
                score = 0.0,
                complexity = ComplexityLevel.LOW,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Вопрос о текущем времени",
                detectedFeatures = listOf("вопрос_о_времени")
            )
        }

        // Вопросы о погоде
        if (requestLower.matches(Regex(".*какая погода.*|.*погода сегодня.*|.*температура.*"))) {
            return UncertaintyResult(
                score = 0.0,
                complexity = ComplexityLevel.LOW,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Вопрос о погоде",
                detectedFeatures = listOf("вопрос_о_погоде")
            )
        }

        // Простые факты
        if (requestLower.matches(Regex(".*что такое.*|.*кто такой.*|.*где находится.*")) &&
            requestLower.length < 50) {
            return UncertaintyResult(
                score = 0.1,
                complexity = ComplexityLevel.LOW,
                suggestedActions = listOf("прямой_ответ"),
                reasoning = "Вопрос о простом факте",
                detectedFeatures = listOf("вопрос_о_факте")
            )
        }

        return null
    }

    /**
     * Рассчитывает оценку сложности от 0.0 до 1.0
     */
    private fun calculateComplexityScore(request: String, requestLower: String, context: ChatContext): Double {
        var score = 0.0

        // 1. Анализ ключевых слов сложности
        val complexityKeywords = mapOf(
            // Сложные технические операции
            "проанализируй" to 0.8,
            "найди баги" to 0.8,
            "оптимизируй" to 0.7,
            "рефактор" to 0.7,
            "улучши" to 0.6,

            // Архитектурные вопросы
            "архитектур" to 0.8,
            "дизайн" to 0.6,
            "структура" to 0.5,

            // Анализ качества
            "проверь качество" to 0.7,
            "code review" to 0.6,
            "качество кода" to 0.6,

            // Сложные задачи
            "создай отчет" to 0.6,
            "сканируй" to 0.5,
            "исследуй" to 0.5,

            // Средняя сложность
            "объясни" to 0.4,
            "почему" to 0.3,
            "как работает" to 0.4,
            "помоги понять" to 0.3
        )

        for ((keyword, weight) in complexityKeywords) {
            if (requestLower.contains(keyword)) {
                score += weight
                logger.debug("Found complexity keyword: $keyword (weight: $weight)")
            }
        }

        // 2. Длина запроса (более длинные запросы обычно сложнее)
        if (request.length > 200) score += 0.2
        else if (request.length > 100) score += 0.1

        // 3. Наличие нескольких вопросов
        val questionCount = request.count { it == '?' }
        if (questionCount > 1) score += 0.2 * questionCount

        // 4. Упоминание файлов, кода, проекта
        val codeKeywords = listOf("файл", "код", "проект", "класс", "метод", "функция")
        val codeKeywordCount = codeKeywords.count { keyword -> requestLower.contains(keyword) }
        if (codeKeywordCount > 0) score += 0.2 * min(codeKeywordCount, 3)

        // 5. Технические термины
        val technicalTerms = listOf("api", "база данных", "алгоритм", "библиотека", "фреймворк", "тести")
        val technicalTermCount = technicalTerms.count { term -> requestLower.contains(term) }
        if (technicalTermCount > 0) score += 0.15 * min(technicalTermCount, 2)

        // 6. Контекст диалога (если есть предыдущие сообщения)
        if (context.history.isNotEmpty()) {
            score += 0.1 // Контекст усложняет анализ
        }

        // Ограничиваем результат диапазоном [0.0, 1.0]
        return score.coerceIn(0.0, 1.0)
    }

    /**
     * Определяет уровень сложности на основе оценки
     */
    private fun determineComplexityLevel(score: Double): ComplexityLevel {
        return when {
            score < 0.3 -> ComplexityLevel.LOW
            score < 0.7 -> ComplexityLevel.MEDIUM
            else -> ComplexityLevel.HIGH
        }
    }

    /**
     * Рассчитывает оценку неопределенности на основе сложности и других факторов
     */
    private fun calculateUncertaintyScore(complexityScore: Double, request: String, context: ChatContext): Double {
        var uncertainty = complexityScore * 0.7 // Базовая неопределенность от сложности

        // Увеличиваем неопределенность если:
        // - Запрос слишком короткий (< 20 символов)
        if (request.trim().length < 20) {
            uncertainty += 0.2
        }

        // - Нет четкого вопроса (отсутствует ?)
        if (!request.contains("?") && complexityScore < 0.5) {
            uncertainty += 0.1
        }

        // - Запрос содержит несколько несвязанных тем
        val topics = extractTopics(request)
        if (topics.size > 2) {
            uncertainty += 0.15
        }

        // Ограничиваем результат
        return uncertainty.coerceIn(0.0, 1.0)
    }

    /**
     * Извлекает темы из запроса (простая реализация)
     */
    private fun extractTopics(request: String): List<String> {
        val topicKeywords = listOf("код", "архитектура", "тестирование", "оптимизация", "баг", "рефакторинг")
        val requestLower = request.lowercase()

        return topicKeywords.filter { topic -> requestLower.contains(topic) }
    }

    /**
     * Генерирует обоснование оценки и список обнаруженных признаков
     */
    private fun generateReasoningAndFeatures(
        score: Double,
        requestLower: String,
        context: ChatContext
    ): Pair<String, List<String>> {
        val features = mutableListOf<String>()
        val reasoning = StringBuilder()

        if (score < 0.3) {
            reasoning.append("Простой запрос: ")
            if (requestLower.length < 50) {
                reasoning.append("короткий, ")
                features.add("короткий_запрос")
            }
            if (requestLower.contains("?")) {
                reasoning.append("четкий вопрос, ")
                features.add("четкий_вопрос")
            }
            reasoning.append("высокая уверенность в ответе")
        } else if (score < 0.7) {
            reasoning.append("Запрос средней сложности: ")
            if (requestLower.contains("объясни")) {
                reasoning.append("требует объяснения, ")
                features.add("требует_объяснения")
            }
            if (requestLower.contains("код") || requestLower.contains("файл")) {
                reasoning.append("связан с кодом, ")
                features.add("связан_с_кодом")
            }
            reasoning.append("требует контекстного ответа")
        } else {
            reasoning.append("Сложный запрос: ")
            if (requestLower.contains("проанализируй")) {
                reasoning.append("требует анализа, ")
                features.add("требует_анализа")
            }
            if (requestLower.contains("архитектур")) {
                reasoning.append("архитектурный, ")
                features.add("архитектурный")
            }
            if (requestLower.length > 100) {
                reasoning.append("подробный, ")
                features.add("подробный")
            }
            reasoning.append("требует планирования")
        }

        if (context.history.isNotEmpty()) {
            reasoning.append(" (с учетом контекста диалога)")
            features.add("есть_контекст")
        }

        return reasoning.toString() to features
    }

    /**
     * Генерирует рекомендуемые действия
     */
    private fun generateSuggestedActions(complexity: ComplexityLevel, uncertainty: Double): List<String> {
        val actions = mutableListOf<String>()

        when (complexity) {
            ComplexityLevel.LOW -> {
                actions.add("прямой_ответ")
                if (uncertainty > 0.1) {
                    actions.add("уточнить_если_нужно")
                }
            }

            ComplexityLevel.MEDIUM -> {
                actions.add("контекстный_ответ")
                if (uncertainty > 0.2) {
                    actions.add("задать_уточняющие_вопросы")
                }
                actions.add("проверить_доступность_контекста")
            }

            ComplexityLevel.HIGH -> {
                actions.add("создать_план")
                actions.add("использовать_оркестратор")
                actions.add("поиск_контекста")
                if (uncertainty > 0.3) {
                    actions.add("уточнить_требования")
                }
            }

            ComplexityLevel.VERY_HIGH -> {
                actions.add("создать_детальный_план")
                actions.add("использовать_оркестратор")
                actions.add("поиск_контекста")
                actions.add("сегментация_задачи")
                if (uncertainty > 0.2) {
                    actions.add("уточнить_требования")
                    actions.add("консультация_с_пользователем")
                }
            }
        }

        return actions
    }
}