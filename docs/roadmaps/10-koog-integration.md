# 🚀 Роадмап: Интеграция библиотеки Koog

## 📊 Метаданные проекта

- **Статус**: 📋 Запланировано (Требует оценки)
- **Версия**: 1.0.0
- **Дата создания**: 2025-10-20
- **Приоритет**: Средний
- **Оценка времени**: 2-3 недели

---

## 🎯 Цели интеграции

### Основная задача
Оценить целесообразность и реализовать интеграцию библиотеки **Koog** (официальный Kotlin framework от JetBrains для построения AI агентов) в проект Ride.

### Что такое Koog?

**Koog** - это Kotlin-based framework от JetBrains для построения AI агентов с поддержкой:
- Multiplatform development (JVM, JS, WasmJS, Android, iOS)
- Reliability и fault-tolerance (retries, persistence)
- Intelligent history compression (оптимизация токенов)
- Enterprise-ready integrations (Spring Boot, Ktor)
- Observability (OpenTelemetry, Langfuse, W&B Weave)
- LLM switching без потери истории
- Model Context Protocol (MCP) integration
- Knowledge retrieval и memory
- Streaming API
- Graph workflows
- Custom tools

### Официальные ресурсы
- GitHub: https://github.com/JetBrains/koog
- Документация: https://docs.koog.ai/
- Блог: https://blog.jetbrains.com/kotlin/2025/09/the-kotlin-ai-stack-build-ai-agents-with-koog-code-smarter-with-junie-and-more/

---

## 🔍 Анализ текущей архитектуры Ride

### Существующая система агентов

```kotlin
// Текущая архитектура
interface Agent {
    val capabilities: AgentCapabilities
    suspend fun ask(req: AgentRequest): AgentResponse
    fun start(req: AgentRequest): Flow<AgentEvent>?
    fun updateSettings(settings: AgentSettings)
    fun dispose()
}

// Реализации
- ChatAgent (основной агент для чата)
- TerminalAgent (выполнение команд)
- SummarizerAgent (сжатие истории)
- AgentOrchestrator (оркестрация агентов)
```

### Что уже реализовано в Ride

✅ **Уже есть:**
1. Модульная архитектура с DI через интерфейсы
2. Мультипровайдерная поддержка (Yandex GPT, HuggingFace)
3. Управление токенами и автосжатие истории
4. Анализ неопределенности (uncertainty analysis)
5. MCP интеграция (stdio + HTTP)
6. Форматирование ответов (JSON/XML/TEXT)
7. Асинхронная обработка через Kotlin Coroutines
8. Persistence через IntelliJ Platform
9. Форматированный вывод (terminal, code blocks, tools)
10. Agent Orchestrator для сложных задач

### Что может дать Koog

🎁 **Потенциальные преимущества:**
1. **Готовые решения** для типовых задач агентов
2. **Graph workflows** - визуальное проектирование логики агентов
3. **Observability** - встроенная телеметрия и мониторинг
4. **Fault tolerance** - автоматические retry и восстановление состояния
5. **Advanced history compression** - более эффективное сжатие контекста
6. **Knowledge retrieval** - векторные embeddings и memory
7. **Streaming API** - потоковая обработка ответов
8. **Parallel tool calls** - параллельный вызов инструментов
9. **Enterprise integrations** - готовая интеграция с Spring Boot, Ktor
10. **Community support** - поддержка от JetBrains и сообщества

---

## ⚖️ Сравнительный анализ

### Текущая реализация vs Koog

| Функция | Ride (текущее) | Koog | Преимущество |
|---------|----------------|------|--------------|
| **Базовые агенты** | ✅ Реализовано | ✅ Есть | = |
| **LLM провайдеры** | Yandex, HuggingFace | OpenAI, Anthropic, Google, DeepSeek, Ollama, Bedrock | Koog (больше выбор) |
| **Streaming** | ❌ Нет | ✅ Есть | Koog |
| **History compression** | ✅ Базовое | ✅ Advanced | Koog (лучше) |
| **MCP integration** | ✅ Есть | ✅ Есть | = |
| **Graph workflows** | ❌ Нет | ✅ Есть | Koog |
| **Observability** | ❌ Базовое | ✅ OpenTelemetry | Koog |
| **Fault tolerance** | ❌ Нет | ✅ Retries + persistence | Koog |
| **Knowledge retrieval** | ❌ Нет | ✅ Vector embeddings | Koog |
| **Parallel tools** | ❌ Нет | ✅ Есть | Koog |
| **IntelliJ integration** | ✅ Нативная | ⚠️ Требует адаптации | Ride |
| **Кастомизация** | ✅ Полная | ⚠️ Ограничена фреймворком | Ride |
| **Размер зависимостей** | Минимальный | Большой (Ktor + др.) | Ride |
| **Learning curve** | Низкая | Средняя | Ride |

