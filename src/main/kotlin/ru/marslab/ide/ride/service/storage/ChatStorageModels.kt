package ru.marslab.ide.ride.service.storage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PersistentRole { USER, ASSISTANT, SYSTEM }

@Serializable
data class PersistentMessage(
    val id: String,
    val content: String,
    val role: PersistentRole,
    val timestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class AgentContext(
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class PersistentChatSession(
    val id: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val messages: List<PersistentMessage> = emptyList(),
    val context: AgentContext = AgentContext()
)

@Serializable
data class ChatStorageMetadata(
    val version: Int = 1,
    val retentionDays: Int = 90
)

@Serializable
data class ChatIndex(
    val sessions: List<String> = emptyList(),
    val metadata: ChatStorageMetadata = ChatStorageMetadata()
)
