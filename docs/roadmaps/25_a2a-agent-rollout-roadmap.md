# ROADMAP: Внедрение A2A во все агенты (Agent Rollout)

## Краткое описание
Данный документ описывает поэтапный план внедрения A2A (Agent-to-Agent) протокола во все агенты плагина Ride, включая инфраструктуру, покрытие отсутствующих A2A-агентов, сквозную передачу данных между шагами, тестирование и выпуск. Все задачи оформлены как чекбоксы и сгруппированы по фазам для удобного трекинга выполнения.

## Область охвата
- Инфраструктура A2A (MessageBus, события, схемы сообщений)
- Все ToolAgents (существующие A2A + отсутствующие A2A-варианты)
- Оркестратор `EnhancedAgentOrchestratorA2A`
- Интеграция с UI (`ChatService`) для событий и прогресса
- Документация и фичефлаги

## Phased Checklist (для утверждения)

### Phase 0: Стабилизация инфраструктуры A2A
- [x] Исправить `A2AAgentAdapter`: параметризовать `agentType` (убрать жёсткое `AgentType.PROJECT_SCANNER`), пробросить `supportedMessageTypes`/`publishedEventTypes` per-агент
- [x] Стандартизировать общую схему: `TOOL_EXECUTION_REQUEST` / `TOOL_EXECUTION_RESULT` через `MessagePayload.CustomPayload { stepId, description, agentType, input, dependencies }`
- [x] Гарантировать `planId` в `metadata` всех событий, публикуемых `EnhancedAgentOrchestratorA2A`
- [x] Обновить `docs/features/a2a-protocol.md` c новыми типами сообщений и правилами

### Phase 1: Покрытие отсутствующих A2A-агентов
- [ ] `ArchitectureToolAgent` → `A2AArchitectureToolAgent` (или адаптер): `ARCHITECTURE_ANALYSIS_REQUEST`
- [ ] `LLMCodeReviewToolAgent` → `A2ALLMReviewToolAgent` (или адаптер): `LLM_REVIEW_REQUEST`
- [ ] `EmbeddingIndexerToolAgent` → `A2AEmbeddingIndexerToolAgent` (или адаптер): `EMBEDDING_INDEX_REQUEST`
- [ ] `CodeChunkerToolAgent` → `A2ACodeChunkerToolAgent` (или адаптер): `CODE_CHUNK_REQUEST`
- [ ] `OpenSourceFileToolAgent` → `A2AOpenSourceFileToolAgent` (или адаптер): `OPEN_FILE_REQUEST`
- [ ] `UserInteractionAgent` → `A2AUserInteractionAgent`: `USER_INPUT_REQUEST` с ожиданием пользовательского ввода
- [x] Универсальный путь через адаптер: добавить обработчик `TOOL_EXECUTION_REQUEST` → парсинг в `ToolPlanStep` → `executeStep()`

### Phase 2: Сквозная передача данных между шагами
- [x] Реализовать `enrichStepInput(step, prevResults)` в `EnhancedAgentOrchestratorA2A.executePlanWithA2A()`
- [x] Контракты данных: `PROJECT_SCANNER → files`; потребители — `BUG_DETECTION`, `CODE_QUALITY`, `LLM_REVIEW`, `ARCHITECTURE_ANALYSIS` (зафиксированы в features)
- [x] Расширить `mapResponseToResult()` для обработки `TOOL_EXECUTION_RESULT`

### Phase 3: Тестирование и верификация
- [ ] Обновить/добавить `A2AAgentsSmokeTest` для всех новых `messageType`
- [ ] Интеграционные тесты плана: зависимости, ретраи, таймауты, корректность протекания данных
- [ ] Нагрузочные тесты MessageBus: параллельные шаги, задержки, throughput/latency
- [x] UI: фильтрация событий по `planId` в `ChatService.handleA2AUiEvent()` и корректное отображение прогресса

### Phase 4: Документация и feature flags
- [x] Обновить данный ROADMAP и `docs/roadmaps/24_a2a-protocol-integration.md` статусами задач
- [ ] Включить фичефлаги в `A2AConfig/A2AConfigUtil` для зрелых агентов; незрелые — за флагом
- [ ] Обновить `docs/README.md` и гайды по созданию A2A-агентов

## Acceptance per Phase
- [ ] Phase 0: изменения мержатся без регрессий; схемы зафиксированы в документации
- [ ] Phase 1: все перечисленные агенты доступны в A2A-режиме; адаптеры корректно исполняют `TOOL_EXECUTION_REQUEST`
- [ ] Phase 2: данные корректно протекают между шагами; отчёт формируется из агрегированных результатов
- [ ] Phase 3: тесты зелёные; целевые метрики latency/throughput выдерживаются; UI показывает реальный прогресс
- [ ] Phase 4: документация актуальна; включены фичефлаги и стратегия постепенного включения

## Риски и смягчения
- **[Performance routing]**: нагрузочное тестирование, оптимизация in-memory routing
- **[Adapter correctness]**: интеграционные тесты для каждого агента; пошаговый rollout с фичефлагами
- **[Data contracts]**: фиксированные схемы payload'ов и конвертеры в одном месте

## Таймлайн (ориентировочно)
- Phase 0: 0.5–1 неделя
- Phase 1: 1–1.5 недели
- Phase 2: 1 неделя
- Phase 3: 1 неделя
- Phase 4: 0.5 недели

## Примечания
- Все файлы ROADMAP хранятся в `docs/roadmaps/`
- Обновления документации отражаются в `docs/README.md` и соответствующих гайдах
