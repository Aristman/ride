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
                
                // Если путь выглядит нормально после быстрой фиксации, используем его
                if (isPathNormal(quickFixed)) {
                    logger.info("Path is already normal: '$path' -> '$quickFixed'")
                    return@withContext quickFixed
                }

                // Если LLM доступен и путь требует сложной нормализации, запрашиваем LLM
                llmProvider?.let { provider ->
                    val normalized = requestLLMNormalization(path, context, provider)
                    val finalPath = if (isPathNormal(normalized)) normalized else quickFixed
                    logger.info("LLM normalized path: '$path' -> '$finalPath'")
                    finalPath
                } ?: quickFixed
            } catch (e: Exception) {
                logger.error("Failed to normalize path: $path", e)
                // В случае ошибки возвращаем базовую нормализацию
                quickFixPath(path)
            }
        }
    }

    /**
     * Проверяет, является ли путь нормальным (не требует дополнительной обработки)
     */
    private fun isPathNormal(path: String): Boolean {
        return path.isNotEmpty() &&
                path.length < 255 &&
                !path.contains("\\") &&
                !path.contains("\u0001") &&
                !path.contains("//") &&
                !path.startsWith("/") &&
                !path.endsWith("/") &&
                !path.contains("\n") &&
                !path.contains("\r") &&
                !path.contains(" ") &&
                path.matches(Regex("^[a-zA-Z0-9._/-]+$"))
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
            .trim()                       // Убираем пробелы
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
            7. НЕ заменяй имя файла на название операции!

            Примеры:
            - "src\u0001main\u0001go\u0001Solution.go" -> "src/main/go/Solution.go"
            - "src\\main\\kotlin\\Main.kt" -> "src/main/kotlin/Main.kt"
            - "src//main///kotlin" -> "src/main/kotlin"
            - "text12.txt" -> "text12.txt"
            - "test.md" -> "test.md"
            - "" -> "file.txt"
        """.trimIndent()

        val userMessage = """
            Нормализуй этот путь файла: "$path"

            ВАЖНО: Сохрани оригинальное имя файла! Не заменяй его на "$context"!
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

        val responseText = response.content.trim()
        
        // Извлекаем путь из ответа LLM (может содержать лишний текст)
        val extractedPath = extractPathFromResponse(responseText)
        
        return extractedPath
            .replace(Regex("^['\"]|['\"]$"), "") // Убираем кавычки если есть
            .replace("\\", "/")
            .ifEmpty { quickFixPath(path) }
    }

    /**
     * Извлекает путь из ответа LLM, который может содержать лишний текст
     */
    private fun extractPathFromResponse(response: String): String {
        // Если ответ выглядит как нормальный путь, возвращаем как есть
        if (isPathNormal(response)) {
            return response
        }
        
        // Ищем строки, которые выглядят как пути файлов
        val pathPatterns = listOf(
            Regex("[a-zA-Z0-9._/-]+\\.[a-zA-Z0-9]+"), // файлы с расширением
            Regex("[a-zA-Z0-9._/-]+/[a-zA-Z0-9._/-]+"), // пути с папками
            Regex("[a-zA-Z0-9._-]+\\.[a-zA-Z0-9]+") // простые имена файлов
        )
        
        for (pattern in pathPatterns) {
            val match = pattern.find(response)
            if (match != null) {
                val foundPath = match.value
                if (foundPath.length < 255 && !foundPath.contains(" ")) {
                    return foundPath
                }
            }
        }
        
        // Если ничего не найдено, возвращаем первую строку без пробелов
        return response.lines()
            .firstOrNull { it.trim().isNotEmpty() && !it.contains(" ") && it.length < 100 }
            ?: response.take(50).replace(" ", "_")
    }
}