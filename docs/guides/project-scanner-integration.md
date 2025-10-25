# Интеграция с ProjectScannerToolAgent

## Обзор

Этот гайд описывает, как другим агентам (оркестратор, анализ кода, документация) потреблять JSON-ответ `ProjectScannerToolAgent` с поддержкой пакетной выдачи и инкрементальных обновлений.

## Формат ответа

- Поле `format = "JSON"`
- Поле `json` содержит полезную нагрузку:

```json
{
  "project": {"path": "string", "type": "string"},
  "batch": {"page": 1, "batch_size": 500, "total": 1234, "has_more": true},
  "files": ["path/to/file1", "path/to/file2"],
  "stats": {"total_files": 1234, "language_distribution": {"kt": 800}},
  "directories_total": 150,
  "tree_included": true,
  "directory_tree": {"path": "/...", "children": []},
  "delta": {"since_ts": 0, "changed_files": ["path/to/file1"]}
}
```

Подробнее: `docs/api/response-formats.md`.

## Пагинация

- Параметры входа: `page` (Int, default 1), `batch_size` (Int, default 500)
- Алгоритм потребления:
  1. Запросить `page = 1` → получить `directory_tree` и первую порцию `files`.
  2. Пока `batch.has_more = true`, увеличивать `page` и запрашивать следующие порции.
  3. Для экономии трафика дерево приходит только на первой странице (`tree_included = true`).

## Инкрементальные обновления (delta)

- Параметр входа: `since_ts` (Long, UNIX ms)
- Рекомендация:
  - Сохраняйте `scan_timestamp` из `stats` (или используйте собственную метку времени вызова).
  - Повторные запросы выполняйте с `since_ts = <предыдущий timestamp>`.
  - Список измененных файлов доступен в `json.delta.changed_files`.

## Рекомендации для потребителей

- **Оркестратор**: используйте `batch.has_more` для построения пайплайна последовательных вызовов. Сохраняйте `since_ts` для инкрементальных прогонов.
- **Code Analysis Agent**: фильтруйте `files` по языкам/маскам из `stats.language_distribution`, запрашивайте дополнительные страницы при необходимости.
- **Docs Agent**: извлекайте ключевые файлы из `json.stats`/`project_structure` (см. поля `key_files`, если доступны в выдаче агента).

## Обратная совместимость

Поля верхнего уровня остаются для старых потребителей: `files`, `directory_tree`, `project_type`, `file_statistics`, `total_files`, `total_directories`, `from_cache`, `scan_time_ms`.

## Пример последовательности запросов

1) Полное сканирование пачками по 500 файлов:

```json
{"page": 1, "batch_size": 500}
```

Далее `page = 2, 3, ...` пока `has_more = false`.

2) Инкремент после полного сканирования:

```json
{"page": 1, "batch_size": 500, "since_ts": 1698000000000}
```

Проверьте `json.delta.changed_files`, при необходимости обработайте только изменившиеся файлы.
