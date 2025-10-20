# Использование команды /terminal в чате

## Обзор

Команда `/terminal` позволяет выполнять shell-команды прямо из чата через интеграцию с `TerminalAgent`.

## Синтаксис

```
/terminal <команда>
/exec <команда>
```

Оба варианта работают одинаково.

## Примеры использования

### Git операции

```
/terminal git status
/terminal git log -n 5
/terminal git diff
/terminal git branch
```

### Сборка проекта

```
/terminal ./gradlew build
/terminal ./gradlew test
/terminal ./gradlew clean
```

### Поиск и навигация

```
/terminal find . -name "*.kt" -type f
/terminal grep -r "TerminalAgent" src/
/terminal ls -la
/terminal pwd
```

### Информация о системе

```
/terminal echo $PATH
/terminal java -version
/terminal node --version
```

## Как это работает

1. **Ввод команды**: Пользователь вводит `/terminal git status` в поле ввода
2. **Обработка**: `ChatPanel` распознает префикс `/terminal` и вызывает `executeTerminalCommand()`
3. **Выполнение**: `ChatService.executeTerminalCommand()` создает `TerminalAgent` и выполняет команду
4. **Результат**: Форматированный вывод отображается в чате с метаданными (код завершения, время выполнения)

## Формат ответа

```
🖥️ **Command Execution Result**

**Command:** `git status`
**Exit Code:** 0
**Execution Time:** 45ms
**Status:** ✅ Success

**Output:**
```
On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean
```
```

## Метаданные

Каждый ответ содержит метаданные:
- `agentType: "terminal"` - тип агента
- `command` - выполненная команда
- `exitCode` - код завершения
- `executionTime` - время выполнения в мс
- `workingDir` - рабочая директория

## Ограничения и безопасность

### ⚠️ Важно
- Команды выполняются с правами IDE
- Нет песочницы или изоляции
- Будьте осторожны с деструктивными командами

### Рекомендации
- Не используйте команды удаления (`rm`, `del`)
- Не выполняйте команды изменения системы (`shutdown`, `reboot`)
- Проверяйте команды перед выполнением
- Используйте для безопасных операций (git, gradle, поиск)

## Интеграция с историей

Все выполненные команды и их результаты сохраняются в истории чата:
- Команда отображается как сообщение пользователя
- Результат отображается как ответ ассистента
- История доступна для контекста в последующих запросах

## Отличия от /plan

| Команда | Назначение | Агент |
|---------|-----------|-------|
| `/terminal` | Выполнение одной команды | TerminalAgent |
| `/plan` | Создание плана и выполнение задач | AgentOrchestrator |
| Обычный текст | Диалог с AI | ChatAgent |

## Примеры сценариев

### Проверка статуса проекта
```
/terminal git status
/terminal ./gradlew build --dry-run
```

### Поиск файлов с ошибками
```
/terminal grep -r "TODO" src/
/terminal find . -name "*.kt" -exec grep -l "FIXME" {} \;
```

### Информация о зависимостях
```
/terminal ./gradlew dependencies
/terminal npm list --depth=0
```

## Будущие улучшения

- [ ] Автодополнение команд
- [ ] История выполненных команд
- [ ] Макросы для часто используемых команд
- [ ] Подтверждение для опасных команд
- [ ] Ограничение доступа к файловой системе
- [ ] Поддержка интерактивных команд

---

**См. также:**
- [Terminal Agent - Использование](terminal-agent.md)
- [Terminal Agent - Интеграция](terminal-agent-integration.md)
- [Список фич](README.md)
