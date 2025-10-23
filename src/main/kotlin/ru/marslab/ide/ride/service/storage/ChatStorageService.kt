package ru.marslab.ide.ride.service.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ru.marslab.ide.ride.model.agent.FormattedOutput
import ru.marslab.ide.ride.model.chat.ChatSession
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Service(Service.Level.APP)
class ChatStorageService {
    private val logger = Logger.getInstance(ChatStorageService::class.java)
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Получает базовую директорию для хранения чатов проекта
     */
    private fun getBaseDir(project: Project): Path {
        val projectBasePath = project.basePath
        return if (projectBasePath != null) {
            Paths.get(projectBasePath, ".idea", "ride", "chats")
        } else {
            logger.warn("Project base path is null for ${project.name}, using fallback directory")
            Paths.get(System.getProperty("user.home"), ".idea", "plugins", "ride", "chats")
        }
    }

    suspend fun loadAllSessions(project: Project): Pair<List<ChatSession>, Map<String, List<Message>>> =
        mutex.withLock {
            return@withLock try {
                val baseDirPath = getBaseDir(project)
                val indexPath = baseDirPath.resolve("index.json")
                if (!indexPath.exists()) return@withLock Pair(emptyList(), emptyMap())

                val index = json.decodeFromString(ChatIndex.serializer(), indexPath.readText())
                val sessions = mutableListOf<ChatSession>()
                val histories = mutableMapOf<String, List<Message>>()

                for (id in index.sessions) {
                    val dir = baseDirPath.resolve("sessions").resolve(id)
                    val metadataPath = dir.resolve("metadata.json")
                    val messagesPath = dir.resolve("messages.json")
                    if (!metadataPath.exists() || !messagesPath.exists()) continue

                    val sessionStored =
                        json.decodeFromString(PersistentChatSession.serializer(), metadataPath.readText())
                    val messagesStored =
                        json.decodeFromString(ListSerializer(PersistentMessage.serializer()), messagesPath.readText())

                    val session = ChatSession(
                        id = sessionStored.id,
                        title = sessionStored.title,
                        createdAt = Instant.ofEpochMilli(sessionStored.createdAtEpochMs),
                        updatedAt = Instant.ofEpochMilli(sessionStored.updatedAtEpochMs)
                    )
                    val messages = messagesStored.map { pm ->
                        Message(
                            id = pm.id,
                            content = pm.content,
                            role = when (pm.role) {
                                PersistentRole.USER -> MessageRole.USER
                                PersistentRole.ASSISTANT -> MessageRole.ASSISTANT
                                PersistentRole.SYSTEM -> MessageRole.SYSTEM
                            },
                            timestamp = pm.timestamp,
                            metadata = parseMetadataFromJson(pm.metadataJson)
                        )
                    }

                    sessions += session
                    histories[session.id] = messages
                }
                Pair(sessions, histories)
            } catch (e: Exception) {
                logger.warn("Failed to load sessions: ${e.message}", e)
                Pair(emptyList(), emptyMap())
            }
        }

    suspend fun saveSession(project: Project, session: ChatSession, messages: List<Message>) = mutex.withLock {
        try {
            val baseDirPath = getBaseDir(project)
            val sessionsDir = baseDirPath.resolve("sessions").createDirectoriesIfNeeded()
            val dir = sessionsDir.resolve(session.id).createDirectoriesIfNeeded()

            val persistentSession = PersistentChatSession(
                id = session.id,
                title = session.title,
                createdAtEpochMs = session.createdAt.toEpochMilli(),
                updatedAtEpochMs = session.updatedAt.toEpochMilli(),
                messages = emptyList() // сообщения отдельно
            )
            val metadataPath = dir.resolve("metadata.json")
            metadataPath.writeText(json.encodeToString(persistentSession))

            val persistentMessages = messages.map { m ->
                PersistentMessage(
                    id = m.id,
                    content = m.content,
                    role = when (m.role) {
                        MessageRole.USER -> PersistentRole.USER
                        MessageRole.ASSISTANT -> PersistentRole.ASSISTANT
                        MessageRole.SYSTEM -> PersistentRole.SYSTEM
                    },
                    timestamp = m.timestamp,
                    metadataJson = serializeMetadataToJson(m.metadata)
                )
            }
            val messagesPath = dir.resolve("messages.json")
            messagesPath.writeText(json.encodeToString(persistentMessages))

            // обновляем индекс
            val indexPath = baseDirPath.resolve("index.json")
            val idx = if (indexPath.exists()) json.decodeFromString(
                ChatIndex.serializer(),
                indexPath.readText()
            ) else ChatIndex()
            val updated = idx.copy(sessions = (idx.sessions + session.id).toSet().toList())
            indexPath.parent?.createDirectoriesIfNeeded()
            indexPath.writeText(json.encodeToString(updated))
        } catch (e: IOException) {
            logger.warn("Failed to save session ${session.id}: ${e.message}", e)
        }
    }

