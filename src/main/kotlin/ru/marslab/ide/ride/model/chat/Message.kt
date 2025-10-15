package ru.marslab.ide.ride.model.chat

import java.util.UUID

/**
 * Модель сообщения в чате
 *
 * @property id Уникальный идентификатор сообщения
 * @property content Содержимое сообщения
 * @property role Роль отправителя
 * @property timestamp Временная метка создания сообщения (Unix timestamp в миллисекундах)
 * @property metadata Дополнительные метаданные сообщения
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Проверяет, является ли сообщение от пользователя
     */
    fun isFromUser(): Boolean = role == MessageRole.USER
    
    /**
     * Проверяет, является ли сообщение от ассистента
     */
    fun isFromAssistant(): Boolean = role == MessageRole.ASSISTANT
    
    /**
     * Проверяет, является ли сообщение системным
     */
    fun isSystem(): Boolean = role == MessageRole.SYSTEM
}
