package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.reflect.KClass

/**
 * Интерфейс шины сообщений для A2A коммуникации между агентами
 *
 * MessageBus обеспечивает:
 * - Публикацию и подписку на сообщения
 * - Фильтрацию по типам сообщений и отправителям
 * - Гарантии доставки и обработки ошибок
 * - Метрики производительности и наблюдаемость
 */
interface MessageBus {

    /**
     * Публикует сообщение в шину
     *
     * @param message Сообщение для публикации
     * @return Boolean Успешность публикации
     */
    suspend fun publish(message: AgentMessage): Boolean

    /**
     * Подписывается на сообщения указанного типа
     *
     * @param messageType Класс типа сообщения
     * @param filter Фильтр для сообщений (опционально)
     * @return Flow поток сообщений
     */
    fun <T : AgentMessage> subscribe(
        messageType: KClass<T>,
        filter: ((T) -> Boolean)? = null
    ): Flow<T>

    /**
     * Подписывается на все сообщения
     *
     * @param filter Фильтр для сообщений (опционально)
     * @return Flow поток всех сообщений
     */
    fun subscribeAll(filter: ((AgentMessage) -> Boolean)? = null): Flow<AgentMessage>

    /**
     * Отправляет запрос и ожидает ответ
     *
     * @param request Запрос для отправки
     * @param timeoutMs Таймаут ожидания ответа
     * @return AgentMessage.Response Ответ на запрос
     * @throws TimeoutException если истекло время ожидания
     */
    suspend fun requestResponse(
        request: AgentMessage.Request,
        timeoutMs: Long = request.timeoutMs
    ): AgentMessage.Response

    /**
     * Получает метрики производительности шины сообщений
     */
    fun getMetrics(): MessageBusMetrics

    /**
     * Очищает шину сообщений (для тестов)
     */
    fun clear()
}

/**
 * Метрики производительности MessageBus
 */
data class MessageBusMetrics(
    val totalMessages: Long = 0,
    val messagesPerSecond: Double = 0.0,
    val averageLatencyMs: Double = 0.0,
    val errorRate: Double = 0.0,
    val activeSubscriptions: Int = 0,
    val queueSize: Int = 0
) {
    companion object {
        val EMPTY = MessageBusMetrics()
    }
}

/**
 * Исключения для MessageBus операций
 */
sealed class MessageBusException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class TimeoutException(message: String, cause: Throwable? = null) :
        MessageBusException(message, cause)

    class SerializationException(message: String, cause: Throwable? = null) :
        MessageBusException(message, cause)

    class DeliveryException(message: String, cause: Throwable? = null) :
        MessageBusException(message, cause)

    class SubscriptionException(message: String, cause: Throwable? = null) :
        MessageBusException(message, cause)
}