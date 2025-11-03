package ru.marslab.ide.ride.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.a2a.AgentMessage
import ru.marslab.ide.ride.agent.a2a.BaseA2AAgent
import ru.marslab.ide.ride.agent.a2a.MessageBus
import ru.marslab.ide.ride.agent.a2a.MessagePayload
import ru.marslab.ide.ride.model.orchestrator.AgentType
import java.io.File

/**
 * A2A агент для анализа архитектуры проекта
 *
 * Анализирует структуру проекта, зависимости, паттерны архитектуры
 * и предоставляет рекомендации по улучшению архитектуры.
 */
class A2AArchitectureToolAgent : BaseA2AAgent(
    agentType = AgentType.ARCHITECTURE_ANALYSIS,
    a2aAgentId = "a2a-architecture-agent",
    supportedMessageTypes = setOf("ARCHITECTURE_ANALYSIS_REQUEST"),
    publishedEventTypes = setOf("TOOL_EXECUTION_STARTED", "TOOL_EXECUTION_COMPLETED", "TOOL_EXECUTION_FAILED")
) {

    override suspend fun handleRequest(
        request: AgentMessage.Request,
        messageBus: MessageBus
    ): AgentMessage.Response? {
        if (request.messageType != "ARCHITECTURE_ANALYSIS_REQUEST") {
            return createErrorResponse(request.id, "Unsupported message type: ${request.messageType}")
        }

        val data = (request.payload as? MessagePayload.CustomPayload)?.data ?: emptyMap<String, Any>()
        val projectPath = data["project_path"] as? String ?: ""
        val includeDependencies = data["include_dependencies"] as? Boolean ?: true
        val includePatterns = data["include_patterns"] as? Boolean ?: true

        return try {
            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_STARTED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "STARTED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis()
                )
            )

            val analysis = withContext(Dispatchers.IO) {
                analyzeArchitecture(projectPath, includeDependencies, includePatterns)
            }

            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_COMPLETED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "COMPLETED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    result = "Architecture analysis completed"
                )
            )

            AgentMessage.Response(
                senderId = a2aAgentId,
                requestId = request.id,
                success = true,
                payload = MessagePayload.CustomPayload(
                    type = "ARCHITECTURE_ANALYSIS_RESULT",
                    data = mapOf(
                        "analysis" to analysis,
                        "metadata" to mapOf(
                            "agent" to "ARCHITECTURE_ANALYSIS",
                            "project_path" to projectPath,
                            "analysis_timestamp" to System.currentTimeMillis()
                        )
                    )
                )
            )
        } catch (e: Exception) {
            logger.error("Error in architecture analysis", e)

            publishEvent(
                messageBus = messageBus,
                eventType = "TOOL_EXECUTION_FAILED",
                payload = MessagePayload.ExecutionStatusPayload(
                    status = "FAILED",
                    agentId = a2aAgentId,
                    requestId = request.id,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            )

            createErrorResponse(request.id, "Architecture analysis failed: ${e.message}")
        }
    }

    private fun createErrorResponse(requestId: String, error: String): AgentMessage.Response {
        return AgentMessage.Response(
            senderId = a2aAgentId,
            requestId = requestId,
            success = false,
            payload = MessagePayload.ErrorPayload(error = error),
            error = error
        )
    }

    private suspend fun analyzeArchitecture(
        projectPath: String,
        includeDependencies: Boolean,
        includePatterns: Boolean
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        val root = if (projectPath.isBlank()) File(".") else File(projectPath)
        if (!root.exists()) {
            return@withContext mapOf(
                "error" to "Project path does not exist: $projectPath",
                "project_type" to "unknown"
            )
        }

        val projectType = detectProjectType(root)
        val sourceFiles = findSourceFiles(root)
        val structure = analyzeProjectStructure(root, sourceFiles)
        val dependencies = if (includeDependencies) analyzeDependencies(root, sourceFiles) else emptyMap<String, Any>()
        val patterns = if (includePatterns) analyzePatterns(sourceFiles) else emptyMap<String, Any>()

        mapOf(
            "project_type" to projectType,
            "total_source_files" to sourceFiles.size,
            "structure" to structure,
            "dependencies" to dependencies,
            "patterns" to patterns,
            "recommendations" to generateRecommendations(structure, dependencies, patterns),
            "complexity_metrics" to calculateComplexityMetrics(sourceFiles)
        )
    }

    private fun detectProjectType(root: File): String {
        return when {
            File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists() -> "kotlin_gradle"
            File(root, "pom.xml").exists() -> "maven"
            File(root, "package.json").exists() -> "node"
            root.resolve("src/main/kotlin").exists() -> "kotlin"
            root.resolve("src/main/java").exists() -> "java"
            else -> "generic"
        }
    }

    private fun findSourceFiles(root: File): List<File> {
        val sourceExtensions = setOf(".kt", ".java", ".scala", ".groovy")
        val sourceFiles = mutableListOf<File>()

        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.startsWith(".") && file.name != "build") {
                    scan(file)
                } else if (file.isFile && sourceExtensions.any { ext -> file.name.endsWith(ext) }) {
                    sourceFiles.add(file)
                }
            }
        }

        scan(root)
        return sourceFiles
    }

    private fun analyzeProjectStructure(root: File, sourceFiles: List<File>): Map<String, Any> {
        val modules = mutableListOf<String>()
        val packages = mutableSetOf<String>()
        val layers = mutableMapOf<String, Int>()

        sourceFiles.forEach { file ->
            val relativePath = file.relativeTo(root).path
            val pathParts = relativePath.split(File.separator)

            // Определение модулей
            if (pathParts.contains("src") && pathParts.size > 2) {
                val moduleIndex = pathParts.indexOf("src")
                if (moduleIndex > 0) {
                    modules.add(pathParts[moduleIndex - 1])
                }
            }

            // Извлечение пакетов
            if (file.name.endsWith(".kt") || file.name.endsWith(".java")) {
                val content = file.readText()
                val packageMatch = Regex("""package\s+([a-zA-Z_][a-zA-Z0-9_.]*)""").find(content)
                packageMatch?.let {
                    packages.add(it.groupValues[1])
                }
            }

            // Определение слоев
            when {
                relativePath.contains("controller") || relativePath.contains("api") ->
                    layers["presentation"] = layers.getOrDefault("presentation", 0) + 1
                relativePath.contains("service") || relativePath.contains("business") ->
                    layers["business"] = layers.getOrDefault("business", 0) + 1
                relativePath.contains("repository") || relativePath.contains("dao") ->
                    layers["data"] = layers.getOrDefault("data", 0) + 1
            }
        }

        return mapOf(
            "modules" to modules.distinct(),
            "packages" to packages.sorted(),
            "layers" to layers,
            "depth" to calculateProjectDepth(sourceFiles, root),
            "file_distribution" to sourceFiles.groupBy { it.extension }
        )
    }

    private fun analyzeDependencies(root: File, sourceFiles: List<File>): Map<String, Any> {
        val externalDependencies = mutableSetOf<String>()
        val internalDependencies = mutableSetOf<String>()
        val dependencyGraph = mutableMapOf<String, MutableSet<String>>()

        sourceFiles.forEach { file ->
            val content = file.readText()
            val imports = extractImports(content)

            imports.forEach { import ->
                when {
                    import.startsWith("kotlin.") || import.startsWith("java.") ||
                    import.startsWith("javax.") || import.startsWith("org.") ||
                    import.startsWith("com.") && !import.contains("marslab") -> {
                        externalDependencies.add(import)
                    }
                    else -> {
                        internalDependencies.add(import)
                        val packageName = extractPackageName(file)
                        dependencyGraph.getOrPut(packageName) { mutableSetOf() }.add(import)
                    }
                }
            }
        }

        return mapOf(
            "external_dependencies" to externalDependencies.sorted(),
            "internal_dependencies" to internalDependencies.sorted(),
            "dependency_graph" to dependencyGraph,
            "circular_dependencies" to detectCircularDependencies(dependencyGraph)
        )
    }

    private fun analyzePatterns(sourceFiles: List<File>): Map<String, Any> {
        val patterns = mutableMapOf<String, Int>()
        val violations = mutableListOf<Map<String, String>>()

        sourceFiles.forEach { file ->
            val content = file.readText()
            val className = file.nameWithoutExtension

            // Проверка паттернов
            when {
                content.contains("interface") && content.contains("class") -> {
                    patterns["interface_segregation"] = patterns.getOrDefault("interface_segregation", 0) + 1
                }
                content.contains("abstract") && content.contains("override") -> {
                    patterns["polymorphism"] = patterns.getOrDefault("polymorphism", 0) + 1
                }
                content.contains("private") && content.contains("public") -> {
                    patterns["encapsulation"] = patterns.getOrDefault("encapsulation", 0) + 1
                }
            }

            // Проверка нарушений
            if (content.lines().count { it.trim().isNotEmpty() } > 200) {
                violations.add(mapOf(
                    "file" to file.path,
                    "violation" to "large_class",
                    "description" to "Class exceeds 200 lines"
                ))
            }

            if (content.contains("static") && content.contains("mutable")) {
                violations.add(mapOf(
                    "file" to file.path,
                    "violation" to "static_mutable_state",
                    "description" to "Static mutable state detected"
                ))
            }
        }

        return mapOf(
            "detected_patterns" to patterns,
            "violations" to violations,
            "pattern_adherence" to calculatePatternAdherence(patterns, sourceFiles.size)
        )
    }

    private fun generateRecommendations(
        structure: Map<String, Any>,
        dependencies: Map<String, Any>,
        patterns: Map<String, Any>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        val totalFiles = structure["total_source_files"] as? Int ?: 0
        if (totalFiles > 100) {
            recommendations.add("Consider modularizing the large project into smaller modules")
        }

        val layers = structure["layers"] as? Map<String, Int> ?: emptyMap()
        if (layers.keys.size < 3) {
            recommendations.add("Consider implementing proper layered architecture")
        }

        val violations = (patterns["violations"] as? List<*>)?.size ?: 0
        if (violations > 5) {
            recommendations.add("Address code quality violations to improve maintainability")
        }

        val circularDeps = dependencies["circular_dependencies"] as? List<*> ?: emptyList<Any>()
        if (circularDeps.isNotEmpty()) {
            recommendations.add("Resolve circular dependencies to improve modularity")
        }

        return recommendations
    }

    private fun calculateComplexityMetrics(sourceFiles: List<File>): Map<String, Any> {
        var totalLines = 0
        var totalClasses = 0
        var totalMethods = 0

        sourceFiles.forEach { file ->
            val content = file.readText()
            totalLines += content.lines().size
            totalClasses += Regex("""class\s+\w+""").findAll(content).count()
            totalMethods += Regex("""fun\s+\w+|def\s+\w+""").findAll(content).count()
        }

        return mapOf(
            "total_lines" to totalLines,
            "total_classes" to totalClasses,
            "total_methods" to totalMethods,
            "average_lines_per_file" to if (sourceFiles.isNotEmpty()) totalLines / sourceFiles.size else 0,
            "average_methods_per_class" to if (totalClasses > 0) totalMethods / totalClasses else 0
        )
    }

    // Вспомогательные функции
    private fun extractImports(content: String): List<String> {
        return Regex("""import\s+([a-zA-Z_][a-zA-Z0-9_.]*)""").findAll(content)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun extractPackageName(file: File): String {
        val content = file.readText()
        return Regex("""package\s+([a-zA-Z_][a-zA-Z0-9_.]*)""").find(content)?.groupValues?.get(1)
            ?: "default"
    }

    private fun calculateProjectDepth(sourceFiles: List<File>, root: File): Int {
        return sourceFiles.map { it.relativeTo(root).path.split(File.separator).size }.maxOrNull() ?: 0
    }

    private fun detectCircularDependencies(dependencyGraph: Map<String, Set<String>>): List<List<String>> {
        // Простая реализация обнаружения циклов
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun dfs(node: String, path: List<String>) {
            if (node in recursionStack) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    cycles.add(path.subList(cycleStart, path.size) + node)
                }
                return
            }

            if (node in visited) return

            visited.add(node)
            recursionStack.add(node)

            dependencyGraph[node]?.forEach { dependency ->
                dfs(dependency, path + node)
            }

            recursionStack.remove(node)
        }

        dependencyGraph.keys.forEach { node ->
            if (node !in visited) {
                dfs(node, emptyList())
            }
        }

        return cycles
    }

    private fun calculatePatternAdherence(patterns: Map<String, Int>, totalFiles: Int): Double {
        val totalPatterns = patterns.values.sum()
        return if (totalFiles > 0) totalPatterns.toDouble() / totalFiles else 0.0
    }
}
