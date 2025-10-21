package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File

/**
 * Агент для разбиения больших файлов на чанки
 * 
 * Capabilities:
 * - file_chunking - разбиение файлов
 * - token_counting - подсчет токенов
 */
class CodeChunkerToolAgent : BaseToolAgent(
    agentType = AgentType.CODE_CHUNKER,
    toolCapabilities = setOf(
        "file_chunking",
        "token_counting"
    )
) {
    
    companion object {
        private const val DEFAULT_CHUNK_SIZE = 1000 // строк
        private const val DEFAULT_OVERLAP = 50 // строк перекрытия
    }
    
    override fun getDescription(): String {
        return "Разбивает большие файлы на управляемые чанки с перекрытием"
    }
    
    override fun validateInput(input: StepInput): ValidationResult {
        val files = input.getList<String>("files")
        
        if (files.isNullOrEmpty()) {
            return ValidationResult.failure("files is required and must not be empty")
        }
        
        val chunkSize = input.getInt("chunk_size")
        if (chunkSize != null && chunkSize <= 0) {
            return ValidationResult.failure("chunk_size must be positive")
        }
        
        return ValidationResult.success()
    }
    
    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files") ?: emptyList()
        val chunkSize = step.input.getInt("chunk_size") ?: DEFAULT_CHUNK_SIZE
        val overlap = step.input.getInt("overlap") ?: DEFAULT_OVERLAP
        
        logger.info("Chunking ${files.size} files with chunk_size=$chunkSize, overlap=$overlap")
        
        val chunks = mutableListOf<FileChunk>()
        var totalTokens = 0
        
        for (filePath in files) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) {
                logger.warn("File does not exist: $filePath")
                continue
            }
            
            val fileChunks = chunkFile(file, chunkSize, overlap)
            chunks.addAll(fileChunks)
            
            totalTokens += fileChunks.sumOf { it.estimatedTokens }
        }
        
        logger.info("Chunking completed: ${chunks.size} chunks created, ~$totalTokens tokens")
        
        return StepResult.success(
            output = StepOutput.of(
                "chunks" to chunks,
                "chunk_count" to chunks.size,
                "total_tokens" to totalTokens
            ),
            metadata = mapOf(
                "files_processed" to files.size,
                "chunk_size" to chunkSize,
                "overlap" to overlap
            )
        )
    }
    
    private fun chunkFile(file: File, chunkSize: Int, overlap: Int): List<FileChunk> {
        val chunks = mutableListOf<FileChunk>()
        
        try {
            val lines = file.readLines()
            
            if (lines.size <= chunkSize) {
                // Файл помещается в один чанк
                chunks.add(
                    FileChunk(
                        file = file.absolutePath,
                        chunkIndex = 0,
                        startLine = 1,
                        endLine = lines.size,
                        content = lines.joinToString("\n"),
                        estimatedTokens = estimateTokens(lines.joinToString("\n"))
                    )
                )
            } else {
                // Разбиваем на чанки с перекрытием
                var currentLine = 0
                var chunkIndex = 0
                
                while (currentLine < lines.size) {
                    val endLine = minOf(currentLine + chunkSize, lines.size)
                    val chunkLines = lines.subList(currentLine, endLine)
                    
                    chunks.add(
                        FileChunk(
                            file = file.absolutePath,
                            chunkIndex = chunkIndex,
                            startLine = currentLine + 1,
                            endLine = endLine,
                            content = chunkLines.joinToString("\n"),
                            estimatedTokens = estimateTokens(chunkLines.joinToString("\n"))
                        )
                    )
                    
                    currentLine += chunkSize - overlap
                    chunkIndex++
                }
            }
            
        } catch (e: Exception) {
            logger.error("Error chunking file ${file.absolutePath}", e)
        }
        
        return chunks
    }
    
    /**
     * Простая оценка количества токенов (примерно 1 токен = 4 символа)
     */
    private fun estimateTokens(text: String): Int {
        return text.length / 4
    }
}

/**
 * Чанк файла
 */
data class FileChunk(
    val file: String,
    val chunkIndex: Int,
    val startLine: Int,
    val endLine: Int,
    val content: String,
    val estimatedTokens: Int
)
