# 📦 Руководство по публикации плагина Ride в JetBrains Marketplace

## Подготовка к публикации

### ✅ Чек-лист перед публикацией

- [x] Код полностью реализован и протестирован
- [x] Все unit тесты проходят успешно
- [x] Плагин протестирован вручную в IDE
- [x] Документация готова (README, CHANGELOG, примеры)
- [x] Иконка плагина создана (pluginIcon.svg)
- [x] plugin.xml заполнен корректно
- [ ] Обновлены контакты (email, GitHub URL)
- [ ] Создан LICENSE файл

## Шаг 1: Финальная подготовка

### 1.1 Обновите контактную информацию

В файлах замените placeholder'ы на реальные данные:

**plugin.xml:**
```xml
<vendor email="your.email@example.com" url="https://github.com/yourusername/ride">MarsLab</vendor>
```

**README.md:**
- `yourusername` → ваш GitHub username
- `your.email@example.com` → ваш email

### 1.2 Создайте LICENSE файл

Создайте файл `LICENSE` в корне проекта:

```
MIT License

Copyright (c) 2025 MarsLab

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### 1.3 Проверьте версию

В `build.gradle.kts` убедитесь, что версия корректна:
```kotlin
version = "1.0.0"  // Уберите -SNAPSHOT для релиза
```

## Шаг 2: Сборка плагина

### 2.1 Соберите финальную версию

```bash
# Очистите предыдущие сборки
./gradlew clean

# Соберите плагин
./gradlew buildPlugin
```

### 2.2 Проверьте артефакт

Плагин будет собран в:
```
build/distributions/ride-1.0.0.zip
```

Проверьте содержимое архива:
- `ride/lib/` - JAR файлы
- `ride/lib/ride-1.0.0.jar` - основной JAR плагина

### 2.3 Протестируйте собранный плагин

Установите собранный плагин в чистую IDE:
```bash
# Или запустите в тестовой IDE
./gradlew runIde
```

## Шаг 3: Регистрация на JetBrains Marketplace

### 3.1 Создайте JetBrains аккаунт

1. Перейдите на https://account.jetbrains.com/
2. Зарегистрируйтесь или войдите через GitHub/Google
3. Подтвердите email

### 3.2 Получите доступ к Marketplace

1. Перейдите на https://plugins.jetbrains.com/
2. Войдите в свой аккаунт
3. Нажмите на аватар → **Upload plugin**

## Шаг 4: Загрузка плагина

### 4.1 Первая загрузка

1. Перейдите на https://plugins.jetbrains.com/plugin/add
2. Нажмите **Get started**

### 4.2 Заполните информацию о плагине

**Основная информация:**
- **Plugin name**: Ride
- **Plugin ID**: `ru.marslab.ide.ride` (из plugin.xml)
- **License**: MIT
- **Category**: Code editing, AI

**Описание:**
Скопируйте из README.md или используйте краткое описание:
```
AI-ассистент для разработчиков с интеграцией Yandex GPT. 
Интерактивный чат прямо в IDE для помощи с кодом, отладкой и разработкой.
```

**Теги (Tags):**
- AI
- Chat
- Assistant
- Yandex GPT
- Code Helper
- Developer Tools

### 4.3 Загрузите файлы

1. **Plugin file**: Загрузите `build/distributions/ride-1.0.0.zip`
2. **Plugin icon**: Загрузите `src/main/resources/META-INF/pluginIcon.svg`
3. **Screenshots** (опционально): Сделайте скриншоты чата в действии

### 4.4 Заполните дополнительную информацию

**Version notes (Release notes):**
```markdown
# Version 1.0.0

Initial release of Ride - AI-powered coding assistant!

## Features
- Interactive chat with AI assistant
- Yandex GPT integration
- Message history
- Secure API key storage
- Async request processing
- Customizable settings

