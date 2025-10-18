package ru.marslab.ide.ride.ui.templates

/**
 * Утилитарный класс для загрузки шаблонов из ресурсов
 */
object TemplateLoader {

    /**
     * Загружает текстовый шаблон из ресурсов
     */
    fun loadTemplate(resourceName: String): String {
        return this::class.java.classLoader.getResourceAsStream(resourceName)
            ?.use { stream ->
                stream.bufferedReader().use { reader ->
                    reader.readText()
                }
            }
            ?: throw IllegalArgumentException("Template not found: $resourceName")
    }

    /**
     * Загружает HTML-шаблон из директории templates/html
     */
    fun loadHtmlTemplate(fileName: String): String {
        return loadTemplate("templates/html/$fileName")
    }
}