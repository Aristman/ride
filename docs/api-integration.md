# Интеграция с Yandex GPT API

## Обзор

Документ описывает интеграцию плагина Ride с Yandex GPT API для обеспечения функциональности AI-агента.

## Yandex GPT API

### Endpoints

**Base URL**: `https://llm.api.cloud.yandex.net/foundationModels/v1/completion`

### Аутентификация

Yandex GPT использует API ключ для аутентификации:
- **Header**: `Authorization: Api-Key <API_KEY>`
- **Альтернатива**: IAM токен (для продвинутых сценариев)

### Формат запроса

```json
{
  "modelUri": "gpt://<folder_id>/yandexgpt-lite/latest",
  "completionOptions": {
    "stream": false,
    "temperature": 0.6,
    "maxTokens": 2000
  },
  "messages": [
    {
      "role": "system",
      "text": "Ты - помощник программиста"
    },
    {
      "role": "user",
      "text": "Объясни, что такое корутины в Kotlin"
    }
  ]
}
```

### Формат ответа

```json
{
  "result": {
    "alternatives": [
      {
        "message": {
          "role": "assistant",
          "text": "Корутины в Kotlin - это..."
        },
        "status": "ALTERNATIVE_STATUS_FINAL"
      }
    ],
    "usage": {
      "inputTextTokens": "28",
      "completionTokens": "243",
      "totalTokens": "271"
    },
    "modelVersion": "07.03.2024"
  }
}
```

## Реализация в плагине

### 1. Конфигурация

```kotlin
data class YandexGPTConfig(
    val apiKey: String,
    val folderId: String,
    val modelUri: String = "gpt://$folderId/yandexgpt-lite/latest",
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000,
    val timeout: Long = 30000 // 30 секунд
)
```

### 2. HTTP клиент

Рекомендуется использовать **Ktor Client** для асинхронных запросов:

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
}
```

### 3. Модели данных

```kotlin
@Serializable
data class YandexGPTRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<YandexMessage>
)

@Serializable
data class CompletionOptions(
    val stream: Boolean = false,
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000
)

@Serializable
data class YandexMessage(
    val role: String,
    val text: String
)

@Serializable
data class YandexGPTResponse(
    val result: Result
) {
    @Serializable
    data class Result(
        val alternatives: List<Alternative>,
        val usage: Usage,
        val modelVersion: String
    )
    
    @Serializable
    data class Alternative(
        val message: YandexMessage,
        val status: String
    )
    
    @Serializable
    data class Usage(
        val inputTextTokens: String,
        val completionTokens: String,
        val totalTokens: String
    )
}
```

### 4. Обработка ошибок

**Возможные ошибки**:
- `400 Bad Request` - неверный формат запроса
- `401 Unauthorized` - неверный API ключ
- `403 Forbidden` - недостаточно прав
- `429 Too Many Requests` - превышен rate limit
- `500 Internal Server Error` - ошибка на стороне сервера

**Стратегия обработки**:
```kotlin
sealed class LLMError {
    data class NetworkError(val message: String) : LLMError()
    data class AuthenticationError(val message: String) : LLMError()
    data class RateLimitError(val retryAfter: Long?) : LLMError()
    data class ServerError(val message: String) : LLMError()
    data class ValidationError(val message: String) : LLMError()
    data class UnknownError(val message: String) : LLMError()
}
```

### 5. Retry логика

```kotlin
suspend fun <T> retryWithExponentialBackoff(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (!shouldRetry(e)) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
    }
    return block() // последняя попытка
}
```

### 6. Rate Limiting

Yandex GPT имеет ограничения:
- **Запросы**: 20 запросов в секунду (для lite версии)
- **Токены**: зависит от тарифа

**Реализация**:
```kotlin
class RateLimiter(
    private val maxRequests: Int = 20,
    private val timeWindowMs: Long = 1000
) {
    private val requests = mutableListOf<Long>()
    
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        requests.removeAll { it < now - timeWindowMs }
        
        if (requests.size >= maxRequests) {
            val oldestRequest = requests.first()
            val delayMs = timeWindowMs - (now - oldestRequest)
            if (delayMs > 0) {
                delay(delayMs)
            }
        }
        
        requests.add(System.currentTimeMillis())
    }
}
```

## Безопасность

### 1. Хранение API ключа

Использовать `PasswordSafe` из IntelliJ Platform:

```kotlin
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials

object ApiKeyStorage {
    private const val SERVICE_NAME = "ru.marslab.ide.ride.yandexgpt"
    private const val KEY_NAME = "api_key"
    
    fun saveApiKey(apiKey: String) {
        val attributes = CredentialAttributes(SERVICE_NAME, KEY_NAME)
        val credentials = Credentials(KEY_NAME, apiKey)
        PasswordSafe.instance.set(attributes, credentials)
    }
    