## Requirements
- IntelliJ IDEA 2025.1.4.1+
- JDK 21
- Yandex Cloud account with Yandex GPT API access
```

**Links:**
- **Source code**: https://github.com/yourusername/ride
- **Documentation**: https://github.com/yourusername/ride#readme
- **Issue tracker**: https://github.com/yourusername/ride/issues

### 4.5 Совместимость

Укажите совместимые продукты:
- ✅ IntelliJ IDEA (Community & Ultimate)
- ✅ PyCharm
- ✅ WebStorm
- ✅ PhpStorm
- ✅ RubyMine
- ✅ GoLand
- ✅ Rider
- ✅ CLion
- ✅ Android Studio

**Версии**: 2025.1.4.1 и выше

## Шаг 5: Модерация и публикация

### 5.1 Отправка на модерацию

1. Проверьте все заполненные данные
2. Нажмите **Submit for review**
3. Дождитесь email подтверждения

### 5.2 Процесс модерации

**Время модерации**: обычно 1-3 рабочих дня

JetBrains проверит:
- ✅ Плагин собирается и запускается
- ✅ Нет вредоносного кода
- ✅ Описание соответствует функциональности
- ✅ Иконка и скриншоты корректны
- ✅ Лицензия указана

### 5.3 Возможные причины отклонения

- ❌ Плагин не запускается
- ❌ Описание неполное или вводит в заблуждение
- ❌ Нарушение авторских прав
- ❌ Отсутствие лицензии
- ❌ Некорректная иконка

### 5.4 После одобрения

После одобрения плагин:
- ✅ Появится в JetBrains Marketplace
- ✅ Будет доступен для установки через IDE
- ✅ Появится в поиске плагинов

## Шаг 6: Обновления плагина

### 6.1 Подготовка новой версии

1. Обновите версию в `build.gradle.kts`:
```kotlin
version = "1.1.0"
```

2. Обновите `changeNotes` в `build.gradle.kts`:
```kotlin
changeNotes = """
    Version 1.1.0:
    - Added new feature X
    - Fixed bug Y
    - Improved performance
""".trimIndent()
```

3. Обновите CHANGELOG.md

### 6.2 Загрузка обновления

1. Соберите новую версию: `./gradlew buildPlugin`
2. Перейдите на страницу плагина в Marketplace
3. Нажмите **Upload update**
4. Загрузите новый ZIP файл
5. Заполните Release notes
6. Отправьте на модерацию

### 6.3 Автоматическая публикация (CI/CD)

Можно настроить автоматическую публикацию через GitHub Actions:

```yaml
# .github/workflows/publish.yml
name: Publish Plugin

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Publish Plugin
        run: ./gradlew publishPlugin
        env:
          PUBLISH_TOKEN: ${{ secrets.JETBRAINS_MARKETPLACE_TOKEN }}
```

## Шаг 7: Продвижение плагина

### 7.1 После публикации

- 📢 Анонсируйте в социальных сетях
- 📝 Напишите статью на Medium/Habr
- 🎥 Создайте видео-демонстрацию
- 💬 Поделитесь в сообществах разработчиков

### 7.2 Мониторинг

Следите за:
- ⭐ Рейтингом и отзывами
- 📊 Статистикой загрузок
- 🐛 Issues на GitHub
- 💬 Вопросами пользователей

### 7.3 Поддержка пользователей

- Отвечайте на вопросы в Issues
- Обновляйте документацию
- Исправляйте баги оперативно
- Добавляйте запрошенные функции

## Полезные ссылки

- 📚 [IntelliJ Platform Plugin Development](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- 🏪 [JetBrains Marketplace](https://plugins.jetbrains.com/)
- 📖 [Plugin Publishing Guide](https://plugins.jetbrains.com/docs/marketplace/plugin-overview.html)
- 🎨 [Plugin Icon Guidelines](https://plugins.jetbrains.com/docs/intellij/plugin-icon-file.html)
- ✅ [Plugin Verification](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)

## Чек-лист публикации

Перед отправкой убедитесь:

- [ ] Версия обновлена (без -SNAPSHOT)
- [ ] Все тесты проходят
- [ ] plugin.xml заполнен корректно
- [ ] Контакты обновлены (email, URL)
- [ ] LICENSE файл создан
- [ ] README актуален
- [ ] CHANGELOG обновлен
- [ ] Иконка готова
- [ ] Плагин собран (`./gradlew buildPlugin`)
- [ ] Плагин протестирован вручную
- [ ] Скриншоты подготовлены
- [ ] Описание для Marketplace готово
- [ ] GitHub репозиторий публичный

## Поздравляем! 🎉

После успешной публикации ваш плагин будет доступен миллионам разработчиков по всему миру!

---

**Удачи с публикацией Ride!** 🚀
