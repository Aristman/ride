package ru.marslab.ide.ride.service.rag

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import ru.marslab.ide.ride.actions.OpenFileAction
import ru.marslab.ide.ride.model.rag.RagChunkOpenAction
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Сервис для обработки source links в RAG ответах
 */
@Service(Service.Level.APP)
class RagSourceLinkService {

    private val logger = Logger.getInstance(RagSourceLinkService::class.java)
    private val settings = service<PluginSettings>()

    companion object {
        fun getInstance(): RagSourceLinkService = service()
    }

    /**
     * Обрабатывает действие открытия файла из RAG ответа
     *
     * @param openAction действие открытия файла
     * @return true если файл успешно открыт, иначе false
     */
    fun handleOpenAction(openAction: RagChunkOpenAction): Boolean {
        if (!settings.ragSourceLinksEnabled) {
            logger.debug("RAG source links are disabled")
            return false
        }

        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isEmpty()) {
            logger.warn("No open projects found for source link navigation")
            return false
        }

        return try {
            // Пробуем открыть файл в любом из открытых проектов
            for (project in openProjects) {
                val ok = OpenFileAction.execute(project, openAction.command)
                if (ok) {
                    logger.info("Successfully opened file in project '${project.name}': ${openAction.path}:${openAction.startLine}-${openAction.endLine}")
                    return true
                } else {
                    logger.debug("File not found in project '${project.name}': ${openAction.path}")
                }
            }
            logger.warn("Failed to open file in all open projects: ${openAction.path}")
            false
        } catch (e: Exception) {
            logger.error("Error opening file from source link: ${openAction.command}", e)
            false
        }
    }

    /**
     * Проверяет, включены ли source links
     */
    fun isSourceLinksEnabled(): Boolean = settings.ragSourceLinksEnabled

    /**
     * Проверяет, является ли команда source link действием
     */
    fun isSourceLinkCommand(command: String): Boolean {
        return command.startsWith("open?path=") && command.contains("startLine=")
    }

    /**
     * Извлекает параметры пути из команды
     */
    fun extractSourceInfo(command: String): RagChunkOpenAction? {
        if (!isSourceLinkCommand(command)) {
            return null
        }

        return try {
            val params = parseOpenCommand(command)
            RagChunkOpenAction(
                command = command,
                path = params["path"] ?: return null,
                startLine = params["startLine"]?.toIntOrNull() ?: 1,
                endLine = params["endLine"]?.toIntOrNull() ?: params["startLine"]?.toIntOrNull() ?: 1
            )
        } catch (e: Exception) {
            logger.warn("Failed to parse source link command: $command", e)
            null
        }
    }

    private fun parseOpenCommand(command: String): Map<String, String> {
        if (!command.startsWith("open?")) {
            return emptyMap()
        }

        val query = command.substringAfter("open?")
        return query.split("&")
            .mapNotNull { param ->
                val (key, value) = param.split("=", limit = 2)
                key to value
            }
            .toMap()
    }
}