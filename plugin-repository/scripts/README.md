# JetBrains Plugin Repository Scripts

Минималистичный набор скриптов для быстрого развертывания и управления кастомным репозиторием плагинов JetBrains на Ubuntu 22.04.

## Структура

```
plugin-repository/
├── deploy-repository.sh    # Основной скрипт развертывания
├── add-plugin.sh           # Добавление нового плагина
├── update-version.sh       # Обновление версии плагина
├── health-check.sh         # Проверка работоспособности
└── README.md               # Эта документация
```

## Быстрый старт

### 1. Развертывание репозитория

```bash
sudo ./deploy-repository.sh
```

Скрипт автоматически:
- Обновит систему и установит nginx
- Создаст структуру директорий
- Настроит виртуальный хост
- Создаст демо-плагин
- Сгенерирует updatePlugins.xml

### 2. Проверка работоспособности

```bash
sudo ./health-check.sh
```

### 3. Добавление нового плагина

```bash
sudo ./add-plugin.sh <plugin-id> "<Plugin Name>" "<Description>" "<Vendor>" /path/to/plugin.zip
```

Пример:
```bash
sudo ./add-plugin.sh com.my.cool.plugin "My Cool Plugin" "Useful plugin for developers" "My Company" /tmp/my-plugin-1.0.0.zip
```

## Подробное описание скриптов

### deploy-repository.sh

Основной скрипт для первичного развертывания репозитория.

**Требования:**
- Ubuntu 22.04
- Права root
- Доступ в интернет

**Что делает:**
1. Проверяет права root
2. Обновляет пакеты системы
3. Устанавливает nginx и curl
4. Создает структуру директорий:
   - `/var/www/plugins/` - корень репозитория
   - `/var/www/plugins/archives/` - ZIP-архивы плагинов
   - `/var/www/plugins/backups/` - резервные копии
5. Настраивает nginx виртуальный хост на порту 80
6. Создает демо-плагин для демонстрации
7. Генерирует `updatePlugins.xml`

**Конфигурация:**
```bash
export DOMAIN_NAME="192.168.1.100"  # IP или домен
./deploy-repository.sh
```

### add-plugin.sh

Добавляет новый плагин в репозиторий.

**Использование:**
```bash
sudo ./add-plugin.sh <plugin-id> "<Plugin Name>" "<Description>" "<Vendor>" <path-to-zip>
```

**Что делает:**
1. Валидирует аргументы и файлы
2. Извлекает версию из plugin.xml в архиве
3. Копирует архив в директорию репозитория
4. Создает резервную копию updatePlugins.xml
5. Добавляет запись о плагине в XML

**Пример:**
```bash
sudo ./add-plugin.sh com.example.tools "Dev Tools" "Development utilities" "Example Corp" /home/user/dev-tools.zip
```

### update-version.sh

Обновляет версию существующего плагина.

**Использование:**
```bash
sudo ./update-version.sh <plugin-id> <path-to-new-zip>
```

**Что делает:**
1. Проверяет существование плагина в репозитории
2. Извлекает информацию из нового архива
3. Обновляет запись в updatePlugins.xml
4. Сохраняет старый архив (опционально)
5. Создает резервную копию XML

**Пример:**
```bash
sudo ./update-version.sh com.example.tools /home/user/dev-tools-2.0.0.zip
```

### health-check.sh

Комплексная проверка работоспособности репозитория.

**Проверки:**
- Состояние сервисов (nginx)
- Файловая структура и права доступа
- Валидность XML
- Доступность архивов плагинов
- Сетевая доступность
- Свободное дисковое пространство

**Использование:**
```bash
sudo ./health-check.sh
```

## Файловая структура репозитория

```
/var/www/plugins/
├── updatePlugins.xml          # Индексный файл репозитория
├── archives/                  # ZIP-архивы плагинов
│   ├── demo-plugin-1.0.0.zip
│   ├── my-tool-1.0.0.zip
│   └── another-plugin-2.1.0.zip
├── backups/                   # Резервные копии XML
│   ├── updatePlugins-20241025-143022.xml
│   └── updatePlugins-20241025-151234.xml
└── README.md                  # Документация
```

## Настройка в IntelliJ IDEA

1. Откройте IntelliJ IDEA
2. `File → Settings → Plugins`
3. Нажмите на иконку ⚙️ (Manage Plugin Repositories)
4. Нажмите кнопку `+` для добавления репозитория
5. Введите URL: `http://your-server-ip/updatePlugins.xml`
6. Нажмите OK
7. В поиске плагинов выберите ваш репозиторий

## URL репозитория

После развертывания репозиторий будет доступен по адресу:
```
http://your-server-ip/updatePlugins.xml
```

## Пример updatePlugins.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="com.example.demo.plugin"
          url="http://192.168.1.100/archives/demo-plugin-1.0.0.zip"
          version="1.0.0">
    <name>Demo Plugin</name>
    <description>Пример плагина для учебного репозитория</description>
    <vendor>Example Company</vendor>
    <idea-version since-build="211.0" until-build="241.*"/>
  </plugin>
</plugins>
```

## Полезные команды

### Проверка доступности репозитория
```bash
curl http://your-server-ip/updatePlugins.xml
```

### Проверка логов nginx
```bash
tail -f /var/log/nginx/plugin-repository*.log
```

### Проверка плагинов в архиве
```bash
unzip -l /var/www/plugins/archives/demo-plugin-1.0.0.zip
```

### Проверка структуры XML
```bash
xmllint --noout /var/www/plugins/updatePlugins.xml
```

## Безопасность

В этой версии реализован минимальный уровень безопасности:
- Базовые права доступа к файлам
- Отсутствие SSL сертификата
- Отсутствие аутентификации

Для продакшн использования рекомендуется:
1. Настроить SSL сертификат
2. Добавить базовую аутентификацию
3. Настроить firewall
4. Реализовать регулярное резервное копирование

## Устранение проблем

### Ошибка "Permission denied"
Убедитесь, что скрипты запускаются с правами root:
```bash
sudo ./deploy-repository.sh
```

### Ошибка "nginx: configuration file test failed"
Проверьте конфигурацию nginx:
```bash
sudo nginx -t
```

### Ошибка "plugin.xml not found"
Убедитесь, что в ZIP-архиве есть файл plugin.xml в корне:
```bash
unzip -l your-plugin.zip | grep plugin.xml
```

### Плагин не виден в IntelliJ IDEA
1. Проверьте доступность XML файла:
   ```bash
   curl http://your-server-ip/updatePlugins.xml
   ```
2. Проверьте MIME-типы в nginx
3. Проверьте правильность структуры XML
4. Перезапустите IntelliJ IDEA

## Время работы

- **Развертывание**: 2-3 минуты на чистой Ubuntu 22.04
- **Добавление плагина**: 10-30 секунд
- **Обновление версии**: 10-30 секунд
- **Проверка работоспособности**: 5-10 секунд

---

*Автор: Claude Code Assistant*
*Версия: 1.0*
*Обновлено: 2025-10-25*