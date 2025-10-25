# 📚 Документация Ride

Добро пожаловать в документацию AI-ассистента Ride для IntelliJ IDEA!

## 🗂️ Навигация по документации

### 📖 Основная документация

- [**README**](../README.md) - Главная страница проекта с быстрым стартом
- [**CHANGELOG**](CHANGELOG.md) - История изменений проекта
- [**BACKLOG**](BACKLOG.md) - Планируемые задачи и фичи

### 🏗️ Архитектура

- [**Обзор архитектуры**](architecture/overview.md) - Общая структура проекта
- [**Архитектурные решения**](architecture.md) - Детальное описание компонентов
- [**Краткое описание**](ARCHITECTURE_SUMMARY.md) - Краткая справка по архитектуре
- [**Структура проекта**](project-structure.md) - Организация файлов и пакетов
- [**Диаграммы последовательности**](sequence-diagrams.md) - UML диаграммы взаимодействия

### ✨ Возможности (Features)

- [**Agent Orchestrator**](features/agent-orchestrator.md) - Система двух агентов (PlannerAgent + ExecutorAgent)
- [**Анализ неопределенности**](features/FEATURE_UNCERTAINTY_ANALYSIS.md) - Интеллектуальная система уточняющих вопросов
- [**Управление токенами**](features/token-management.md) - Подсчёт токенов и автоматическое сжатие истории
- [**Роудмап анализа неопределенности**](features/uncertainty-analysis-roadmap.md) - История разработки фичи
- [**Обзор фич**](features/README.md) - Список всех возможностей

### 📘 Руководства (Guides)

- [**Project Scanner Tool Agent**](guides/project-scanner-usage-guide.md) - Полное руководство по использованию сканера проектов ⭐ Новое
- [**Примеры конфигурации Project Scanner**](guides/project-scanner-configuration-examples.md) - Готовые конфигурации для различных сценариев ⭐ Новое
- [**Лучшие практики Project Scanner**](guides/project-scanner-best-practices.md) - Рекомендации и паттерны использования ⭐ Новое
- [**Настройка JCEF в Android Studio**](guides/android-studio-jcef.md) - Включение подсветки кода
- [**Тестирование Orchestrator**](guides/testing-orchestrator.md) - Как тестировать систему агентов
- [**Публикация плагина**](PUBLISHING_GUIDE.md) - Инструкция по публикации в JetBrains Marketplace
- [**Примеры использования**](USAGE_EXAMPLES.md) - Практические примеры работы с плагином

### 🔌 API

- [**Project Scanner API для агентов**](api/project-scanner-api-for-agents.md) - API для интеграции с другими агентами ⭐ Новое
- [**Интеграция с API**](api-integration.md) - Работа с LLM провайдерами
- [**Форматы ответов**](api/response-formats.md) - JSON/XML/TEXT форматирование
- [**Пример ChatAgent**](chat-agent-example.md) - Как использовать ChatAgent

### 🗺️ Роудмапы (Roadmaps)

- [**Обзор роудмапов**](roadmaps/README.md) - Планы развития проекта

**Завершенные:**

- [**Фаза 1: Реализация чата**](roadmaps/01-chat-implementation-phase1.md)
- [**Фаза 2: Форматы ответов**](roadmaps/02-response-format-feature.md)
- [**Фаза 3: Рефакторинг сообщений**](roadmaps/03-llm-messages-refactor.md)
- [**Фаза 4: Анализ неопределенности**](roadmaps/04-uncertainty-analysis-feature.md)
- [**Фаза 6: Интеграция MCP серверов**](roadmaps/06-mcp-server-integration.md)
- [**Фаза 7: Оркестрация агентов**](roadmaps/07-agent-orchestration.md)
- [**Фаза 8: Форматированный вывод агентов**](roadmaps/08-agent-formatted-output.md)

