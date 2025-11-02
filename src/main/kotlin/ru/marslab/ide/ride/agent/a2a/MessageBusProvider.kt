package ru.marslab.ide.ride.agent.a2a

/**
 * Единый провайдер MessageBus для A2A-коммуникаций.
 * Все компоненты (оркестратор, реестр, агенты) должны использовать общую шину.
 */
object MessageBusProvider {
    private val sharedBus: MessageBus by lazy { InMemoryMessageBus() }

    fun get(): MessageBus = sharedBus
}