### Ключевые выводы

✅ **Koog даст:**
- Streaming API для real-time ответов
- Продвинутое сжатие истории
- Fault tolerance и retry логику
- Graph workflows для сложных сценариев
- Observability из коробки
- Knowledge retrieval и memory
- Поддержку большего количества LLM провайдеров

⚠️ **Потенциальные проблемы:**
- Увеличение размера плагина (Ktor + зависимости)
- Конфликт с IntelliJ Platform (корутины, HTTP клиенты)
- Необходимость адаптации под IntelliJ API
- Потеря гибкости кастомизации
- Сложность миграции существующего кода

---

## 🎯 Стратегия интеграции

### Вариант 1: Полная замена (❌ Не рекомендуется)

Заменить всю текущую систему агентов на Koog.

**Плюсы:**
- Максимальное использование возможностей Koog
- Меньше кастомного кода

**Минусы:**
- Высокий риск breaking changes
- Потеря существующей функциональности
- Большой объем работы по миграции
- Возможные конфликты с IntelliJ Platform

### Вариант 2: Гибридный подход (✅ Рекомендуется)

Использовать Koog для новых агентов, сохранив существующую архитектуру.

**Плюсы:**
- Постепенная миграция без рисков
- Возможность сравнить производительность
- Сохранение работающего функционала
- Гибкость выбора инструментов

**Минусы:**
- Поддержка двух систем одновременно
- Увеличение сложности кодовой базы

### Вариант 3: Адаптер паттерн (✅ Рекомендуется)

Создать адаптер между Koog и текущей системой агентов.

**Плюсы:**
- Минимальные изменения в существующем коде
- Возможность использовать лучшее из обоих миров
- Легкая замена в будущем

**Минусы:**
- Дополнительный слой абстракции
- Некоторый overhead

---

## 📋 Задачи (Checklist)

### Phase 1: Исследование и прототипирование
- [ ] Изучить документацию Koog подробно
- [ ] Создать proof-of-concept интеграции
- [ ] Протестировать совместимость с IntelliJ Platform
- [ ] Оценить размер зависимостей и влияние на плагин
- [ ] Проверить конфликты с существующими библиотеками
- [ ] Измерить производительность vs текущая реализация

### Phase 2: Архитектурное проектирование
- [ ] Спроектировать адаптер между Koog и Agent интерфейсом
- [ ] Определить, какие агенты мигрировать на Koog
- [ ] Разработать стратегию миграции данных
- [ ] Спроектировать систему конфигурации
- [ ] Определить точки интеграции с IntelliJ API

### Phase 3: Базовая интеграция
- [ ] Добавить Koog зависимости в build.gradle.kts
- [ ] Создать KoogAgentAdapter
- [ ] Реализовать базовый Koog-based агент
- [ ] Настроить LLM провайдеры для Koog
- [ ] Интегрировать с существующей системой настроек

### Phase 4: Продвинутые функции
- [ ] Реализовать streaming через Koog
- [ ] Настроить observability (OpenTelemetry)
- [ ] Добавить graph workflows для сложных задач
- [ ] Интегрировать knowledge retrieval
- [ ] Настроить fault tolerance

### Phase 5: Миграция агентов
- [ ] Мигрировать CodeAnalysisAgent на Koog (если целесообразно)
- [ ] Оценить миграцию ChatAgent
- [ ] Сравнить производительность старых и новых агентов
- [ ] Провести A/B тестирование

### Phase 6: Тестирование и оптимизация
- [ ] Написать unit-тесты для Koog интеграции
- [ ] Провести интеграционное тестирование
- [ ] Оптимизировать использование памяти
- [ ] Провести нагрузочное тестирование
- [ ] Измерить влияние на размер плагина

### Phase 7: Документация
- [ ] Документировать архитектуру интеграции
- [ ] Создать migration guide
- [ ] Обновить API документацию
- [ ] Добавить примеры использования Koog агентов

---

## 🏗️ Архитектура интеграции (Адаптер паттерн)

