package ru.marslab.ide.ride.model

/**
 * Сообщение в диалоге для LLM провайдера
 *
 * @property role Роль отправителя (user, assistant, system)
 * @property content Содержимое сообщения
 */
data class ConversationMessage(
    val role: ConversationRole,
    val content: String
)

/**
 * Роль в диалоге для LLM провайдера
 */
enum class ConversationRole {
    /**
     * Сообщение от пользователя
     */
    USER,

    /**
     * Ответ от ассистента
     */
    ASSISTANT,

    /**
     * Системное сообщение/инструкция
     */
    SYSTEM
}