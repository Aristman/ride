package ru.marslab.ide.ride.service

import ru.marslab.ide.ride.model.chat.Message
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Хранилище истории сообщений чата
 *
 * Потокобезопасное хранилище для сообщений в рамках одной сессии.
 * В будущем может быть расширено для персистентности.
 */
class MessageHistory {

    private val messages = CopyOnWriteArrayList<Message>()

    /**
     * Добавляет сообщение в историю
     *
     * @param message Сообщение для добавления
     */
    fun addMessage(message: Message) {
        messages.add(message)
    }

    /**
     * Возвращает все сообщения
     *
     * @return Список всех сообщений
     */
    fun getMessages(): List<Message> {
        return messages.toList()
    }

    /**
     * Возвращает последние N сообщений
     *
     * @param count Количество сообщений
     * @return Список последних сообщений
     */
    fun getRecentMessages(count: Int): List<Message> {
        return messages.takeLast(count)
    }

    /**
     * Возвращает количество сообщений
     *
     * @return Количество сообщений в истории
     */
    fun getMessageCount(): Int {
        return messages.size
    }

    /**
     * Очищает всю историю
     */
    fun clear() {
        messages.clear()
    }

    /**
     * Проверяет, пуста ли история
     *
     * @return true если история пуста
     */
    fun isEmpty(): Boolean {
        return messages.isEmpty()
    }
}
