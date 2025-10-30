# ROADMAP: Реранкинг/фильтрация результатов Retrieval (THRESHOLD)

Данный документ описывает добавление второго этапа после первичного поиска по эмбеддингам: фильтрация/реранкинг на основе порога схожести (THRESHOLD) с настраиваемыми параметрами. Цель — повысить точность RAG-обогащения чата за счёт отсечения нерелевантных результатов и управления topK.

## Цель
- Добавить конфигурируемый порог релевантности и управляемые параметры выборки.
- Включить второй этап после `EmbeddingDatabaseService.findSimilarEmbeddings()` — фильтрацию по порогу (THRESHOLD) с сортировкой по убыванию похожести.
- Обеспечить безопасную деградацию и обратную совместимость.

## Область и критерии приёмки
- В настройках плагина доступны параметры RAG:
  - `ragTopK`, `ragCandidateK`, `ragSimilarityThreshold`, `ragRerankerStrategy` (значение по умолчанию THRESHOLD).
- `RagEnrichmentService` использует параметры из настроек, удалены жёстко заданные константы `topK=5` и `threshold=0.25`.
- По умолчанию поведение соответствует текущему roadmap: `topK=5`, `threshold≈0.25`.
- Логи содержат стратегию реранкинга, topK, candidateK, threshold и итоговое число выбранных чанков.
- Юнит-тесты покрывают фильтрацию порогом и чтение настроек. Интеграционные тесты не ломаются.

## Модель/провайдеры
- Эмбеддинги: Ollama, модель доступна локально: `nomic-embed-text:latest` (семейство `nomic-bert`).
- Кросс-энкодеры и MMR — вне области данной итерации (см. Out of scope).

## Значения по умолчанию
- `ragTopK = 5`
- `ragCandidateK = 30`
- `ragSimilarityThreshold = 0.25f`
- `ragRerankerStrategy = THRESHOLD`
- Ограничение контекста — существующий `maxContextTokens` из настроек

## Изменения в коде

- Пакеты и файлы (существующие для интеграции):
  - `src/main/kotlin/ru/marslab/ide/ride/service/rag/RagEnrichmentService.kt`
  - `src/main/kotlin/ru/marslab/ide/ride/service/embedding/EmbeddingDatabaseService.kt`
  - `src/main/kotlin/ru/marslab/ide/ride/settings/PluginSettingsState.kt`
  - `src/main/kotlin/ru/marslab/ide/ride/settings/PluginSettings.kt`
  - `src/main/kotlin/ru/marslab/ide/ride/settings/SettingsConfigurable.kt`

- Новые/обновлённые параметры настроек (`PluginSettingsState`, UI в `SettingsConfigurable`):
  - `ragTopK: Int = 5`
  - `ragCandidateK: Int = 30` (кандидаты первичного поиска до реранкинга)
  - `ragSimilarityThreshold: Float = 0.25f`
  - `ragRerankerStrategy: String = "THRESHOLD"`
  - Валидация: `candidateK >= topK`, `0 <= threshold <= 1`.

- Изменения в `RagEnrichmentService`:
  - Читать `topK`, `candidateK`, `threshold`, `strategy` из `PluginSettings`.
  - На этапе поиска запрашивать `candidateK` через `findSimilarChunks()`.
  - Применять фильтрацию по порогу и сортировку по similarity, затем усечение до `topK`.
  - Исключить жёсткие константы `defaultTopK`, `defaultSimilarityThreshold`.
  - Логировать параметры и результат, сохранять поведение деградации.

- БД слой: без изменений. `EmbeddingDatabaseService.findSimilarEmbeddings()` уже возвращает пары `(chunkId, similarity)`; дополнительный доступ к BLOB эмбеддингов для THRESHOLD не требуется.

## Чек-лист задач

- [x] Обновить `PluginSettingsState` и `PluginSettings` (геттеры/сеттеры) новыми параметрами RAG
- [x] Обновить UI `SettingsConfigurable`: поля topK, candidateK, threshold, стратегия (select), валидации
- [x] Интегрировать параметры в `RagEnrichmentService`:
  - [x] Заменить константы на чтение из настроек
  - [x] Использовать `candidateK` на первом этапе поиска
  - [x] Применить фильтр THRESHOLD и урезать до `topK`
  - [x] Расширить логи и метаданные результата (strategy, threshold, topK, candidateK)
- [ ] Тесты:
  - [ ] Юнит: фильтрация по порогу, корректная сортировка/урезание до `topK`
  - [ ] Юнит: чтение настроек, валидация (candidateK >= topK)
  - [ ] Интеграция: пустая выдача/недоступность БД → корректная деградация
- [x] Документация: обновить `docs/roadmaps/19_rag-enrichment-chat.md` и README по новым настройкам

## Риски и смягчение
- Большой `candidateK` может замедлять первичный поиск — по умолчанию 30, документировать влияние и лимитировать 5–100.
- Неправильные значения порога приводят к пустой выдаче или шуму — подсказки в UI и дефолт 0.25.
- Совместимость: при отсутствии новых полей в сохранённом стейте — дефолты.

## Out of scope (следующие итерации)
- Стратегия `MMR` (Maximal Marginal Relevance)
- `CROSS_ENCODER`/"re-ranker" через внешнюю модель
- Хранение и доступ к эмбеддингам чанков на втором этапе

## План коммитов
- Коммит 1: настройки и UI
- Коммит 2: интеграция в `RagEnrichmentService`
- Коммит 3: тесты
- Коммит 4: документация

## Rollback план
- Флаг `enableRagEnrichment` уже существует: при проблемах — отключение RAG.
- Вернуться к текущему поведению можно установкой `ragRerankerStrategy=THRESHOLD`, `candidateK=topK`, `threshold=0.25`.
