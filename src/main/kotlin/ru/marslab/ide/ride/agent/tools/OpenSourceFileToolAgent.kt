package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.application.ApplicationManager
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.service.rag.RagSourceLinkService
import ru.marslab.ide.ride.model.rag.RagChunkOpenAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Инструмент для открытия файлов в IDE по запросу LLM/оркестратора.
 *
 * Входные параметры (step.input):
 * - path: String (обязательный) — путь относительно корня workspace
 * - start_line: Int (обязательный)
 * - end_line: Int (опциональный, по умолчанию = start_line)
 */
class OpenSourceFileToolAgent : BaseToolAgent(
    agentType = AgentType.FILE_OPERATIONS,
    toolCapabilities = setOf("open_source_file")
) {

    override fun getDescription(): String =
        "Открытие файлов в IDE по пути и диапазону строк."

    override fun validateInput(input: StepInput): ru.marslab.ide.ride.agent.ValidationResult {
        val path = input.getString("path")
        val start = input.get<Int>("start_line")
        if (path.isNullOrBlank()) {
            return ru.marslab.ide.ride.agent.ValidationResult.failure("path is required")
        }
        if (start == null || start <= 0) {
            return ru.marslab.ide.ride.agent.ValidationResult.failure("start_line must be positive Int")
        }
        return ru.marslab.ide.ride.agent.ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val path = step.input.getString("path")!!.trim()
        val startLine = step.input.get<Int>("start_line") ?: 1
        val endLine = step.input.get<Int>("end_line") ?: startLine

        val command = "open?path=$path&startLine=$startLine&endLine=$endLine"
        val service = RagSourceLinkService.getInstance()

        // Попытка распарсить и выполнить через сервис
        val openAction = service.extractSourceInfo(command)
            ?: RagChunkOpenAction(command = command, path = path, startLine = startLine, endLine = endLine)

        // Выполняем UI операцию в EDT
        val success = try {
            withContext(Dispatchers.Main) {
                var result = false
                ApplicationManager.getApplication().invokeAndWait {
                    result = service.handleOpenAction(openAction)
                }
                result
            }
        } catch (e: Exception) {
            false
        }

        return if (success) {
            StepResult.success(
                output = StepOutput.of(
                    "path" to path,
                    "start_line" to startLine,
                    "end_line" to endLine,
                    "command" to command
                ),
                metadata = mapOf("tool" to "open_source_file", "executed" to true)
            )
        } else {
            StepResult.error(
                error = "Не удалось открыть файл: $path",
                output = StepOutput.of(
                    "path" to path,
                    "start_line" to startLine,
                    "end_line" to endLine,
                    "command" to command
                )
            )
        }
    }
}
