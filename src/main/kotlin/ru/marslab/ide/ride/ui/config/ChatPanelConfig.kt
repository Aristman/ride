package ru.marslab.ide.ride.ui.config

/**
 * Конфигурация и константы для ChatPanel
 */
object ChatPanelConfig {

    // Размеры UI компонентов
    const val HISTORY_WIDTH = 400
    const val HISTORY_HEIGHT = 400
    const val INPUT_HEIGHT = 80
    const val INPUT_ROWS = 3

    // Константы для обработки кода
    const val COPY_LINK_PREFIX = "ride-copy:"
    const val CODE_CACHE_LIMIT = 200
    const val DEFAULT_LANGUAGE = "text"

    // Уровень неопределенности
    const val IS_FINAL_LEVEL = 0.1

    // Псевдонимы языков программирования
    val LANGUAGE_ALIASES = mapOf(
        "js" to "javascript",
        "ts" to "typescript",
        "tsx" to "typescript",
        "jsx" to "javascript",
        "py" to "python",
        "kt" to "kotlin",
        "kts" to "kotlin",
        "c++" to "cpp",
        "hpp" to "cpp",
        "h" to "c",
        "cs" to "csharp",
        "rb" to "ruby",
        "ps1" to "powershell",
        "sh" to "bash",
        "shell" to "bash",
        "sql" to "sql",
        "yml" to "yaml",
        "yaml" to "yaml",
        "md" to "markdown"
    )

    // CSS классы для статусов
    object StatusClasses {
        const val FINAL = "status-final"
        const val LOW_CONFIDENCE = "status-low-confidence"
        const val UNCERTAIN = "status-uncertain"
    }

    // CSS классы для ролей сообщений
    object RoleClasses {
        const val USER = "user"
        const val ASSISTANT = "assistant"
        const val SYSTEM = "system"
        const val AFTER_SYSTEM = "after-system"
    }

    // Иконки для индикаторов
    object Icons {
        const val USER = "👤"
        const val ASSISTANT = "🤖"
        const val SYSTEM = "ℹ️"
        const val LOADING = "⏳"
        const val ERROR = "❌"
        const val WARNING = "⚠️"
        const val SUCCESS = "✅"
        const val QUESTION = "❓"
        const val COPY = "📊"
        const val HELLO = "👋"
        const val COPY_CODE = "&#128203;"
    }

    // Тексты сообщений
    object Messages {
        const val WELCOME = """👋 Привет! Я AI-ассистент для разработчиков. Чем могу помочь?
            
**Доступные команды:**
• `/terminal <команда>` - выполнить команду в термінале (например: `/terminal git status`)
• `/exec <команда>` - альтернативный синтаксис для термінала
• `/plan <задача>` - создать план и выполнить задачу по шагам"""
        const val CONFIGURATION_WARNING = "⚠️ Плагин не настроен. Перейдите в Settings → Tools → ride для настройки API ключа."
        const val HISTORY_CLEARED = "История чата очищена."
        const val PROCESSING_REQUEST = "⏳ Обработка запроса..."
        const val CONFIRM_CLEAR_CHAT = "Вы уверены, что хотите очистить историю чата?"
        const val CONFIRMATION_TITLE = "Подтверждение"
    }

    // Префиксы для сообщений
    object Prefixes {
        const val USER = "Вы"
        const val ASSISTANT = "Ассистент"
        const val SYSTEM = "Система"
    }

    // Задержки для UI обновлений
    object Delays {
        const val APPEARANCE_REFRESH_MS = 150
    }

    // Тексты для статусов
    object StatusTexts {
        const val FINAL_ANSWER = "Окончательный ответ"
        const val REQUIRE_CLARIFICATION = "Требуются уточнения"
        const val ANSWER_WITH_PARSING = "Ответ с парсингом"
        const val LOW_CONFIDENCE_ANSWER = "Ответ с низкой уверенностью"
        const val UNCERTAINTY_TEMPLATE = "(неопределенность: %d%%)"
    }
}