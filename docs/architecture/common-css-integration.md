# Интеграция общих CSS стилей для обоих режимов

## Обзор

Была выполнена рефакторинг CSS-архитектуры для унификации стилей между JCEF и fallback режимами чата.

## Проблема

До рефакторинга существовало два независимых набора CSS стилей:
1. **JCEF режим**: использовал `chat.css` с базовыми стилями
2. **Fallback режим**: встроенные стили в `HtmlDocumentManager.kt` с частичным дублированием

Стили для форматированного вывода агентов (`agent-output-styles.css`) были доступны только в fallback режиме.

## Решение

### 1. Создание общих CSS ресурсов

- **`/css/common-styles.css`**: Единый файл с общими стилями для обоих режимов
- **`/css/agent-output-styles.css`**: Специализированные стили для форматированного вывода (существовал ранее)
- **`CommonStyles.kt`**: Утилита для управления загрузкой и инжектом стилей

### 2. Архитектура

```
src/main/resources/css/
├── common-styles.css          # Общие стили для обоих режимов
└── agent-output-styles.css    # Стили форматированного вывода агентов

src/main/kotlin/ru/marslab/ide/ride/ui/style/
└── CommonStyles.kt           # Управление загрузкой стилей
```

### 3. Интеграция

#### JCEF режим
- `ChatHtmlResources.loadCss()` использует `CommonStyles.getJcefStyles()`
- `chat.css` импортирует общие стили через `@import url("../css/common-styles.css")`

#### Fallback режим
- `HtmlDocumentManager.initializeFallbackHtml()` использует `CommonStyles.getFallbackStyles()`
- CSS переменные заменяются на конкретные значения темы

## Ключевые изменения

### CommonStyles.kt
```kotlin
object CommonStyles {
    fun loadCommonCss(): String      // Загрузка общих стилей
    fun loadAgentOutputCss(): String // Загрузка стилей агентов
    fun getJcefStyles(): String      // Стили для JCEF режима
    fun getFallbackStyles(themeReplacements: Map<String, String>): String // Стили для fallback
}
```

### HtmlDocumentManager.kt
```kotlin
private fun initializeFallbackHtml() {
    val themeReplacements = mapOf(
        "bg" to theme.bg,
        "textPrimary" to theme.textPrimary,
        // ... остальные переменные темы
    )
    val commonStyles = CommonStyles.getFallbackStyles(themeReplacements)
    // Инжект стилей в HTML
}
```

## CSS переменные и темы

Общие стили используют CSS переменные с поддержкой тем:

```css
:root {
    --bg: #0f1115;
    --textPrimary: #e6e6e6;
    --codeBg: #2b2b2b;
    /* ... */
}

[data-theme="light"] {
    --bg: #ffffff;
    --textPrimary: #333333;
    /* ... */
}
```

## Преимущества

1. **Унификация**: Общие стили для обоих режимов
2. **Масштабируемость**: Легкое добавление новых стилей
3. **Поддержка тем**: CSS переменные для светлой/темной темы
4. **DRY принцип**: Устранение дублирования кода
5. **Обслуживаемость**: Единое место для управления стилями

## Файловая структура

```
src/main/
├── resources/
│   ├── css/
│   │   ├── common-styles.css        # Новые общие стили
│   │   └── agent-output-styles.css  # Стили агентов (существовал)
│   └── chat/
│       └── chat.css                 # Обновлен с импортом общих стилей
└── kotlin/ru/marslab/ide/ride/
    ├── ui/style/
    │   └── CommonStyles.kt          # Новая утилита управления стилями
    ├── ui/chat/
    │   └── ChatHtmlResources.kt     # Обновлен для использования CommonStyles
    └── ui/manager/
        └── HtmlDocumentManager.kt   # Обновлен для инжекта общих стилей
```

## Тестирование

- Сборка проекта: `./gradlew buildPlugin` ✅
- Запуск в IDE: `./gradlew runIde` ✅
- JCEF режим успешно инициализируется с общими стилями
- Fallback режим использует инжект стилей с заменой переменных

## Дальнейшее развитие

1. Добавление переключения тем в runtime
2. Расширение набора CSS переменных
3. Оптимизация загрузки стилей
4. Поддержка кастомных тем пользователем