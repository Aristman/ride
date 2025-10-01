# Пример реализации ChatAgent

## Концепция

`ChatAgent` - это универсальная реализация агента, которая **НЕ привязана** к конкретному LLM провайдеру. Агент получает `LLMProvider` через конструктор (Dependency Injection) и делегирует ему обработку запросов.

## Преимущества подхода

1. **Гибкость**: Можно использовать любой LLM провайдер (Yandex GPT, OpenAI, Claude и т.д.)
2. **Тестируемость**: Легко подменить провайдер на mock для тестов
3. **Расширяемость**: Добавление нового провайдера не требует изменения агента
4. **Single Responsibility**: Агент отвечает за логику обработки, провайдер - за взаимодействие с API

## Пример кода

### Интерфейс Agent

```kotlin
package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext

/**
 * Базовый интерфейс для всех агентов
 */
interface Agent {
    /**
     * Обрабатывает запрос пользователя
     * 
     * @param request Текст запроса пользователя
     * @param context Контекст чата (история, проект, файлы)
     * @return Ответ агента
     */
    suspend fun processRequest(request: String, context: ChatContext): AgentResponse
    
    /**
     * Возвращает имя агента
     */
    fun getName(): String
    
    /**
     * Возвращает описание агента
     */
    fun getDescription(): String
}
```

### Реализация ChatAgent

```kotlin
package ru.marslab.ide.ride.agent.impl

import ru.marslab.ide.ride.agent.Agent
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.AgentResponse
import ru.marslab.ide.ride.model.ChatContext
import ru.marslab.ide.ride.model.LLMParameters
import com.intellij.openapi.diagnostic.Logger

/**
 * Универсальная реализация агента для общения с пользователем
 * 
 * Агент НЕ привязан к конкретному LLM провайдеру.
 * Провайдер передается через конструктор (Dependency Injection).
 */
class ChatAgent(
    private val llmProvider: LLMProvider,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
) : Agent {
    
    private val logger = Logger.getInstance(ChatAgent::class.java)
    
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        logger.info("Processing request, length: ${request.length}")
        
        try {
            // Формируем промпт с учетом контекста
            val fullPrompt = buildPrompt(request, context)
            
            // Делегируем запрос в LLM провайдер
            val llmResponse = llmProvider.sendRequest(
                prompt = fullPrompt,
                parameters = LLMParameters(
                    temperature = 0.7,
                    maxTokens = 2000
                )
            )
            
            // Проверяем успешность ответа
            if (!llmResponse.success) {
                logger.warn("LLM provider returned error: ${llmResponse.error}")
                return AgentResponse(
                    content = "Извините, произошла ошибка при обработке запроса: ${llmResponse.error}",
                    success = false,
                    error = llmResponse.error
                )
            }
            
            logger.info("Request processed successfully, tokens used: ${llmResponse.tokensUsed}")
            
            return AgentResponse(
                content = llmResponse.content,
                success = true,
                metadata = mapOf(
                    "tokensUsed" to llmResponse.tokensUsed,
                    "provider" to llmProvider.getProviderName()
                )
            )
            
        } catch (e: Exception) {
            logger.error("Error processing request", e)
            return AgentResponse(
                content = "Произошла непредвиденная ошибка: ${e.message}",
                success = false,
                error = e.message
            )
        }
    }
    
    override fun getName(): String = "Chat Agent"
    
    override fun getDescription(): String = 
        "Универсальный агент для общения с пользователем через ${llmProvider.getProviderName()}"
    
    /**
     * Формирует полный промпт с учетом системного промпта и контекста
     */
    private fun buildPrompt(request: String, context: ChatContext): String {
        val promptBuilder = StringBuilder()
        
        // Системный промпт
        promptBuilder.append(systemPrompt).append("\n\n")
        
        // История сообщений (последние N сообщений)
        val recentHistory = context.history.takeLast(5)
        if (recentHistory.isNotEmpty()) {
            promptBuilder.append("История диалога:\n")
            recentHistory.forEach { message ->
                promptBuilder.append("${message.role}: ${message.content}\n")
            }
            promptBuilder.append("\n")
        }
        
        // Контекст текущего файла (опционально)
        context.currentFile?.let { file ->
            promptBuilder.append("Текущий файл: ${file.name}\n")
        }
        
        // Выделенный текст (опционально)
        context.selectedText?.let { text ->
            promptBuilder.append("Выделенный код:\n```\n$text\n```\n\n")
        }
        
        // Запрос пользователя
        promptBuilder.append("Пользователь: $request")
        
        return promptBuilder.toString()
    }
    
    companion object {
        private const val DEFAULT_SYSTEM_PROMPT = """
            Ты - AI-ассистент для разработчиков в IntelliJ IDEA.
            Твоя задача - помогать программистам с их вопросами о коде, отладке и разработке.
            Отвечай четко, по существу и профессионально.
            Если нужно показать код, используй markdown форматирование.
        """.trimIndent()
    }
}
```

