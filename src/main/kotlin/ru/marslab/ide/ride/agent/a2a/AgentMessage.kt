package ru.marslab.ide.ride.agent.a2a

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Базовый класс для всех A2A сообщений между агентами
 *
 * A2A (Agent-to-Agent) протокол обеспечивает прямую коммуникацию между агентами
 * минуя централизованный оркестратор, что улучшает масштабируемость и наблюдаемость
 */
sealed class AgentMessage {
    abstract val id: String
    abstract val senderId: String
    abstract val timestamp: Long
    abstract val metadata: Map<String, @Contextual Any>

    /**
     * Запрос от одного агента другому с ожиданием ответа
     */
    @Serializable
    data class Request(
        override val id: String = UUID.randomUUID().toString(),
        override val senderId: String,
        val targetId: String? = null, // null = broadcast
        val messageType: String,
        val payload: MessagePayload,
        override val timestamp: Long = System.currentTimeMillis(),
        val timeoutMs: Long = 30000, // 30 секунд по умолчанию
        override val metadata: Map<String, @Contextual Any> = emptyMap()
    ) : AgentMessage()

    /**
     * Ответ на запрос
     */
    @Serializable
    data class Response(
        override val id: String = UUID.randomUUID().toString(),
        override val senderId: String,
        val requestId: String, // ID оригинального запроса
        val success: Boolean,
        val payload: MessagePayload,
        val error: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
        override val metadata: Map<String, @Contextual Any> = emptyMap()
    ) : AgentMessage()

    /**
     * Событие для широковещательной рассылки (event-driven коммуникация)
     */
    @Serializable
    data class Event(
        override val id: String = UUID.randomUUID().toString(),
        override val senderId: String,
        val eventType: String,
        val payload: MessagePayload,
        override val timestamp: Long = System.currentTimeMillis(),
        override val metadata: Map<String, @Contextual Any> = emptyMap()
    ) : AgentMessage()

    /**
     * Acknowledgement сообщения для подтверждения доставки
     */
    @Serializable
    data class Ack(
        override val id: String = UUID.randomUUID().toString(),
        override val senderId: String,
        val originalMessageId: String,
        val status: AckStatus,
        override val timestamp: Long = System.currentTimeMillis(),
        override val metadata: Map<String, @Contextual Any> = emptyMap()
    ) : AgentMessage()

    enum class AckStatus {
        RECEIVED,
        PROCESSED,
        FAILED
    }
}

/**
 * Базовый sealed class для всех payload'ов сообщений
 * Обеспечивает типизацию и валидацию данных между агентами
 */
@Serializable
sealed class MessagePayload {
    @Serializable
    data class ProjectStructurePayload(
        val files: List<String>,
        val directories: List<String>,
        val projectType: String,
        val totalFiles: Int,
        val scannedAt: Long
    ) : MessagePayload()

    @Serializable
    data class FilesScannedPayload(
        val files: List<String>,
        val scanPath: String,
        val fileTypes: Map<String, Int>, // file extension -> count
        val scanDurationMs: Long
    ) : MessagePayload()

    @Serializable
    data class CodeAnalysisPayload(
        val findings: List<CodeFinding>,
        val summary: AnalysisSummary,
        val processedFiles: Int
    ) : MessagePayload()

    @Serializable
    data class CodeFinding(
        val file: String,
        val line: Int?,
        val severity: String, // critical, high, medium, low
        val rule: String,
        val message: String,
        val suggestion: String
    )

    @Serializable
    data class AnalysisSummary(
        val totalFindings: Int,
        val criticalCount: Int,
        val highCount: Int,
        val mediumCount: Int,
        val lowCount: Int
    )

    @Serializable
    data class ErrorPayload(
        val error: String,
        val cause: String? = null,
        val stackTrace: List<String> = emptyList(),
        val context: Map<String, @Contextual Any> = emptyMap()
    ) : MessagePayload()

    @Serializable
    data class ProgressPayload(
        val stepId: String,
        val status: String, // started, progress, completed, failed
        val progress: Int, // 0-100
        val message: String? = null
    ) : MessagePayload()

    @Serializable
    data class ExecutionStatusPayload(
        val status: String, // STARTED, COMPLETED, FAILED
        val agentId: String,
        val requestId: String,
        val timestamp: Long,
        val result: String? = null,
        val error: String? = null
    ) : MessagePayload()

    @Serializable
    data class AgentInfoPayload(
        val agentId: String,
        val agentType: String,
        val legacyAgentClass: String,
        val supportedMessageTypes: Set<String>? = null,
        val timestamp: Long
    ) : MessagePayload()

    @Serializable
    data class TextPayload(
        val text: String,
        val metadata: Map<String, @Contextual Any> = emptyMap()
    ) : MessagePayload()

    @Serializable
    data class CustomPayload(
        val type: String,
        val data: Map<String, @Contextual Any>
    ) : MessagePayload()
}