    fun getApiKey(): String? {
        val attributes = CredentialAttributes(SERVICE_NAME, KEY_NAME)
        return PasswordSafe.instance.getPassword(attributes)
    }
    
    fun clearApiKey() {
        val attributes = CredentialAttributes(SERVICE_NAME, KEY_NAME)
        PasswordSafe.instance.set(attributes, null)
    }
}
```

### 2. Валидация API ключа

```kotlin
suspend fun validateApiKey(apiKey: String, folderId: String): Boolean {
    return try {
        val provider = YandexGPTProvider(
            YandexGPTConfig(apiKey, folderId)
        )
        val response = provider.sendRequest(
            "test",
            LLMParameters(maxTokens = 10)
        )
        response.success
    } catch (e: Exception) {
        false
    }
}
```

### 3. Логирование

**НЕ логировать**:
- API ключи
- Полные запросы пользователя (могут содержать чувствительные данные)

**Можно логировать**:
- Метаданные запросов (timestamp, размер)
- Коды ошибок
- Время ответа

```kotlin
import com.intellij.openapi.diagnostic.Logger

class YandexGPTProvider {
    private val logger = Logger.getInstance(YandexGPTProvider::class.java)
    
    suspend fun sendRequest(prompt: String, params: LLMParameters): LLMResponse {
        logger.info("Sending request to Yandex GPT, prompt length: ${prompt.length}")
        // ... код запроса
        logger.info("Received response, tokens used: ${response.tokensUsed}")
    }
}
```

## Оптимизация

### 1. Кэширование

Для повторяющихся запросов можно использовать кэш:

```kotlin
class CachedLLMProvider(
    private val delegate: LLMProvider,
    private val cacheSize: Int = 100
) : LLMProvider {
    private val cache = LRUCache<String, LLMResponse>(cacheSize)
    
    override suspend fun sendRequest(
        prompt: String,
        parameters: LLMParameters
    ): LLMResponse {
        val cacheKey = "$prompt:${parameters.hashCode()}"
        return cache.get(cacheKey) ?: run {
            val response = delegate.sendRequest(prompt, parameters)
            cache.put(cacheKey, response)
            response
        }
    }
}
```

### 2. Streaming (для будущих версий)

Yandex GPT поддерживает streaming для получения ответа по частям:

```kotlin
suspend fun streamRequest(
    prompt: String,
    onChunk: (String) -> Unit
) {
    // Установить stream: true в completionOptions
    // Обрабатывать SSE (Server-Sent Events)
}
```

## Тестирование

### 1. Mock провайдер для тестов

```kotlin
class MockLLMProvider : LLMProvider {
    var mockResponse: LLMResponse? = null
    var shouldFail: Boolean = false
    
    override suspend fun sendRequest(
        prompt: String,
        parameters: LLMParameters
    ): LLMResponse {
        if (shouldFail) {
            throw Exception("Mock error")
        }
        return mockResponse ?: LLMResponse(
            content = "Mock response",
            success = true,
            tokensUsed = 10
        )
    }
    
    override fun isAvailable(): Boolean = !shouldFail
    override fun getProviderName(): String = "Mock"
}
```

### 2. Интеграционные тесты

```kotlin
@Test
fun `test yandex gpt integration`() = runBlocking {
    val apiKey = System.getenv("YANDEX_GPT_API_KEY") ?: return@runBlocking
    val folderId = System.getenv("YANDEX_FOLDER_ID") ?: return@runBlocking
    
    val provider = YandexGPTProvider(
        YandexGPTConfig(apiKey, folderId)
    )
    
    val response = provider.sendRequest(
        "Привет!",
        LLMParameters(maxTokens = 50)
    )
    
    assertTrue(response.success)
    assertNotNull(response.content)
}
```

## Мониторинг и метрики

### Отслеживаемые метрики:
- Количество запросов
- Среднее время ответа
- Количество ошибок по типам
- Использование токенов
- Rate limit violations

```kotlin
class MetricsCollector {
    private val requestCount = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    private val totalResponseTime = AtomicLong(0)
    
    fun recordRequest(responseTimeMs: Long) {
        requestCount.incrementAndGet()
        totalResponseTime.addAndGet(responseTimeMs)
    }
    
    fun recordError() {
        errorCount.incrementAndGet()
    }
    
    fun getAverageResponseTime(): Double {
        val count = requestCount.get()
        return if (count > 0) {
            totalResponseTime.get().toDouble() / count
        } else 0.0
    }
}
```

## Ссылки

- [Yandex GPT API Documentation](https://cloud.yandex.ru/docs/yandexgpt/)
- [Yandex Cloud Authentication](https://cloud.yandex.ru/docs/iam/concepts/authorization/api-key)
- [Ktor Client Documentation](https://ktor.io/docs/client.html)
- [IntelliJ Platform SDK - PasswordSafe](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html)
