package ru.marslab.ide.ride.agent.a2a

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.system.measureTimeMillis

/**
 * InMemory реализация MessageBus для A2A коммуникации
 *
 * Особенности:
 * - Основана на Kotlin Coroutines Flow
 * - Поддерживает Request-Response паттерн с таймаутами
 * - Собирает метрики производительности
 * - Обеспечивает фильтрацию сообщений
 * - Обрабатывает ошибки и предоставляет retry механизмы
 */
class InMemoryMessageBus : MessageBus {

    private val logger = Logger.getInstance(InMemoryMessageBus::class.java)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Основной канал для всех сообщений
    private val messageChannel = Channel<AgentMessage>(Channel.UNLIMITED)

    // SharedFlow для подписчиков (горячие подписки)
    private val sharedFlow: SharedFlow<AgentMessage> = messageChannel
        .receiveAsFlow()
        .shareIn(CoroutineScope(Dispatchers.Default), started = SharingStarted.WhileSubscribed(5000))

    // Request-Response обработка
    private val pendingRequests = mutableMapOf<String, CompletableDeferred<AgentMessage.Response>>()
    private val pendingRequestsMutex = Mutex()

    // Метрики
    private val metricsMutex = Mutex()
    private var metrics = MessageBusMetrics.EMPTY.copy()
    private var messageCount = 0L
    private var totalLatency = 0L
    private var errorCount = 0L
    private var subscriptionCount = 0

    // Очистка ресурсов
    private val cleanupJob = Job()

    override suspend fun publish(message: AgentMessage): Boolean {
        return try {
            val latency = measureTimeMillis {
                if (!messageChannel.isClosedForSend) {
                    messageChannel.send(message)

                    // Обработка специальных типов сообщений
                    when (message) {
                        is AgentMessage.Response -> handleResponse(message)
                        is AgentMessage.Ack -> handleAck(message)
                        else -> { /* Обычные сообщения обрабатываются подписчиками */ }
                    }

                    true
                } else {
                    false
                }
            }

            // Обновляем метрики
            updateMetrics(latency, success = true)
            logger.debug("Published message ${message.id} of type ${message::class.simpleName}")
            true

        } catch (e: Exception) {
            logger.error("Failed to publish message ${message.id}", e)
            updateMetrics(0, success = false)
            false
        }
    }

    override fun <T : AgentMessage> subscribe(
        messageType: KClass<T>,
        filter: ((T) -> Boolean)?
    ): Flow<T> {
        subscriptionCount++
        return sharedFlow
            .filter { messageType.isInstance(it) }
            .map { it as T }
            .filter { message -> filter?.invoke(message) ?: true }
            .onEach { message ->
                // Отправляем acknowledgement для запросов
                if (message is AgentMessage.Request) {
                    publishAck(message.id)
                }
            }
            .catch { e ->
                logger.error("Error in subscription for ${messageType.simpleName}", e)
            }
    }

    override fun subscribeAll(filter: ((AgentMessage) -> Boolean)?): Flow<AgentMessage> {
        subscriptionCount++
        return sharedFlow
            .filter { message -> filter?.invoke(message) ?: true }
            .onEach { message ->
                if (message is AgentMessage.Request) {
                    publishAck(message.id)
                }
            }
            .catch { e ->
                logger.error("Error in global subscription", e)
            }
    }

    override suspend fun requestResponse(
        request: AgentMessage.Request,
        timeoutMs: Long
    ): AgentMessage.Response {
        val deferred = CompletableDeferred<AgentMessage.Response>()

        pendingRequestsMutex.withLock {
            pendingRequests[request.id] = deferred
        }

        try {
            // Публикуем запрос
            val published = publish(request)
            if (!published) {
                throw MessageBusException.DeliveryException("Failed to publish request")
            }

            // Ожидаем ответ с таймаутом
            return withTimeout(timeoutMs) {
                deferred.await()
            }

        } catch (e: TimeoutCancellationException) {
            pendingRequestsMutex.withLock {
                pendingRequests.remove(request.id)
            }
            throw MessageBusException.TimeoutException(
                "Request ${request.id} timed out after ${timeoutMs}ms", e
            )
        } catch (e: Exception) {
            pendingRequestsMutex.withLock {
                pendingRequests.remove(request.id)
            }
            throw MessageBusException.DeliveryException("Request failed", e)
        }
    }

    override fun getMetrics(): MessageBusMetrics {
        return runBlocking {
            metricsMutex.withLock {
                val now = Clock.System.now()
                val messagesPerSecond = if (messageCount > 0) {
                    messageCount.toDouble() / ((now.toEpochMilliseconds() - (metrics.totalMessages * 1000)).coerceAtLeast(1))
                } else 0.0

                metrics.copy(
                    totalMessages = messageCount,
                    messagesPerSecond = messagesPerSecond,
                    averageLatencyMs = if (messageCount > 0) totalLatency.toDouble() / messageCount else 0.0,
                    errorRate = if (messageCount > 0) (errorCount.toDouble() / messageCount) * 100 else 0.0,
                    activeSubscriptions = subscriptionCount,
                    queueSize = messageChannel.tryReceive().let { if (it != null) 1 else 0 }
                )
            }
        }
    }

    override fun clear() {
        runBlocking {
            // Отменяем все pending requests
            pendingRequestsMutex.withLock {
                pendingRequests.values.forEach { it.cancel("Message bus cleared") }
                pendingRequests.clear()
            }

            // Очищаем канал
            messageChannel.close()
            cleanupJob.cancel()

            // Сбрасываем метрики
            metricsMutex.withLock {
                metrics = MessageBusMetrics.EMPTY
                messageCount = 0
                totalLatency = 0
                errorCount = 0
                subscriptionCount = 0
            }
        }
    }

    private suspend fun handleResponse(response: AgentMessage.Response) {
        pendingRequestsMutex.withLock {
            pendingRequests[response.requestId]?.let { deferred ->
                if (deferred.isActive) {
                    deferred.complete(response)
                }
                pendingRequests.remove(response.requestId)
            }
        }
    }

    private suspend fun handleAck(ack: AgentMessage.Ack) {
        // Acknowledgement сообщения обрабатываются логированием
        logger.debug("Received ack for message ${ack.originalMessageId}: ${ack.status}")
    }

    private suspend fun publishAck(messageId: String) {
        val ack = AgentMessage.Ack(
            senderId = "message-bus",
            originalMessageId = messageId,
            status = AgentMessage.AckStatus.RECEIVED
        )
        publish(ack)
    }

    private suspend fun updateMetrics(latencyMs: Long, success: Boolean) {
        metricsMutex.withLock {
            messageCount++
            if (success) {
                totalLatency += latencyMs
            } else {
                errorCount++
            }
        }
    }
}