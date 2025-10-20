package ru.marslab.ide.ride.agent.tools

import ru.marslab.ide.ride.agent.BaseToolAgent
import ru.marslab.ide.ride.agent.ValidationResult
import ru.marslab.ide.ride.model.orchestrator.AgentType
import ru.marslab.ide.ride.model.orchestrator.ExecutionContext
import ru.marslab.ide.ride.model.tool.*
import java.io.File

/**
 * Агент для анализа архитектуры проекта
 * 
 * Capabilities:
 * - architecture_analysis - анализ архитектуры
 * - dependency_analysis - анализ зависимостей
 * - layer_detection - определение слоев
 */
class ArchitectureToolAgent : BaseToolAgent(
    agentType = AgentType.ARCHITECTURE_ANALYSIS,
    toolCapabilities = setOf(
        "architecture_analysis",
        "dependency_analysis",
        "layer_detection"
    )
) {
    
    override fun getDescription(): String {
        return "Анализирует архитектуру проекта и структуру зависимостей"
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
        
        logger.info("Analyzing architecture for ${files.size} files")
        
        val packages = mutableMapOf<String, PackageInfo>()
        val dependencies = mutableListOf<Dependency>()
        val layers = mutableSetOf<String>()
        
        for (filePath in files) {
            val file = File(filePath)
            if (!file.exists() || !file.isFile) continue
            
            val fileInfo = analyzeFile(file)
            
            // Собираем информацию о пакетах
            val packageName = fileInfo.packageName
            if (packageName.isNotEmpty()) {
                val pkgInfo = packages.getOrPut(packageName) {
                    PackageInfo(packageName, mutableListOf(), mutableSetOf())
                }
                pkgInfo.files.add(filePath)
                pkgInfo.imports.addAll(fileInfo.imports)
                
                // Определяем слой по имени пакета
                val layer = detectLayer(packageName)
                if (layer != null) {
                    layers.add(layer)
                }
            }
            
            // Создаем зависимости
            fileInfo.imports.forEach { import ->
                dependencies.add(
                    Dependency(
                        from = packageName,
                        to = extractPackage(import),
                        type = "import"
                    )
                )
            }
        }
        
        // Анализируем циклические зависимости
        val cycles = detectCycles(dependencies)
        
        // Анализируем нарушения слоев
        val layerViolations = detectLayerViolations(dependencies, layers)
        
        val findings = mutableListOf<Finding>()
        
        // Добавляем находки для циклических зависимостей
        cycles.forEach { cycle ->
            findings.add(
                Finding(
                    file = "",
                    line = 0,
                    severity = Severity.HIGH,
                    category = "circular_dependency",
                    message = "Циклическая зависимость: ${cycle.joinToString(" -> ")}",
                    suggestion = "Рефакторинг для устранения циклической зависимости"
                )
            )
        }
        
        // Добавляем находки для нарушений слоев
        layerViolations.forEach { violation ->
            findings.add(
                Finding(
                    file = "",
                    line = 0,
                    severity = Severity.MEDIUM,
                    category = "layer_violation",
                    message = violation,
                    suggestion = "Следуйте принципам слоистой архитектуры"
                )
            )
        }
        
        logger.info("Architecture analysis completed: ${packages.size} packages, ${layers.size} layers")
        
        return StepResult.success(
            output = StepOutput.of(
                "packages" to packages.keys.toList(),
                "package_count" to packages.size,
                "layers" to layers.toList(),
                "dependencies" to dependencies,
                "cycles" to cycles,
                "findings" to findings
            ),
            metadata = mapOf(
                "files_analyzed" to files.size,
                "cycles_found" to cycles.size,
                "layer_violations" to layerViolations.size
            )
        )
    }
    
    private fun analyzeFile(file: File): FileInfo {
        var packageName = ""
        val imports = mutableSetOf<String>()
        
        try {
            file.readLines().forEach { line ->
                val trimmed = line.trim()
                
                if (trimmed.startsWith("package ")) {
                    packageName = trimmed.removePrefix("package ").removeSuffix(";").trim()
                }
                
                if (trimmed.startsWith("import ")) {
                    val import = trimmed.removePrefix("import ").removeSuffix(";").trim()
                    if (!import.startsWith("java.") && !import.startsWith("kotlin.")) {
                        imports.add(import)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error analyzing file ${file.absolutePath}", e)
        }
        
        return FileInfo(packageName, imports)
    }
    
    private fun extractPackage(import: String): String {
        return import.substringBeforeLast(".", "")
    }
    
    private fun detectLayer(packageName: String): String? {
        return when {
            packageName.contains(".ui.") || packageName.endsWith(".ui") -> "presentation"
            packageName.contains(".service.") || packageName.endsWith(".service") -> "service"
            packageName.contains(".agent.") || packageName.endsWith(".agent") -> "domain"
            packageName.contains(".model.") || packageName.endsWith(".model") -> "model"
            packageName.contains(".integration.") || packageName.endsWith(".integration") -> "infrastructure"
            packageName.contains(".orchestrator.") || packageName.endsWith(".orchestrator") -> "orchestration"
            else -> null
        }
    }
    
    private fun detectCycles(dependencies: List<Dependency>): List<List<String>> {
        // Упрощенный алгоритм поиска циклов
        val graph = dependencies.groupBy { it.from }
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
    
    private fun detectLayerViolations(dependencies: List<Dependency>, layers: Set<String>): List<String> {
        val violations = mutableListOf<String>()
        
        // Правила слоев (presentation -> service -> domain -> model)
        val layerOrder = listOf("presentation", "service", "orchestration", "domain", "model", "infrastructure")
        
        dependencies.forEach { dep ->
            val fromLayer = detectLayer(dep.from)
            val toLayer = detectLayer(dep.to)
            
            if (fromLayer != null && toLayer != null) {
                val fromIndex = layerOrder.indexOf(fromLayer)
                val toIndex = layerOrder.indexOf(toLayer)
                
                // Нарушение: нижний слой зависит от верхнего
                if (fromIndex > toIndex && toIndex >= 0) {
                    violations.add("$fromLayer слой не должен зависеть от $toLayer слоя (${dep.from} -> ${dep.to})")
                }
            }
        }
        
        return violations
    }
    
    private data class FileInfo(
        val packageName: String,
        val imports: Set<String>
    )
    
    private data class PackageInfo(
        val name: String,
        val files: MutableList<String>,
        val imports: MutableSet<String>
    )
    
    private data class Dependency(
        val from: String,
        val to: String,
        val type: String
    )
}
