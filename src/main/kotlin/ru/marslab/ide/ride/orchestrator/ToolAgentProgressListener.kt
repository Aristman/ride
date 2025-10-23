package ru.marslab.ide.ride.orchestrator

import ru.marslab.ide.ride.model.chat.ToolAgentStatusMessage

/**
 * Слушатель для отслеживания прогресса выполнения tool agents
 */
interface ToolAgentProgressListener {
    /**
     * Вызывается когда tool agent начинает выполняться
     */
    fun onToolAgentStarted(message: ToolAgentStatusMessage)

    /**
     * Вызывается когда tool agent меняет статус
     */
    fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage)

    /**
     * Вызывается когда tool agent завершается (успешно или с ошибкой)
     */
    fun onToolAgentCompleted(message: ToolAgentStatusMessage)

    /**
     * Вызывается когда tool agent завершается с ошибкой
     */
    fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String)
}

/**
 * Утилитарный класс для создания слушателей прогресса
 */
object ToolAgentProgressListeners {
    /**
     * Создает композитный слушатель, который делегирует вызовы нескольким слушателям
     */
    fun composite(vararg listeners: ToolAgentProgressListener): ToolAgentProgressListener {
        return object : ToolAgentProgressListener {
            override fun onToolAgentStarted(message: ToolAgentStatusMessage) {
                listeners.forEach { it.onToolAgentStarted(message) }
            }

            override fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage) {
                listeners.forEach { it.onToolAgentStatusUpdated(message) }
            }

            override fun onToolAgentCompleted(message: ToolAgentStatusMessage) {
                listeners.forEach { it.onToolAgentCompleted(message) }
            }

            override fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String) {
                listeners.forEach { it.onToolAgentFailed(message, error) }
            }
        }
    }

    /**
     * Создает слушатель, который логирует все события
     */
    fun logging(): ToolAgentProgressListener = object : ToolAgentProgressListener {
        override fun onToolAgentStarted(message: ToolAgentStatusMessage) {
            println("Tool Agent Started: ${message.result.agentName}")
        }

        override fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage) {
            println("Tool Agent Updated: ${message.result.agentName} -> ${message.result.status}")
        }

        override fun onToolAgentCompleted(message: ToolAgentStatusMessage) {
            println("Tool Agent Completed: ${message.result.agentName}")
        }

        override fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String) {
            println("Tool Agent Failed: ${message.result.agentName} - $error")
        }
    }

    /**
     * Создает пустой слушатель, который ничего не делает
     */
    fun empty(): ToolAgentProgressListener = object : ToolAgentProgressListener {
        override fun onToolAgentStarted(message: ToolAgentStatusMessage) {}
        override fun onToolAgentStatusUpdated(message: ToolAgentStatusMessage) {}
        override fun onToolAgentCompleted(message: ToolAgentStatusMessage) {}
        override fun onToolAgentFailed(message: ToolAgentStatusMessage, error: String) {}
    }
}