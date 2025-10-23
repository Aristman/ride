package ru.marslab.ide.ride.model.mcp

import kotlinx.serialization.Serializable

/**
 * Тип подключения к MCP серверу
 */
@Serializable
enum class MCPServerType {
    /** Подключение через stdio (запуск процесса) */
    STDIO,

    /** Подключение через HTTP */
    HTTP
}

/**
 * Конфигурация MCP сервера
 *
 * @property name Имя сервера (уникальный идентификатор)
 * @property type Тип подключения (stdio/http)
 * @property command Команда для запуска (для stdio)
 * @property args Аргументы команды (для stdio)
 * @property env Переменные окружения (для stdio)
 * @property url URL для подключения (для http)
 * @property headers HTTP заголовки (для http)
 * @property enabled Включен ли сервер
 */
@Serializable
data class MCPServerConfig(
    val name: String,
    val type: MCPServerType,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val enabled: Boolean = true
) {
    /**
     * Валидация конфигурации
     */
    fun validate(): ValidationResult {
        if (name.isBlank()) {
            return ValidationResult.Error("Server name cannot be empty")
        }

        return when (type) {
            MCPServerType.STDIO -> {
                if (command.isNullOrBlank()) {
                    ValidationResult.Error("Command is required for STDIO type")
                } else {
                    ValidationResult.Valid
                }
            }

            MCPServerType.HTTP -> {
                if (url.isNullOrBlank()) {
                    ValidationResult.Error("URL is required for HTTP type")
                } else if (!isValidUrl(url)) {
                    ValidationResult.Error("Invalid URL format")
                } else {
                    ValidationResult.Valid
                }
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            uri.scheme in listOf("http", "https") && uri.host != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Результат валидации
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Error(val message: String) : ValidationResult()

        fun isValid(): Boolean = this is Valid
        fun getErrorMessage(): String? = (this as? Error)?.message
    }
}