### Использование в AgentFactory

```kotlin
package ru.marslab.ide.ride.agent

import ru.marslab.ide.ride.agent.impl.ChatAgent
import ru.marslab.ide.ride.integration.llm.LLMProviderFactory
import ru.marslab.ide.ride.settings.PluginSettings
import com.intellij.openapi.components.service

/**
 * Фабрика для создания агентов
 */
object AgentFactory {
    
    /**
     * Создает ChatAgent с настроенным LLM провайдером
     */
    fun createChatAgent(): Agent {
        val settings = service<PluginSettings>()
        
        // Получаем провайдер из фабрики на основе настроек
        val llmProvider = LLMProviderFactory.createProvider(
            providerType = settings.selectedProvider, // "yandex", "openai", etc.
            apiKey = settings.getApiKey(),
            config = settings.getLLMConfig()
        )
        
        // Создаем агента с провайдером
        return ChatAgent(
            llmProvider = llmProvider,
            systemPrompt = settings.systemPrompt
        )
    }
    
    /**
     * Создает агента с кастомным провайдером (для тестов или специальных случаев)
     */
    fun createChatAgent(llmProvider: LLMProvider): Agent {
        return ChatAgent(llmProvider = llmProvider)
    }
}
```

## Как это работает

### 1. Инициализация

```kotlin
// В ChatService при старте
val agent = AgentFactory.createChatAgent()
```

### 2. Обработка запроса

```kotlin
// Когда пользователь отправляет сообщение
val response = agent.processRequest(
    request = "Как работают корутины в Kotlin?",
    context = ChatContext(
        project = project,
        history = messageHistory.getMessages(),
        currentFile = null,
        selectedText = null
    )
)
```

### 3. Внутри агента

```
ChatAgent.processRequest()
    ↓
buildPrompt() - формирует промпт с контекстом
    ↓
llmProvider.sendRequest() - отправляет в LLM
    ↓
Возвращает AgentResponse
```

## Добавление нового провайдера

Чтобы добавить поддержку нового LLM (например, OpenAI):

1. Создать `OpenAIProvider implements LLMProvider`
2. Добавить в `LLMProviderFactory`
3. Добавить настройки в UI

**ChatAgent НЕ нужно менять!** Он будет работать с любым провайдером.

## Пример с разными провайдерами

```kotlin
// С Yandex GPT
val yandexProvider = YandexGPTProvider(config)
val agentWithYandex = ChatAgent(yandexProvider)

// С OpenAI (гипотетически)
val openAIProvider = OpenAIProvider(config)
val agentWithOpenAI = ChatAgent(openAIProvider)

// С mock провайдером для тестов
val mockProvider = MockLLMProvider()
val agentForTest = ChatAgent(mockProvider)
```

Все три агента имеют одинаковый интерфейс и поведение, но используют разные LLM!

## Расширение функциональности

Если нужен агент с дополнительной логикой (например, для анализа кода):

```kotlin
class CodeAnalysisAgent(
    private val llmProvider: LLMProvider
) : Agent {
    
    override suspend fun processRequest(request: String, context: ChatContext): AgentResponse {
        // Специфичная логика для анализа кода
        val codeContext = extractCodeFromProject(context.project)
        val enhancedPrompt = """
            Проанализируй следующий код:
            $codeContext
            
            Вопрос пользователя: $request
        """.trimIndent()
        
        // Используем тот же LLMProvider!
        val llmResponse = llmProvider.sendRequest(enhancedPrompt, LLMParameters())
        
        return AgentResponse(
            content = llmResponse.content,
            success = llmResponse.success
        )
    }
    
    // ...
}
```

## Ключевые моменты

✅ **Agent** - это функциональная единица работы (обработка запроса)  
✅ **LLMProvider** - это способ взаимодействия с конкретным LLM  
✅ Агент использует провайдер через DI, не зная о его деталях  
✅ Легко тестировать, расширять и поддерживать  
✅ Следует принципам SOLID
