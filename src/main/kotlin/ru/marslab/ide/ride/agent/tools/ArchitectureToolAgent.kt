package ru.marslab.ide.ride.agent.tools

import com.intellij.openapi.vfs.VirtualFile
import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.integration.llm.LLMProvider
import ru.marslab.ide.ride.model.codeanalysis.*
import ru.marslab.ide.ride.model.llm.LLMParameters
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.StepInput
import ru.marslab.ide.ride.model.tool.StepOutput
import ru.marslab.ide.ride.model.tool.StepResult
import ru.marslab.ide.ride.model.tool.ToolPlanStep
import java.io.File

/**
 * Агент для анализа архитектуры проекта
 * Реализован по аналогии с ArchitectureAnalyzer для использования в orchestrator
 *
 * Capabilities:
 * - architecture_analysis - анализ архитектуры
 * - dependency_analysis - анализ зависимостей
 * - layer_detection - определение слоев
 * - module_identification - идентификация модулей
 * - structure_description - генерация описания архитектуры
 */
class ArchitectureToolAgent(
    private val llmProvider: LLMProvider
) : BaseToolAgent(
    agentType = AgentType.ARCHITECTURE_ANALYSIS,
    toolCapabilities = setOf(
        "architecture_analysis",
        "dependency_analysis",
        "layer_detection",
        "module_identification",
        "structure_description"
    )
) {

    override fun getDescription(): String {
        return "Анализирует архитектуру проекта, определяет модули, слои и зависимости"
    }

    override fun validateInput(input: StepInput): ValidationResult {
        val files = input.getList<String>("files")

        if (files.isNullOrEmpty()) {
            return ValidationResult.failure("files is required and must not be empty")
        }

        return ValidationResult.success()
    }

    override suspend fun doExecuteStep(step: ToolPlanStep, context: ExecutionContext): StepResult {
        val files = step.input.getList<String>("files") ?: emptyList()
        val projectPath = step.input.getString("projectPath") ?: ""
        val analysisType = step.input.getString("analysis_type") ?: "architecture_analysis"

        logger.info("Analyzing architecture for ${files.size} files with type: $analysisType")

        // Проверяем, есть ли файлы для анализа
        if (files.isEmpty()) {
            logger.warn("No files provided for architecture analysis")
            return StepResult.success(
                output = StepOutput.of(
                    "message" to "No files provided for architecture analysis",
                    "files_count" to 0
                ),
                metadata = mapOf("files_analyzed" to 0)
            )
        }

        // Конвертируем файлы в VirtualFile для совместимости с ArchitectureAnalyzer
        val virtualFiles = files.mapNotNull { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    createVirtualFileFromFile(file)
                } else {
                    logger.warn("File does not exist: $filePath")
                    null
                }
            } catch (e: Exception) {
                logger.error("Error creating VirtualFile for: $filePath", e)
                null
            }
        }

        return when (analysisType) {
            "architecture_analysis" -> analyzeArchitecture(virtualFiles, projectPath)
            "module_identification" -> identifyModules(virtualFiles)
            "dependency_analysis" -> analyzeDependencies(virtualFiles)
            "layer_detection" -> detectLayers(virtualFiles)
            "structure_description" -> generateStructureDescription(virtualFiles, projectPath)
            else -> analyzeArchitecture(virtualFiles, projectPath)
        }
    }

    /**
     * Анализирует архитектуру проекта
     */
    private suspend fun analyzeArchitecture(projectFiles: List<VirtualFile>, projectPath: String): StepResult {
        // Группируем файлы по пакетам/директориям
        val packageStructure = groupFilesByPackage(projectFiles)

        // Определяем модули
        val modules = identifyModulesFromFiles(packageStructure, projectFiles)

        // Определяем слои архитектуры
        val layers = identifyLayersFromModules(modules)

        // Анализируем зависимости
        val dependencies = analyzeModuleDependencies(modules)

        // Определяем корневой пакет
        val rootPackage = findRootPackage(packageStructure)

        // Создаем структуру проекта
        val structure = ProjectStructure(
            rootPackage = rootPackage,
            modules = modules,
            layers = layers,
            dependencies = dependencies
        )

        // Анализируем проблемы архитектуры
        val findings = analyzeArchitecturalIssues(structure)

        logger.info("Architecture analysis completed: ${modules.size} modules, ${layers.size} layers")

        return StepResult.success(
            output = StepOutput.of(
                "project_structure" to structure,
                "root_package" to rootPackage,
                "modules" to modules.map { module ->
                    mapOf(
                        "name" to module.name,
                        "path" to module.path,
                        "type" to module.type.name,
                        "files" to module.files,
                        "lines_of_code" to module.linesOfCode
                    )
                },
                "layers" to layers.map { layer ->
                    mapOf(
                        "name" to layer.name,
                        "modules" to layer.modules
                    )
                },
                "dependencies" to dependencies.map { dep ->
                    mapOf(
                        "from" to dep.from,
                        "to" to dep.to,
                        "type" to dep.type
                    )
                },
                "findings" to findings
            ),
            metadata = mapOf(
                "files_analyzed" to projectFiles.size,
                "modules_found" to modules.size,
                "layers_identified" to layers.size,
                "dependencies_found" to dependencies.size
            )
        )
    }

    /**
     * Идентифицирует модули проекта
     */
    private suspend fun identifyModules(projectFiles: List<VirtualFile>): StepResult {
        val packageStructure = groupFilesByPackage(projectFiles)
        val modules = identifyModulesFromFiles(packageStructure, projectFiles)

        return StepResult.success(
            output = StepOutput.of(
                "modules" to modules.map { module ->
                    mapOf(
                        "name" to module.name,
                        "path" to module.path,
                        "type" to module.type.name,
                        "files" to module.files,
                        "lines_of_code" to module.linesOfCode
                    )
                },
                "module_count" to modules.size
            ),
            metadata = mapOf(
                "files_analyzed" to projectFiles.size
            )
        )
    }

    /**
     * Анализирует зависимости между модулями
     */
    private suspend fun analyzeDependencies(projectFiles: List<VirtualFile>): StepResult {
        val packageStructure = groupFilesByPackage(projectFiles)
        val modules = identifyModulesFromFiles(packageStructure, projectFiles)
        val dependencies = analyzeModuleDependencies(modules)

        // Анализируем циклические зависимости
        val cycles = detectDependencyCycles(dependencies)

        return StepResult.success(
            output = StepOutput.of(
                "dependencies" to dependencies.map { dep ->
                    mapOf(
                        "from" to dep.from,
                        "to" to dep.to,
                        "type" to dep.type
                    )
                },
                "cycles" to cycles,
                "dependency_count" to dependencies.size,
                "cycle_count" to cycles.size
            ),
            metadata = mapOf(
                "files_analyzed" to projectFiles.size
            )
        )
    }

    /**
     * Определяет слои архитектуры
     */
    private suspend fun detectLayers(projectFiles: List<VirtualFile>): StepResult {
        val packageStructure = groupFilesByPackage(projectFiles)
        val modules = identifyModulesFromFiles(packageStructure, projectFiles)
        val layers = identifyLayersFromModules(modules)

        return StepResult.success(
            output = StepOutput.of(
                "layers" to layers.map { layer ->
                    mapOf(
                        "name" to layer.name,
                        "modules" to layer.modules
                    )
                },
                "layer_count" to layers.size
            ),
            metadata = mapOf(
                "files_analyzed" to projectFiles.size,
                "modules_analyzed" to modules.size
            )
        )
    }

    /**
     * Генерирует описание архитектуры через LLM
     */
    private suspend fun generateStructureDescription(projectFiles: List<VirtualFile>, projectPath: String): StepResult {
        val packageStructure = groupFilesByPackage(projectFiles)
        val modules = identifyModulesFromFiles(packageStructure, projectFiles)
        val layers = identifyLayersFromModules(modules)
        val dependencies = analyzeModuleDependencies(modules)
        val rootPackage = findRootPackage(packageStructure)

        val structure = ProjectStructure(rootPackage, modules, layers, dependencies)
        val description = generateArchitectureDescription(structure)

        return StepResult.success(
            output = StepOutput.of(
                "description" to description,
                "structure_summary" to mapOf(
                    "root_package" to rootPackage,
                    "module_count" to modules.size,
                    "layer_count" to layers.size,
                    "dependency_count" to dependencies.size
                )
            ),
            metadata = mapOf(
                "files_analyzed" to projectFiles.size,
                "description_generated" to true
            )
        )
    }

    /**
     * Группирует файлы по пакетам
     */
    private fun groupFilesByPackage(files: List<VirtualFile>): Map<String, List<VirtualFile>> {
        return files.groupBy { file ->
            try {
                file.parent?.path ?: ""
            } catch (e: Exception) {
                logger.warn("Error getting parent path for file: ${file.path}", e)
                ""
            }
        }
    }

    /**
     * Определяет модули проекта
     */
    private fun identifyModulesFromFiles(
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
                logger.warn("Error counting lines for file: ${file.path}", e)
                0
            }
        }
    }

    /**
     * Определяет архитектурные слои
     */
    private fun identifyLayersFromModules(modules: List<Module>): List<Layer> {
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
     * Анализирует зависимости между модулями
     */
    private fun analyzeModuleDependencies(modules: List<Module>): List<Dependency> {
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
     * Анализирует архитектурные проблемы
     */
    private fun analyzeArchitecturalIssues(structure: ProjectStructure): List<Map<String, Any>> {
        val findings = mutableListOf<Map<String, Any>>()

        // Проверяем циклические зависимости
        val cycles = detectDependencyCycles(structure.dependencies)
        cycles.forEach { cycle ->
            findings.add(
                mapOf(
                    "type" to "circular_dependency",
                    "severity" to "HIGH",
                    "message" to "Циклическая зависимость: ${cycle.joinToString(" -> ")}",
                    "suggestion" to "Рефакторинг для устранения циклической зависимости"
                )
            )
        }

        // Проверяем изолированные модули
        val isolatedModules = findIsolatedModules(structure)
        isolatedModules.forEach { module ->
            findings.add(
                mapOf(
                    "type" to "isolated_module",
                    "severity" to "MEDIUM",
                    "message" to "Модуль $module не имеет связей с другими модулями",
                    "suggestion" to "Проверьте, является ли это намеренным дизайном"
                )
            )
        }

        return findings
    }

    /**
     * Обнаруживает циклические зависимости
     */
    private fun detectDependencyCycles(dependencies: List<Dependency>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()

        // Для простоты ищем только прямые циклы A -> B -> A
        dependencies.forEach { dep ->
            val reverseDep = dependencies.find { it.from == dep.to && it.to == dep.from }
            if (reverseDep != null && dep.from < dep.to) { // Избегаем дубликатов
                cycles.add(listOf(dep.from, dep.to, dep.from))
            }
        }

        return cycles
    }

    /**
     * Находит изолированные модули
     */
    private fun findIsolatedModules(structure: ProjectStructure): List<String> {
        val connectedModules = mutableSetOf<String>()

        structure.dependencies.forEach { dep ->
            connectedModules.add(dep.from)
            connectedModules.add(dep.to)
        }

        return structure.modules.map { it.name }.filter { it !in connectedModules }
    }

    /**
     * Генерирует описание архитектуры через LLM
     */
    private suspend fun generateArchitectureDescription(structure: ProjectStructure): String {
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

    /**
     * Создает VirtualFile из обычного File с корректной реализацией всех необходимых методов
     */
    private fun createVirtualFileFromFile(file: File): VirtualFile {
        return object : VirtualFile() {
            override fun getName() = file.name
            override fun getFileSystem() =
                throw UnsupportedOperationException("FileSystem not supported for file-based VirtualFile")

            override fun getPath() = file.absolutePath
            override fun isWritable() = false
            override fun isDirectory() = file.isDirectory
            override fun isValid() = file.exists()
            override fun getParent(): VirtualFile? {
                val parentFile = file.parentFile
                return if (parentFile != null) createVirtualFileFromFile(parentFile) else null
            }

            override fun getChildren(): Array<VirtualFile> {
                return if (file.isDirectory) {
                    file.listFiles()?.map { createVirtualFileFromFile(it) }?.toTypedArray() ?: emptyArray()
                } else emptyArray()
            }

            override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) =
                throw UnsupportedOperationException("Write operations not supported")

            override fun contentsToByteArray() = file.readBytes()
            override fun getTimeStamp() = file.lastModified()
            override fun getLength() = file.length()
            override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {}
            override fun getInputStream() = file.inputStream()
        }
    }

    companion object {
        private const val ARCHITECTURE_SYSTEM_PROMPT = """
        Ты - архитектор программного обеспечения с глубоким пониманием паттернов проектирования.
        Анализируй архитектуру проектов и давай конструктивные рекомендации.
        Ссылайся на принципы SOLID, Clean Architecture и best practices.
        """
    }
}
