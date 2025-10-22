package ru.marslab.ide.ride.service.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.marslab.ide.ride.model.chat.Message
import ru.marslab.ide.ride.model.chat.MessageRole
import ru.marslab.ide.ride.model.chat.ChatSession
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

    // ~/.idea/plugins/ride/chats/
    private val baseDir: Path by lazy {
        val home = System.getProperty("user.home")
        Paths.get(home, ".idea", "plugins", "ride", "chats")
    }

    suspend fun loadAllSessions(): Pair<List<ChatSession>, Map<String, List<Message>>> = mutex.withLock {
        return@withLock try {
            val indexPath = baseDir.resolve("index.json")
            if (!indexPath.exists()) return@withLock Pair(emptyList(), emptyMap())

            val index = json.decodeFromString(ChatIndex.serializer(), indexPath.readText())
            val sessions = mutableListOf<ChatSession>()
            val histories = mutableMapOf<String, List<Message>>()

            for (id in index.sessions) {
                val dir = baseDir.resolve("sessions").resolve(id)
                val metadataPath = dir.resolve("metadata.json")
                val messagesPath = dir.resolve("messages.json")
                if (!metadataPath.exists() || !messagesPath.exists()) continue

                val sessionStored = json.decodeFromString(PersistentChatSession.serializer(), metadataPath.readText())
                val messagesStored = json.decodeFromString(ListSerializer(PersistentMessage.serializer()), messagesPath.readText())

                val session = ChatSession(
                    id = sessionStored.id,
                    title = sessionStored.title,
                    createdAt = Instant.ofEpochMilli(sessionStored.createdAtEpochMs),
                    updatedAt = Instant.ofEpochMilli(sessionStored.updatedAtEpochMs)
                )
                val messages = messagesStored.map { pm ->
                    Message(
                        content = pm.content,
                        role = when (pm.role) {
                            PersistentRole.USER -> MessageRole.USER
                            PersistentRole.ASSISTANT -> MessageRole.ASSISTANT
                            PersistentRole.SYSTEM -> MessageRole.SYSTEM
                        },
                        metadata = pm.metadata
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

    suspend fun saveSession(session: ChatSession, messages: List<Message>) = mutex.withLock {
        try {
            val sessionsDir = baseDir.resolve("sessions").createDirectoriesIfNeeded()
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
                    content = m.content,
                    role = when (m.role) {
                        MessageRole.USER -> PersistentRole.USER
                        MessageRole.ASSISTANT -> PersistentRole.ASSISTANT
                        MessageRole.SYSTEM -> PersistentRole.SYSTEM
                    },
                    metadata = m.metadata.mapValues { it.value.toString() }
                )
            }
            val messagesPath = dir.resolve("messages.json")
            messagesPath.writeText(json.encodeToString(persistentMessages))

            // обновляем индекс
            val indexPath = baseDir.resolve("index.json")
            val idx = if (indexPath.exists()) json.decodeFromString(ChatIndex.serializer(), indexPath.readText()) else ChatIndex()
            val updated = idx.copy(sessions = (idx.sessions + session.id).toSet().toList())
            indexPath.parent?.createDirectoriesIfNeeded()
            indexPath.writeText(json.encodeToString(updated))
        } catch (e: IOException) {
            logger.warn("Failed to save session ${session.id}: ${e.message}", e)
        }
    }

    suspend fun deleteSession(sessionId: String, withBackup: Boolean = true) = mutex.withLock {
        try {
            val dir = baseDir.resolve("sessions").resolve(sessionId)
            if (!dir.exists()) return@withLock

            if (withBackup) {
                val backup = baseDir.resolve("backup").resolve("${sessionId}.zip")
                runCatching { zipDirectory(dir, backup) }
            }
            // простое удаление файлов
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }

            // обновляем индекс
            val indexPath = baseDir.resolve("index.json")
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
}
