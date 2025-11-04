package ru.marslab.ide.ride.service.rules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import ru.marslab.ide.ride.settings.PluginSettings
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

/**
 * Сервис для управления настраиваемыми правилами из Markdown файлов
 *
 * Application Service для загрузки и конкатенации правил из директорий:
 * - Глобальные: ~/.ride/rules/
 * - Проектные: <PROJECT_ROOT>/.ride/rules/
 */
@Service(Service.Level.APP)
class RulesService {

    private val logger = Logger.getInstance(RulesService::class.java)

    // Кеш для загруженных правил с меткой времени
    private data class CachedRules(
        val content: String,
        val lastModified: Long,
        val globalLastModified: Long,
        val projectLastModified: Long?
    )

    private var cachedRules: CachedRules? = null

    companion object {
        private const val MAX_RULES_SIZE = 50000 // Максимальный размер всех правил в символах
        private const val RULES_DIR_NAME = "rules"
        private const val RIDE_DIR_NAME = ".ride"
    }

    /**
     * Создает системный промпт с добавленными правилами
     *
     * @param basePrompt Базовый системный промпт
     * @param project Текущий проект (может быть null)
     * @return Системный промпт с правилами
     */
    fun composeSystemPromptWithRules(basePrompt: String, project: Project?): String {
        if (!areRulesEnabled()) {
            return basePrompt
        }

        val rulesContent = loadRules(project)
        return if (rulesContent.isNotBlank()) {
            buildString {
                append(basePrompt)
                append("\n\n")
                append(rulesContent)
            }
        } else {
            basePrompt
        }
    }

    /**
     * Получить превью активных правил для отображения в UI
     *
     * @param project Текущий проект
     * @return Содержимое правил с информацией об источниках
     */
    fun getRulesPreview(project: Project? = null): String {
        if (!areRulesEnabled()) {
            return "Правила отключены в настройках"
        }

        val rulesContent = loadRules(project)
        return if (rulesContent.isBlank()) {
            "Правила не найдены. Создайте .md файлы в директориях:\n• Глобальные: ~/.ride/rules/\n• Проектные: <PROJECT_ROOT>/.ride/rules/"
        } else {
            rulesContent
        }
    }

    /**
     * Загружает правила из директорий с кешированием
     *
     * @param project Текущий проект
     * @return Сконкатенированное содержимое правил
     */
    private fun loadRules(project: Project?): String {
        val globalDir = getGlobalRulesDirectory()
        val projectDir = project?.let { getProjectRulesDirectory(it) }

        // Проверяем время модификации директорий
        val globalLastModified = getDirectoryLastModified(globalDir)
        val projectLastModified = projectDir?.let { getDirectoryLastModified(it) }

        // Проверяем кеш
        val cached = cachedRules
        if (cached != null &&
            cached.lastModified == globalLastModified + (projectLastModified ?: 0L) &&
            cached.globalLastModified == globalLastModified &&
            cached.projectLastModified == projectLastModified) {
            logger.debug("Using cached rules")
            return cached.content
        }

        // Загружаем правила заново
        val rulesContent = loadRulesFromDirectories(globalDir, projectDir)

        // Обрезаем если превышен лимит
        val trimmedContent = if (rulesContent.length > MAX_RULES_SIZE) {
            logger.warn("Rules content exceeds $MAX_RULES_SIZE characters, trimming")
            rulesContent.take(MAX_RULES_SIZE) + "\n\n<!-- Правила обрезаны из-за превышения лимита -->"
        } else {
            rulesContent
        }

        // Кешируем результат
        cachedRules = CachedRules(
            content = trimmedContent,
            lastModified = globalLastModified + (projectLastModified ?: 0L),
            globalLastModified = globalLastModified,
            projectLastModified = projectLastModified
        )

        return trimmedContent
    }

    /**
     * Загружает правила из указанных директорий
     */
    private fun loadRulesFromDirectories(globalDir: File, projectDir: File?): String {
        val result = StringBuilder()

        // Сначала проектные правила (выший приоритет)
        projectDir?.let { dir ->
            if (dir.exists() && dir.isDirectory) {
                val projectRules = loadRulesFromDirectory(dir, "проектные")
                if (projectRules.isNotBlank()) {
                    result.append(projectRules)
                    result.append("\n")
                }
            }
        }

        // Затем глобальные правила
        if (globalDir.exists() && globalDir.isDirectory) {
            val globalRules = loadRulesFromDirectory(globalDir, "глобальные")
            if (globalRules.isNotBlank()) {
                result.append(globalRules)
            }
        }

        return result.toString()
    }