```kotlin
// Адаптер между Koog и Ride Agent
class KoogAgentAdapter(
    private val koogAgent: AIAgent,
    private val config: KoogConfig
) : Agent {
    
    override val capabilities: AgentCapabilities
        get() = AgentCapabilities(
            stateful = true,
            streaming = true, // Koog поддерживает streaming!
            reasoning = true,
            tools = koogAgent.availableTools.map { it.name }.toSet(),
            systemPrompt = config.systemPrompt,
            responseRules = config.responseRules
        )
    
    override suspend fun ask(req: AgentRequest): AgentResponse {
        try {
            // Конвертируем AgentRequest в Koog формат
            val koogRequest = convertToKoogRequest(req)
            
            // Вызываем Koog агента
            val koogResult = koogAgent.run(koogRequest)
            
            // Конвертируем результат обратно
            return convertToAgentResponse(koogResult)
            
        } catch (e: Exception) {
            logger.error("Koog agent error", e)
            return AgentResponse.error(
                error = e.message ?: "Unknown error",
                content = "Ошибка при выполнении Koog агента"
            )
        }
    }
    
    override fun start(req: AgentRequest): Flow<AgentEvent>? {
        // Используем streaming API от Koog
        return flow {
            koogAgent.stream(convertToKoogRequest(req)).collect { chunk ->
                emit(AgentEvent.ContentChunk(chunk.content))
            }
        }
    }
    
    override fun updateSettings(settings: AgentSettings) {
        // Обновляем конфигурацию Koog агента
        koogAgent.updateConfiguration(convertToKoogSettings(settings))
    }
    
    override fun dispose() {
        koogAgent.dispose()
    }
    
    private fun convertToKoogRequest(req: AgentRequest): String {
        // Конвертация запроса
        return buildString {
            append(req.request)
            if (req.context.history.isNotEmpty()) {
                append("\n\nКонтекст:\n")
                req.context.history.takeLast(5).forEach { msg ->
                    append("${msg.role}: ${msg.content}\n")
                }
            }
        }
    }
    
    private fun convertToAgentResponse(result: String): AgentResponse {
        return AgentResponse.success(
            content = result,
            isFinal = true,
            uncertainty = 0.0,
            metadata = mapOf(
                "provider" to "koog",
                "streaming" to false
            )
        )
    }
}

// Фабрика для создания Koog агентов
object KoogAgentFactory {
    
    fun createKoogChatAgent(config: KoogConfig): Agent {
        val koogAgent = AIAgent(
            executor = createExecutor(config),
            systemPrompt = config.systemPrompt,
            llmModel = config.model
        )
        
        return KoogAgentAdapter(koogAgent, config)
    }
    
    fun createKoogCodeAnalysisAgent(config: KoogConfig): Agent {
        // Специализированный агент для анализа кода с graph workflow
        val koogAgent = AIAgent(
            executor = createExecutor(config),
            systemPrompt = CODE_ANALYSIS_PROMPT,
            llmModel = config.model,
            workflow = createCodeAnalysisWorkflow()
        )
        
        return KoogAgentAdapter(koogAgent, config)
    }
    
    private fun createExecutor(config: KoogConfig): LLMExecutor {
        return when (config.provider) {
            "openai" -> simpleOpenAIExecutor(config.apiKey)
            "anthropic" -> simpleAnthropicExecutor(config.apiKey)
            "google" -> simpleGoogleExecutor(config.apiKey)
            else -> throw IllegalArgumentException("Unsupported provider: ${config.provider}")
        }
    }
    
    private fun createCodeAnalysisWorkflow(): Workflow {
        // Graph workflow для анализа кода
        return workflow {
            step("scan_project") {
                // Сканирование проекта
            }
            step("analyze_files") {
                // Анализ файлов
            }
            step("generate_report") {
                // Генерация отчета
            }
        }
    }
}

data class KoogConfig(
    val provider: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val responseRules: List<String> = emptyList(),
    val enableStreaming: Boolean = true,
    val enableObservability: Boolean = false
)
```

---

## 🔧 Технические детали

### Зависимости

```kotlin
// build.gradle.kts
dependencies {
    // Koog core
    implementation("ai.koog:koog-core:0.5.0")
    
    // LLM executors (выбрать нужные)
    implementation("ai.koog:koog-openai:0.5.0")
    implementation("ai.koog:koog-anthropic:0.5.0")
    implementation("ai.koog:koog-google:0.5.0")
    
    // Опционально: observability
    implementation("ai.koog:koog-observability-opentelemetry:0.5.0")
    
    // Опционально: knowledge retrieval
    implementation("ai.koog:koog-knowledge:0.5.0")
    
    // Ktor (требуется для Koog)
    implementation("io.ktor:ktor-client-core:2.3.0")
    implementation("io.ktor:ktor-client-cio:2.3.0")
}
```

### Конфликты с IntelliJ Platform

⚠️ **Потенциальные проблемы:**

1. **Ktor vs IntelliJ HTTP Client**
   - Koog использует Ktor
   - IntelliJ Platform имеет свой HTTP клиент
   - Возможны конфликты версий корутин

2. **Корутины**
   - Koog использует kotlinx.coroutines
   - IntelliJ Platform тоже использует корутины
   - Нужно убедиться в совместимости версий

3. **Classloader issues**
   - Плагины IntelliJ используют отдельные classloaders
   - Могут быть проблемы с загрузкой Koog классов

**Решения:**

```kotlin
// Использовать Ktor только для Koog, изолировать от остального кода
// Явно указать версии корутин
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
}

// Настроить classloader isolation
intellijPlatform {
    pluginConfiguration {
        // Изолировать Koog зависимости
    }
}
```

