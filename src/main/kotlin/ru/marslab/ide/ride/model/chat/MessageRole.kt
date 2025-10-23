package ru.marslab.ide.ride.model.chat

/**
 * Роль отправителя сообщения в чате
 */
enum class MessageRole {
    /**
     * Сообщение от пользователя
     */
    USER,

    /**
     * Сообщение от AI ассистента
     */
    ASSISTANT,

    /**
     * Системное сообщение (промпт, инструкции)
     */
    SYSTEM
}