    suspend fun deleteSession(project: Project, sessionId: String, withBackup: Boolean = true) = mutex.withLock {
        try {
            val baseDirPath = getBaseDir(project)
            val dir = baseDirPath.resolve("sessions").resolve(sessionId)
            if (!dir.exists()) return@withLock

            if (withBackup) {
                val backup = baseDirPath.resolve("backup").resolve("${sessionId}.zip")
                runCatching { zipDirectory(dir, backup) }
            }
            // простое удаление файлов
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }

            // обновляем индекс
            val indexPath = baseDirPath.resolve("index.json")
            if (indexPath.exists()) {
                val idx = json.decodeFromString(ChatIndex.serializer(), indexPath.readText())
                val updated = idx.copy(sessions = idx.sessions.filterNot { it == sessionId })
                indexPath.writeText(json.encodeToString(updated))
            }
        } catch (e: Exception) {
            logger.warn("Failed to delete session $sessionId: ${e.message}", e)
        }
    }

    private fun Path.createDirectoriesIfNeeded(): Path {
        if (!this.exists()) this.createDirectories()
        return this
    }

    private fun zipDirectory(sourceDir: Path, zipFile: Path) {
        // Упрощенно: резервное копирование пропускаем для сокращения зависимости, можно реализовать позже
        // Здесь можно добавить real zip при необходимости
    }

    /**
     * Сериализует metadata в JSON строку
     * Сохраняет примитивные типы (String, Number, Boolean) и FormattedOutput
     */
    private fun serializeMetadataToJson(metadata: Map<String, Any>): String {
        return try {
            val jsonObject = buildJsonObject {
                metadata.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, JsonPrimitive(value))
                        is Number -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        is FormattedOutput -> {
                            // Сериализуем FormattedOutput как вложенный JSON
                            try {
                                val formattedJson = json.encodeToString(FormattedOutput.serializer(), value)
                                put(key, JsonPrimitive(formattedJson))
                            } catch (e: Exception) {
                                logger.warn("Failed to serialize FormattedOutput: ${e.message}")
                            }
                        }
                        // Игнорируем другие сложные объекты
                    }
                }
            }
            json.encodeToString(JsonObject.serializer(), jsonObject)
        } catch (e: Exception) {
            logger.warn("Failed to serialize metadata: ${e.message}", e)
            "{}"
        }
    }

    /**
     * Парсит metadata из JSON строки
     * Восстанавливает правильные типы для примитивов и FormattedOutput
     */
    private fun parseMetadataFromJson(metadataJson: String): Map<String, Any> {
        return try {
            if (metadataJson.isBlank() || metadataJson == "{}") {
                return emptyMap()
            }

            val jsonObject = json.decodeFromString(JsonObject.serializer(), metadataJson)
            jsonObject.mapValues { (key, jsonElement) ->
                when (val primitive = jsonElement as? JsonPrimitive) {
                    null -> jsonElement.toString()
                    else -> when {
                        primitive.isString -> {
                            val content = primitive.content
                            // Пытаемся десериализовать FormattedOutput если ключ подходящий
                            if (key == "formattedOutput" && content.startsWith("{")) {
                                try {
                                    json.decodeFromString(FormattedOutput.serializer(), content)
                                } catch (e: Exception) {
                                    logger.warn("Failed to deserialize FormattedOutput: ${e.message}")
                                    content
                                }
                            } else {
                                content
                            }
                        }

                        primitive.content == "true" -> true
                        primitive.content == "false" -> false
                        primitive.content.toLongOrNull() != null -> primitive.content.toLong()
                        primitive.content.toDoubleOrNull() != null -> primitive.content.toDouble()
                        else -> primitive.content
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse metadata from JSON: ${e.message}", e)
            emptyMap()
        }
    }
}