---

## 📊 Оценка влияния на проект

### Размер плагина

| Компонент | Размер (примерно) |
|-----------|-------------------|
| Текущий плагин | ~5 MB |
| Koog core | ~2 MB |
| Ktor client | ~3 MB |
| LLM executors | ~1 MB каждый |
| **Итого с Koog** | **~12-15 MB** |

**Вывод**: Размер плагина увеличится в 2-3 раза.

### Производительность

**Плюсы:**
- Оптимизированное сжатие истории
- Параллельные вызовы инструментов
- Streaming для faster perceived performance

**Минусы:**
- Дополнительный overhead от адаптера
- Больше памяти для Koog runtime

### Сложность поддержки

**Плюсы:**
- Меньше кастомного кода для типовых задач
- Поддержка от JetBrains
- Активное сообщество

**Минусы:**
- Зависимость от внешней библиотеки
- Необходимость следить за обновлениями Koog
- Сложность отладки проблем в Koog

---

## 🎯 Рекомендации

### ✅ Использовать Koog для:

1. **Новых агентов** с продвинутой логикой
   - CodeAnalysisAgent с graph workflow
   - Knowledge retrieval агенты
   - Агенты с parallel tool calls

2. **Streaming функциональности**
   - Real-time ответы в чате
   - Прогрессивная генерация кода

3. **Observability**
   - Мониторинг производительности агентов
   - Отладка сложных workflow

### ❌ НЕ использовать Koog для:

1. **Простых агентов**
   - ChatAgent (текущая реализация достаточна)
   - TerminalAgent (не требует LLM)

2. **Критичных для производительности частей**
   - Где важен минимальный overhead

3. **Функций, тесно интегрированных с IntelliJ API**
   - Где нужна нативная интеграция

### 🎯 Оптимальная стратегия

**Гибридный подход с адаптером:**

1. Сохранить текущую архитектуру агентов
2. Добавить Koog как опциональную зависимость
3. Создать KoogAgentAdapter для совместимости
4. Использовать Koog для новых сложных агентов
5. Постепенно мигрировать агенты по необходимости

**Приоритет миграции:**
1. ✅ CodeAnalysisAgent (новый) → Koog (graph workflow)
2. ⚠️ ChatAgent → Оставить текущую реализацию
3. ⚠️ SummarizerAgent → Возможно Koog (лучшее сжатие)
4. ❌ TerminalAgent → Оставить текущую реализацию

---

## 📈 Метрики успеха интеграции

- ✅ Koog агенты работают без конфликтов с IntelliJ Platform
- ✅ Размер плагина увеличился не более чем на 50%
- ✅ Streaming работает корректно в UI
- ✅ Производительность не хуже текущей реализации
- ✅ Observability данные доступны и полезны
- ✅ Все существующие тесты проходят
- ✅ Документация обновлена

---

## 🔮 Будущие возможности с Koog

После успешной интеграции:

1. **Advanced workflows**: Сложные multi-step агенты
2. **Knowledge base**: RAG для работы с документацией
3. **Multi-agent systems**: Координация нескольких агентов
4. **A2A protocol**: Взаимодействие агентов между собой
5. **Custom tools ecosystem**: Marketplace инструментов для агентов

---

## 📚 Связанные документы

- [Code Analysis Agent Roadmap](./09-code-analysis-agent.md)
- [Agent Architecture](../architecture/overview.md)
- [Koog Documentation](https://docs.koog.ai/)
- [Koog GitHub](https://github.com/JetBrains/koog)

---

## 💡 Заключение

### Упростит ли Koog разработку плагина?

**Да, но с оговорками:**

✅ **Упростит:**
- Реализацию сложных workflow агентов
- Добавление streaming функциональности
- Observability и мониторинг
- Fault tolerance и retry логику
- Knowledge retrieval и memory

⚠️ **Усложнит:**
- Размер и сложность зависимостей
- Интеграцию с IntelliJ Platform
- Отладку проблем
- Поддержку двух систем агентов

### Итоговая рекомендация

**Использовать гибридный подход:**
1. Сохранить текущую архитектуру для простых агентов
2. Добавить Koog для новых сложных агентов (CodeAnalysisAgent)
3. Использовать адаптер паттерн для совместимости
4. Постепенно оценивать целесообразность миграции

**Начать с:**
- Proof-of-concept интеграции
- Реализации CodeAnalysisAgent на Koog
- Сравнения производительности и удобства

**Решение о полной миграции принимать после:**
- Успешного PoC
- Оценки реальных преимуществ
- Измерения влияния на производительность

---

**Дата создания**: 2025-10-20  
**Автор**: AI Assistant  
**Статус**: Требует обсуждения и утверждения