    /**
     * Загружает .md файлы из указанной директории
     */
    private fun loadRulesFromDirectory(directory: File, scope: String): String {
        val result = StringBuilder()

        try {
            val mdFiles = directory.listFiles { file ->
                file.isFile && file.name.lowercase().endsWith(".md")
            }?.sortedBy { it.name }

            if (!mdFiles.isNullOrEmpty()) {
                if (result.isNotBlank()) {
                    result.append("\n")
                }

                result.append("\n\n---\n<!-- rules: $scope -->\n\n")

                var totalSize = 0
                mdFiles.forEach { file ->
                    try {
                        if (totalSize < MAX_RULES_SIZE) {
                            val content = readFileContent(file)
                            val fileName = file.nameWithoutExtension

                            result.append("<!-- rule: $fileName -->\n")
                            result.append(content)
                            result.append("\n\n")

                            totalSize += content.length
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to read rule file ${file.absolutePath}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to load rules from directory ${directory.absolutePath}: ${e.message}")
        }

        return result.toString()
    }

    /**
     * Безопасно читает содержимое файла с нормализацией
     */
    private fun readFileContent(file: File): String {
        return try {
            val content = Files.readString(file.toPath())
                .replace("\r\n", "\n") // Нормализуем переносы строк
                .replace("\r", "\n")
                .trim()

            // Удаляем BOM если есть
            if (content.startsWith("\uFEFF")) {
                content.substring(1)
            } else {
                content
            }
        } catch (e: Exception) {
            logger.warn("Failed to read file ${file.absolutePath}: ${e.message}")
            ""
        }
    }

    /**
     * Получает время последней модификации директории
     */
    private fun getDirectoryLastModified(directory: File): Long {
        return if (!directory.exists()) {
            0L
        } else {
            try {
                Files.walk(directory.toPath(), 1)
                    .filter { path -> !Files.isDirectory(path) }
                    .map { path -> Files.getLastModifiedTime(path).toMillis() }
                    .toList()
                    .maxOrNull() ?: 0L
            } catch (e: Exception) {
                logger.warn("Failed to get directory last modified time for ${directory.absolutePath}: ${e.message}")
                0L
            }
        }
    }

    /**
     * Получает директорию глобальных правил
     */
    fun getGlobalRulesDirectory(): File {
        val userHome = System.getProperty("user.home")
        return Paths.get(userHome, RIDE_DIR_NAME, RULES_DIR_NAME).toFile()
    }

    /**
     * Получает директорию правил проекта
     */
    fun getProjectRulesDirectory(project: Project): File? {
        val basePath = project.basePath ?: return null
        return Paths.get(basePath, RIDE_DIR_NAME, RULES_DIR_NAME).toFile()
    }

    /**
     * Создает директорию для правил если она не существует
     */
    fun ensureRulesDirectoryExists(isGlobal: Boolean, project: Project? = null): Boolean {
        val directory = if (isGlobal) {
            getGlobalRulesDirectory()
        } else {
            project?.let { getProjectRulesDirectory(it) } ?: return false
        }

        return try {
            if (!directory.exists()) {
                Files.createDirectories(directory.toPath())
                logger.info("Created rules directory: ${directory.absolutePath}")
                true
            } else {
                true
            }
        } catch (e: Exception) {
            logger.warn("Failed to create rules directory ${directory.absolutePath}: ${e.message}")
            false
        }
    }

    /**
     * Создает шаблон файла правил
     */
    fun createRuleTemplate(isGlobal: Boolean, project: Project? = null): File? {
        val directory = if (isGlobal) {
            getGlobalRulesDirectory()
        } else {
            project?.let { getProjectRulesDirectory(it) } ?: return null
        }

        if (!ensureRulesDirectoryExists(isGlobal, project)) {
            return null
        }

        val templateName = if (isGlobal) {
            "example-global-rule.md"
        } else {
            "example-project-rule.md"
        }

        val templateFile = File(directory, templateName)

        // Если файл уже существует, не перезаписываем
        if (templateFile.exists()) {
            return templateFile
        }

        val templateContent = if (isGlobal) {
            buildString {
                appendLine("# Пример глобального правила")
                appendLine()
                appendLine("Это правило применяется ко всем проектам.")
                appendLine("Используйте его для определения общих соглашений:")
                appendLine()
                appendLine("- Стиль кодирования")
                appendLine("- Форматирование")
                appendLine("- Лучшие практики")
                appendLine("- Стандарты документации")
                appendLine()
                appendLine("## Пример:")
                appendLine()
                appendLine("```kotlin")
                appendLine("// Используй descriptive имена переменных")
                appendLine("val userManager = UserManager()")
                appendLine("```")
            }
        } else {
            buildString {
                appendLine("# Пример проектного правила")
                appendLine()
                appendLine("Это правило применяется только к текущему проекту.")
                appendLine("Используйте его для определения проектно-специфичных соглашений:")
                appendLine()
                appendLine("- Архитектурные паттерны")
                appendLine("- Конвенции именования в проекте")
                appendLine("- Специфичные для проекта практики")
                appendLine()
                appendLine("## Пример:")
                appendLine()
                appendLine("```kotlin")
                appendLine("// В этом проекте используем Repository pattern")
                appendLine("class UserRepository : Repository<User> {")
                appendLine("    // реализация")
                appendLine("}")
                appendLine("```")
            }
        }

        return try {
            Files.write(templateFile.toPath(), templateContent.toByteArray(Charsets.UTF_8))
            logger.info("Created rule template: ${templateFile.absolutePath}")
            templateFile
        } catch (e: Exception) {
            logger.warn("Failed to create rule template ${templateFile.absolutePath}: ${e.message}")
            null
        }
    }

    /**
     * Очищает кеш правил
     */
    fun clearCache() {
        cachedRules = null
        logger.debug("Rules cache cleared")
    }

    /**
     * Проверяет включены ли правила в настройках
     */
    private fun areRulesEnabled(): Boolean {
        return service<PluginSettings>().enableCustomRules
    }
}