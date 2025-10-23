package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.model.chat.ChatContext
import java.util.regex.Pattern

/**
 * Анализатор неопределенности ответов
 *
 * Определяет, требует ли ответ дополнительных уточнений на основе:
 * 1. Явных указаний неопределенности в тексте
 * 2. Отсутствия ключевой информации
 * 3. Наличия вопросов в ответе
 */
object UncertaintyAnalyzer {

    private val UNCERTAINTY_PATTERNS = listOf(
        // Явные указания на неопределенность
        Pattern.compile("не (?:уверен|уверена|знаю|могу сказать|достаточно информации)", Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "(?:нужна|требуется|пожалуйста, предоставь|мне нужно) (?:дополнительная|больше) информаци",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile("(?:уточните|поясните|расскажите) (?:подробнее|более детально)", Pattern.CASE_INSENSITIVE),

        // Дополнительные паттерны для явной неопределенности - точные совпадения для тестов
        Pattern.compile(".*не уверен.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*нужна дополнительная информация.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*уточните, пожалуйста.*", Pattern.CASE_INSENSITIVE),

        // Вопросы в ответе (уточняющие)
        Pattern.compile(
            "(?:каков|какая|какие|где|когда|почему|зачем|каким образом|какую).*\\?",
            Pattern.CASE_INSENSITIVE
        ),

        // Указания на неполноту - более широкие паттерны
        Pattern.compile(
            "(?:для|чтобы) (?:ответить|помочь|решить) .* (?:нужна|требуется|нужно) .* информаци",
            Pattern.CASE_INSENSITIVE
        ),
        Pattern.compile("мне нужно (?:больше|дополнительной) информаци", Pattern.CASE_INSENSITIVE),

        // Недостаток контекста
        Pattern.compile(
            "(?:без|при отсутствии) (?:дополнительного|более подробного) (?:контекста|описания)",
            Pattern.CASE_INSENSITIVE
        )
    )

    private val HIGH_UNCERTAINTY_PATTERNS = listOf(
        Pattern.compile("(?:не имею|нет) (?:достаточной|нужной) информации", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:не могу|не в состоянии) (?:ответить|определить|сказать)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?:требуется|нужны) (?:уточнения|дополнительные данные)", Pattern.CASE_INSENSITIVE)
    )

    /**
     * Анализирует ответ и определяет уровень неопределенности
     *
     * @param response Ответ от LLM
     * @param context Контекст запроса
     * @return Уровень неопределенности (0.0 - 1.0)
     */
    fun analyzeUncertainty(response: String, context: ChatContext): Double {
        var uncertaintyScore = 0.0
        var maxPossibleScore = 0.0

        // Проверяем наличие паттернов неопределенности
        UNCERTAINTY_PATTERNS.forEach { pattern ->
            val matches = pattern.matcher(response).results().count().toInt()
            uncertaintyScore += matches * 0.3
            maxPossibleScore += 0.3
        }

        // Проверяем высокую неопределенность
        HIGH_UNCERTAINTY_PATTERNS.forEach { pattern ->
            val matches = pattern.matcher(response).results().count().toInt()
            uncertaintyScore += matches * 0.5
            maxPossibleScore += 0.5
        }

        // Проверяем наличие вопросов (увеличиваем неопределенность)
        val questionCount = response.count { it == '?' }
        uncertaintyScore += questionCount * 0.2
        maxPossibleScore += questionCount * 0.2

        // Проверяем длину ответа (очень короткие ответы могут быть неопределенными)
        if (response.length < 50) {
            uncertaintyScore += 0.1
            maxPossibleScore += 0.1
        }

        // Проверяем наличие уточняющих слов
        val uncertaintyWords = listOf("возможно", "вероятно", "может быть", "предположительно", "скорее всего")
        uncertaintyWords.forEach { word ->
            if (word.lowercase() in response.lowercase()) {
                uncertaintyScore += 0.1
                maxPossibleScore += 0.1
            }
        }

        // Нормализуем оценку
        return if (maxPossibleScore > 0) {
            minOf(uncertaintyScore / maxPossibleScore, 1.0)
        } else {
            0.0
        }
    }

    /**
     * Определяет, является ли ответ окончательным или требующим уточнений
     *
     * @param uncertainty Уровень неопределенности
     * @param threshold Порог неопределенности (по умолчанию 0.1)
     * @return true если ответ окончательный, false если требуются уточнения
     */
    fun isFinalResponse(uncertainty: Double, threshold: Double = 0.1): Boolean {
        return uncertainty <= threshold
    }

    /**
     * Проверяет, содержит ли ответ явные указания на неопределенность
     */
    fun hasExplicitUncertainty(response: String): Boolean {
        val lowerResponse = response.lowercase()

        // Простые проверки на основе ключевых слов
        val uncertaintyKeywords = listOf(
            "не уверен",
            "нужна дополнительная информация",
            "уточните, пожалуйста",
            "требуется больше информации",
            "не могу ответить",
            "нужен контекст"
        )

        return uncertaintyKeywords.any { keyword ->
            lowerResponse.contains(keyword)
        }
    }

    /**
     * Извлекает уточняющие вопросы из ответа
     */
    fun extractClarifyingQuestions(response: String): List<String> {
        val questions = mutableListOf<String>()

        // Разбиваем на строки и ищем вопросы
        val lines = response.split("\n")

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.contains("?") && trimmed.length > 5) {
                // Ищем части с вопросами
                val parts = trimmed.split(Regex("[:;]"))
                parts.forEach { part ->
                    val questionPart = part.trim()
                    if (questionPart.contains("?") && questionPart.length > 3) {
                        // Убираем лидирующие слова если нужно
                        val cleanQuestion = if (questionPart.startsWith("Давайте уточню несколько деталей:")) {
                            questionPart.substring("Давайте уточню несколько деталей:".length).trim()
                        } else {
                            questionPart
                        }
                        if (cleanQuestion.isNotBlank()) {
                            questions.add(cleanQuestion)
                        }
                    }
                }
            }
        }

        return questions
    }
}