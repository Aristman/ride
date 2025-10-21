package ru.marslab.ide.ride.codeanalysis.chunker

import ru.marslab.ide.ride.integration.llm.TokenCounter
import ru.marslab.ide.ride.model.codeanalysis.CodeChunk

/**
 * Разбивает большие файлы на чанки для обработки
 * 
 * @property tokenCounter Счетчик токенов
 * @property maxTokensPerChunk Максимальное количество токенов в одном чанке
 */
class CodeChunker(
    private val tokenCounter: TokenCounter,
    private val maxTokensPerChunk: Int = 4000
) {
    /**
     * Разбивает содержимое файла на чанки
     * 
     * @param content Содержимое файла
     * @return Список чанков
     */
    fun chunkFile(content: String): List<CodeChunk> {
        val chunks = mutableListOf<CodeChunk>()
        val lines = content.lines()

        var currentChunk = StringBuilder()
        var currentTokens = 0
        var startLine = 1

        for ((index, line) in lines.withIndex()) {
            val lineTokens = tokenCounter.countTokens(line)

            // Если добавление строки превысит лимит и чанк не пустой, сохраняем текущий чанк
            if (currentTokens + lineTokens > maxTokensPerChunk && currentChunk.isNotEmpty()) {
                chunks.add(
                    CodeChunk(
                        content = currentChunk.toString(),
                        startLine = startLine,
                        endLine = index,
                        tokens = currentTokens
                    )
                )
                currentChunk = StringBuilder()
                currentTokens = 0
                startLine = index + 1
            }

            currentChunk.appendLine(line)
            currentTokens += lineTokens
        }

        // Добавляем последний чанк, если он не пустой
        if (currentChunk.isNotEmpty()) {
            chunks.add(
                CodeChunk(
                    content = currentChunk.toString(),
                    startLine = startLine,
                    endLine = lines.size,
                    tokens = currentTokens
                )
            )
        }

        return chunks
    }

    /**
     * Проверяет, нужно ли разбивать файл на чанки
     * 
     * @param content Содержимое файла
     * @return true если файл нужно разбить
     */
    fun needsChunking(content: String): Boolean {
        return tokenCounter.countTokens(content) > maxTokensPerChunk
    }
}
