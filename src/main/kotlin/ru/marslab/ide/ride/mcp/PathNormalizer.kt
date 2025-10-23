package ru.marslab.ide.ride.mcp

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * Сервис для нормализации путей файловой системы через LLM
 * Используется для валидации и исправления путей перед отправкой в MCP инструменты
 */
class PathNormalizer(
    private val llmProvider: LLMProvider? = null
) {

    private val logger = Logger.getInstance(PathNormalizer::class.java)

    /**
     * Нормализует путь файловой системы через LLM
     * @param path Исходный путь от LLM
     * @param context Контекст операции (create_file, read_file и т.д.)
     * @return Нормализованный путь
     */
    suspend fun normalizePath(path: String, context: String = "file_operation"): String {
        return withContext(Dispatchers.IO) {
            try {
                // Быстрая проверка базовых проблем
                val quickFixed = quickFixPath(path)
                if (quickFixed != path) {
                    logger.info("Quick fixed path: '$path' -> '$quickFixed'")
                    return@withContext quickFixed
                }

                // Если LLM доступен и быстрая фиксация не помогла, запрашиваем LLM
                llmProvider?.let { provider ->
                    val normalized = requestLLMNormalization(path, context, provider)
                    logger.info("LLM normalized path: '$path' -> '$normalized'")
                    normalized
                } ?: quickFixed
            } catch (e: Exception) {
                logger.error("Failed to normalize path: $path", e)
                // В случае ошибки возвращаем базовую нормализацию
                quickFixPath(path)
            }
        }
    }

    /**
     * Быстрая фиксация очевидных проблем с путями
     */
    private fun quickFixPath(path: String): String {
        return path
            .replace("\\", "/")           // Обратные слэши на прямые
            .replace("\u0001", "/")        // Unicode разделители
            .replace(Regex("/+"), "/")     // Множественные слэши на один
            .trim('/')                    // Убираем начальные/конечные слэши
            .ifEmpty { "file.txt" }        // Если пусто, используем имя по умолчанию
    }

    /**
     * Запрашивает нормализацию пути у LLM
     */
    private suspend fun requestLLMNormalization(path: String, context: String, provider: LLMProvider): String {
        val systemPrompt = """
            Ты эксперт по файловым системам. Твоя задача - нормализовать пути файлов.

            Правила:
            1. Используй только прямые слэши (/) как разделители
            2. Удали все спецсимволы и escape-последовательности
            3. Исправь опечатки в именах файлов и папок
            4. Сохраняй логическую структуру пути
            5. Если путь некорректен, предложи разумную альтернативу
            6. Ответ должен содержать ТОЛЬКО нормализованный путь, без объяснений

            Примеры:
            - "src\u0001main\u0001go\u0001Solution.go" -> "src/main/go/Solution.go"
            - "src\\main\\kotlin\\Main.kt" -> "src/main/kotlin/Main.kt"
            - "src//main///kotlin" -> "src/main/kotlin"
            - "" -> "file.txt"
        """.trimIndent()

        val userMessage = """
            Нормализуй путь для $context: "$path"

            Ответ должен содержать только нормализованный путь.
        """.trimIndent()

        val response = provider.sendRequest(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            conversationHistory = emptyList(),
            parameters = LLMParameters(
                temperature = 0.1,
                maxTokens = 100
            )
        )

        return response.content.trim()
            .replace(Regex("^['\"]|['\"]$"), "") // Убираем кавычки если есть
            .replace("\\", "/")
            .ifEmpty { quickFixPath(path) }
    }
}