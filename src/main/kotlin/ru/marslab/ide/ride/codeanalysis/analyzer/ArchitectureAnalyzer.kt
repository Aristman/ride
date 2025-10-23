package ru.marslab.ide.ride.codeanalysis.analyzer

import com.intellij.openapi.vfs.VirtualFile
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.codeanalysis.*
import ru.marslab.ide.ride.model.llm.LLMParameters

/**
 * Анализатор архитектуры проекта
 */
class ArchitectureAnalyzer(
    private val llmProvider: LLMProvider
) {
    /**
     * Анализирует архитектуру проекта
     *
     * @param projectFiles Файлы проекта
     * @param projectPath Путь к проекту
     * @return Структура проекта
     */
    suspend fun analyze(projectFiles: List<VirtualFile>, projectPath: String): ProjectStructure {
        // Группируем файлы по пакетам/директориям
        val packageStructure = groupFilesByPackage(projectFiles)

        // Определяем модули
        val modules = identifyModules(packageStructure, projectFiles)

        // Определяем слои архитектуры
        val layers = identifyLayers(modules)

        // Анализируем зависимости (упрощенная версия)
        val dependencies = analyzeDependencies(modules)

        // Определяем корневой пакет
        val rootPackage = findRootPackage(packageStructure)

        return ProjectStructure(
            rootPackage = rootPackage,
            modules = modules,
            layers = layers,
            dependencies = dependencies
        )
    }

    /**
     * Группирует файлы по пакетам
     */
    private fun groupFilesByPackage(files: List<VirtualFile>): Map<String, List<VirtualFile>> {
        return files.groupBy { file ->
            file.parent?.path ?: ""
        }
    }

    /**
     * Определяет модули проекта
     */
    private fun identifyModules(
        packageStructure: Map<String, List<VirtualFile>>,
        allFiles: List<VirtualFile>
    ): List<Module> {
        val modules = mutableListOf<Module>()

        // Группируем по основным директориям
        val moduleGroups = packageStructure.keys.groupBy { path ->
            // Извлекаем основную директорию модуля
            val parts = path.split("/")
            val srcIndex = parts.indexOfLast { it == "src" || it == "main" || it == "kotlin" || it == "java" }
            if (srcIndex >= 0 && srcIndex + 1 < parts.size) {
                parts.subList(0, srcIndex + 2).joinToString("/")
            } else {
                path.split("/").take(3).joinToString("/")
            }
        }

        for ((modulePath, packages) in moduleGroups) {
            val moduleFiles = packages.flatMap { packageStructure[it] ?: emptyList() }
            val moduleName = modulePath.split("/").lastOrNull() ?: "unknown"
            val moduleType = detectModuleType(moduleName, modulePath)

            modules.add(
                Module(
                    name = moduleName,
                    path = modulePath,
                    type = moduleType,
                    files = moduleFiles.size,
                    linesOfCode = countLinesOfCode(moduleFiles)
                )
            )
        }

        return modules
    }

    /**
     * Определяет тип модуля по имени и пути
     */
    private fun detectModuleType(name: String, path: String): ModuleType {
        val lowerName = name.lowercase()
        val lowerPath = path.lowercase()

        return when {
            lowerName.contains("test") || lowerPath.contains("/test") -> ModuleType.TEST
            lowerName.contains("ui") || lowerName.contains("view") -> ModuleType.UI
            lowerName.contains("domain") || lowerName.contains("model") -> ModuleType.DOMAIN
            lowerName.contains("service") || lowerName.contains("business") -> ModuleType.SERVICE
            lowerName.contains("integration") || lowerName.contains("api") -> ModuleType.INTEGRATION
            lowerName.contains("util") || lowerName.contains("helper") -> ModuleType.UTIL
            else -> ModuleType.SERVICE
        }
    }

    /**
     * Подсчитывает строки кода в файлах
     */
    private fun countLinesOfCode(files: List<VirtualFile>): Int {
        return files.sumOf { file ->
            try {
                String(file.contentsToByteArray()).lines().size
            } catch (e: Exception) {
                0
            }
        }
    }

    /**
     * Определяет архитектурные слои
     */
    private fun identifyLayers(modules: List<Module>): List<Layer> {
        val layers = mutableListOf<Layer>()

        val uiModules = modules.filter { it.type == ModuleType.UI }.map { it.name }
        if (uiModules.isNotEmpty()) {
            layers.add(Layer("UI Layer", uiModules))
        }

        val serviceModules = modules.filter { it.type == ModuleType.SERVICE }.map { it.name }
        if (serviceModules.isNotEmpty()) {
            layers.add(Layer("Service Layer", serviceModules))
        }

        val domainModules = modules.filter { it.type == ModuleType.DOMAIN }.map { it.name }
        if (domainModules.isNotEmpty()) {
            layers.add(Layer("Domain Layer", domainModules))
        }

        val integrationModules = modules.filter { it.type == ModuleType.INTEGRATION }.map { it.name }
        if (integrationModules.isNotEmpty()) {
            layers.add(Layer("Integration Layer", integrationModules))
        }

        return layers
    }

    /**
     * Анализирует зависимости между модулями (упрощенная версия)
     */
    private fun analyzeDependencies(modules: List<Module>): List<Dependency> {
        val dependencies = mutableListOf<Dependency>()

        // Простая эвристика: UI зависит от Service, Service от Domain
        val uiModules = modules.filter { it.type == ModuleType.UI }
        val serviceModules = modules.filter { it.type == ModuleType.SERVICE }
        val domainModules = modules.filter { it.type == ModuleType.DOMAIN }

        for (ui in uiModules) {
            for (service in serviceModules) {
                dependencies.add(Dependency(ui.name, service.name, "uses"))
            }
        }

        for (service in serviceModules) {
            for (domain in domainModules) {
                dependencies.add(Dependency(service.name, domain.name, "uses"))
            }
        }

        return dependencies
    }

    /**
     * Находит корневой пакет проекта
     */
    private fun findRootPackage(packageStructure: Map<String, List<VirtualFile>>): String {
        if (packageStructure.isEmpty()) {
            return "unknown"
        }

        // Ищем общий префикс всех пакетов
        val paths = packageStructure.keys.toList()
        if (paths.isEmpty()) return "unknown"

        val commonPrefix = paths.reduce { acc, path ->
            acc.commonPrefixWith(path)
        }

        return commonPrefix.split("/").lastOrNull { it.isNotEmpty() } ?: "unknown"
    }

    /**
     * Генерирует описание архитектуры через LLM
     */
    suspend fun generateArchitectureDescription(structure: ProjectStructure): String {
        val prompt = """
        Проанализируй структуру проекта и опиши его архитектуру.
        
        Корневой пакет: ${structure.rootPackage}
        Количество модулей: ${structure.modules.size}
        Слои: ${structure.layers.map { it.name }.joinToString(", ")}
        Зависимости: ${structure.dependencies.size}
        
        Модули:
        ${structure.modules.joinToString("\n") { "- ${it.name} (${it.type.name}): ${it.files} файлов, ${it.linesOfCode} строк" }}
        
        Определи:
        - Архитектурный паттерн (MVC, MVVM, Clean Architecture, etc.)
        - Соблюдение принципов SOLID
        - Проблемы в архитектуре
        - Рекомендации по улучшению
        """.trimIndent()

        val response = llmProvider.sendRequest(
            systemPrompt = ARCHITECTURE_SYSTEM_PROMPT,
            userMessage = prompt,
            conversationHistory = emptyList(),
            parameters = LLMParameters.BALANCED
        )

        return if (response.success) response.content else "Не удалось проанализировать архитектуру"
    }

    companion object {
        private const val ARCHITECTURE_SYSTEM_PROMPT = """
        Ты - архитектор программного обеспечения с глубоким пониманием паттернов проектирования.
        Анализируй архитектуру проектов и давай конструктивные рекомендации.
        Ссылайся на принципы SOLID, Clean Architecture и best practices.
        """
    }
}
