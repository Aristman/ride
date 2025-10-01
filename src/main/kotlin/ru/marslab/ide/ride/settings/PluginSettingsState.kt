package ru.marslab.ide.ride.settings

/**
 * State класс для хранения настроек плагина
 * 
 * Используется для персистентности настроек между сессиями IDE
 */
data class PluginSettingsState(
    var folderId: String = "",
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    var temperature: Double = 0.7,
    var maxTokens: Int = 2000
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.

Правила:
- Отвечай четко, по существу и профессионально
- Если нужно показать код, используй markdown форматирование с указанием языка
- Если не уверен в ответе, честно скажи об этом
- Предлагай лучшие практики и современные подходы
- Будь дружелюбным и помогающим

Отвечай на русском языке, если пользователь пишет на русском."""
    }
}