**Запланированные:**
- [**Фаза 9: Агент анализа кода**](roadmaps/09-code-analysis-agent.md) ⭐ Новое
- [**Фаза 10: Интеграция Koog**](roadmaps/10-koog-integration.md) ⭐ Новое
- [**Фаза 11: Расширенный агент-оркестратор**](roadmaps/11-enhanced-orchestrator.md) ⭐ Новое
- [**Фаза 16: Улучшенный Project Scanner**](roadmaps/16-enhanced-project-scanner.md) ⭐ Новое
  - [📁 Документация по разработке](roadmaps/orchestrator-development/)
  - [Детальный план](roadmaps/orchestrator-development/enhanced-orchestrator-development.md)
  - [Техническая спецификация](roadmaps/orchestrator-development/enhanced-orchestrator-technical.md)
  - [Краткое резюме](roadmaps/orchestrator-development/ORCHESTRATOR_SUMMARY.md)

## 🎯 Быстрые ссылки

### Для пользователей
- [Установка и настройка](../README.md#-установка)
- [Использование чата](../README.md#-использование)
- [Команда /plan](features/agent-orchestrator.md#через-ui-команда-plan)
- [Настройка токенов](features/token-management.md#настройки)

### Для разработчиков

- [Структура проекта](project-structure.md)
- [Добавление нового LLM провайдера](../README.md#добавление-нового-llm-провайдера)
- [Архитектура агентов](architecture/overview.md)
- [Тестирование](../README.md#-тестирование)

### Для контрибьюторов

- [Вклад в проект](../README.md#-вклад-в-проект)
- [Бэклог задач](BACKLOG.md)
- [Публикация плагина](PUBLISHING_GUIDE.md)

## 📊 Статус документации

| Раздел      | Статус      | Актуальность |
|-------------|-------------|--------------|
| README      | ✅ Готово    | Актуально    |
| Архитектура | ✅ Готово    | Актуально    |
| Features    | ✅ Готово    | Актуально    |
| API         | ✅ Готово    | Актуально    |
| roadmaps    | ⚠️ Частично | Устарело     |

## 🔄 Последние обновления

 - **2025-10-25**: Фаза 7 (Тестирование и валидация) Project Scanner завершена: созданы comprehensive unit тесты, performance тесты, стресс-тесты; выявлены проблемы с фильтрацией и интеграцией
 - **2025-10-23**: Фаза 6 (Улучшение производительности) Project Scanner завершена: индексация файлов, оптимизация памяти, метрики производительности
 - **2025-10-23**: Фаза 4 (Расширенная аналитика файлов) выполнена: SLOC, сложность, статистика языков, анализ зависимостей; добавлена контент-детекция типов файлов
- **2025-10-20**: Добавленroadmap расширенного агента-оркестратора с Tool Agents и стейт-машиной
- **2025-10-20**: Создана техническая спецификация для агента-оркестратора
- **2025-10-20**: Добавленroadmap агента анализа кода (Code Analysis Agent)
- **2025-10-20**: Добавленroadmap интеграции библиотеки Koog
- **2025-10-20**: Проведен анализ целесообразности использования Koog
{{ ... }}
- **2025-10-18**: Добавлен roadmap оркестрации агентов
- **2025-10-18**: Исправлена кодировка вывода терминала для Windows (CP866)
- **2025-10-11**: Добавлена документация по управлению токенами
- **2025-10-11**: Систематизирована структура документации

## 💡 Как использовать эту документацию

1. **Новичкам** - начните с [главного README](../README.md)
2. **Разработчикам** - изучите [архитектуру](architecture/overview.md)
3. **Пользователям** - смотрите [примеры использования](USAGE_EXAMPLES.md)
4. **Контрибьюторам** - проверьте [бэклог](BACKLOG.md)

## 📝 Обратная связь

Нашли ошибку в документации? [Создайте issue](https://github.com/yourusername/ride/issues) или отправьте Pull Request!

---

<p align="center">
  <sub>Документация обновлена: 2025-10-25</sub>
</p>
