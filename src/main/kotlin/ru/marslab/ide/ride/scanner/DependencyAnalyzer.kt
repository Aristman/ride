package ru.marslab.ide.ride.scanner

import ru.marslab.ide.ride.model.scanner.ProjectType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Анализатор зависимостей проекта
 */
object DependencyAnalyzer {

    /**
     * Информация о зависимости
     */
    data class Dependency(
        val name: String,
        val version: String?,
        val type: DependencyType,
        val scope: String? = null,
        val sourceFile: String
    )

    /**
     * Тип зависимости
     */
    enum class DependencyType {
        COMPILE,     // Компиляционная зависимость
        RUNTIME,     // Время выполнения
        TEST,        // Тестовая зависимость
        PROVIDED,    // Предоставленная зависимость
        DEVELOPMENT, // Зависимость для разработки
        PLUGIN,      // Плагин
        BUILTIN      // Встроенная зависимость
    }

    /**
     * Результат анализа зависимостей
     */
    data class DependencyAnalysis(
        val dependencies: List<Dependency>,
        val buildTools: List<String>,
        val frameworks: List<String>,
        val testingFrameworks: List<String>,
        val totalDependencies: Int
    )

    /**
     * Анализирует зависимости проекта
     */
    fun analyzeDependencies(projectPath: Path, projectType: ProjectType): DependencyAnalysis {
        val dependencies = mutableListOf<Dependency>()
        val buildTools = mutableListOf<String>()
        val frameworks = mutableListOf<String>()
        val testingFrameworks = mutableListOf<String>()

        when (projectType) {
            ProjectType.MAVEN -> analyzeMavenDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
            ProjectType.GRADLE, ProjectType.GRADLE_KOTLIN -> analyzeGradleDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
            ProjectType.PYTHON -> analyzePythonDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
            ProjectType.NODE_JS -> analyzeNodeDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
            ProjectType.RUST -> analyzeRustDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
            ProjectType.SPRING_BOOT -> {
                analyzeMavenDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
                frameworks.add("Spring Boot")
            }
            ProjectType.ANDROID -> {
                analyzeGradleDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
                frameworks.add("Android")
            }
            else -> analyzeGenericDependencies(projectPath, dependencies, buildTools, frameworks, testingFrameworks)
        }

        return DependencyAnalysis(
            dependencies = dependencies.distinctBy { "${it.name}:${it.version}" },
            buildTools = buildTools.distinct(),
            frameworks = frameworks.distinct(),
            testingFrameworks = testingFrameworks.distinct(),
            totalDependencies = dependencies.size
        )
    }

    /**
     * Анализ Maven зависимостей
     */
    private fun analyzeMavenDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        buildTools.add("Maven")

        val pomFile = projectPath.resolve("pom.xml")
        if (!Files.exists(pomFile)) return

        try {
            val content = Files.readString(pomFile)

            // Анализ зависимостей
            val dependencyRegex = """<dependency>[\s\S]*?<groupId>([^<]+)</groupId>[\s\S]*?<artifactId>([^<]+)</artifactId>([\s\S]*?<version>([^<]+)</version>)?""".toRegex()

            dependencyRegex.findAll(content).forEach { match ->
                val groupId = match.groupValues[1].trim()
                val artifactId = match.groupValues[2].trim()
                val version = match.groupValues[4].takeIf { it.isNotEmpty() }?.trim()
                val name = "$groupId:$artifactId"

                val scope = when {
                    match.range.first != -1 && content.substring(0, match.range.first).contains("<scope>test</scope>") -> "test"
                    match.range.first != -1 && content.substring(0, match.range.first).contains("<scope>provided</scope>") -> "provided"
                    match.range.first != -1 && content.substring(0, match.range.first).contains("<scope>runtime</scope>") -> "runtime"
                    else -> "compile"
                }

                val type = when (scope) {
                    "test" -> DependencyType.TEST
                    "provided" -> DependencyType.PROVIDED
                    "runtime" -> DependencyType.RUNTIME
                    else -> DependencyType.COMPILE
                }

                dependencies.add(Dependency(name, version, type, scope, "pom.xml"))
            }

            // Определение фреймворков
            when {
                content.contains("spring-boot") -> frameworks.add("Spring Boot")
                content.contains("spring-framework") -> frameworks.add("Spring Framework")
                content.contains("hibernate") -> frameworks.add("Hibernate")
                content.contains("junit") -> testingFrameworks.add("JUnit")
                content.contains("mockito") -> testingFrameworks.add("Mockito")
                content.contains("testng") -> testingFrameworks.add("TestNG")
                content.contains("jakarta.servlet") -> frameworks.add("Servlet API")
                content.contains("micronaut") -> frameworks.add("Micronaut")
                content.contains("quarkus") -> frameworks.add("Quarkus")
            }

        } catch (e: Exception) {
            // Логируем ошибку, но продолжаем анализ
        }
    }

    /**
     * Анализ Gradle зависимостей
     */
    private fun analyzeGradleDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        buildTools.add("Gradle")

        // Ищем build.gradle или build.gradle.kts
        val buildFiles = listOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")

        buildFiles.forEach { fileName ->
            val buildFile = projectPath.resolve(fileName)
            if (!Files.exists(buildFile)) return@forEach

            try {
                val content = Files.readString(buildFile)

                // Анализ зависимостей
                val dependencyPatterns = listOf(
                    """implementation\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """compile\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """api\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """testImplementation\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """testCompile\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """runtimeOnly\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex(),
                    """compileOnly\s+['"]([^:]+):([^:]+):([^'"]+)['"]""".toRegex()
                )

                dependencyPatterns.forEach { pattern ->
                    pattern.findAll(content).forEach { match ->
                        val group = match.groupValues[1].trim()
                        val artifact = match.groupValues[2].trim()
                        val version = match.groupValues[3].trim()
                        val name = "$group:$artifact"

                        val configuration = match.value.split('(').first().trim()
                        val scope = when {
                            configuration.startsWith("test") -> "test"
                            configuration == "runtimeOnly" -> "runtime"
                            configuration == "compileOnly" -> "provided"
                            else -> "compile"
                        }

                        val type = when (scope) {
                            "test" -> DependencyType.TEST
                            "provided" -> DependencyType.PROVIDED
                            "runtime" -> DependencyType.RUNTIME
                            else -> DependencyType.COMPILE
                        }

                        dependencies.add(Dependency(name, version, type, scope, fileName))
                    }
                }

                // Плагины Gradle
                val pluginPattern = """(?:id|apply plugin)\s+['"]([^'"]+)['"]""".toRegex()
                pluginPattern.findAll(content).forEach { match ->
                    val pluginName = match.groupValues[1].trim()
                    dependencies.add(Dependency(pluginName, null, DependencyType.PLUGIN, null, fileName))
                }

                // Определение фреймворков
                when {
                    content.contains("spring-boot") -> frameworks.add("Spring Boot")
                    content.contains("org.springframework") -> frameworks.add("Spring Framework")
                    content.contains("kotlin") -> frameworks.add("Kotlin")
                    content.contains("junit") -> testingFrameworks.add("JUnit")
                    content.contains("mockito") -> testingFrameworks.add("Mockito")
                    content.contains("testng") -> testingFrameworks.add("TestNG")
                    content.contains("android") -> frameworks.add("Android")
                    content.contains("java-library") -> frameworks.add("Java Library")
                    content.contains("application") -> frameworks.add("Java Application")
                }

            } catch (e: Exception) {
                // Логируем ошибку, но продолжаем анализ
            }
        }
    }

    /**
     * Анализ Python зависимостей
     */
    private fun analyzePythonDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        buildTools.add("Python")

        // Анализ requirements.txt
        val requirementsFile = projectPath.resolve("requirements.txt")
        if (Files.exists(requirementsFile)) {
            try {
                val content = Files.readString(requirementsFile)
                content.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("==", ">=", "<=", ">", "<", "!=")
                        val name = parts.first().trim()
                        val version = parts.getOrNull(1)?.trim()

                        dependencies.add(Dependency(name, version, DependencyType.COMPILE, null, "requirements.txt"))

                        // Определение фреймворков
                        when {
                            name.contains("django") -> frameworks.add("Django")
                            name.contains("flask") -> frameworks.add("Flask")
                            name.contains("fastapi") -> frameworks.add("FastAPI")
                            name.contains("pytest") -> testingFrameworks.add("pytest")
                            name.contains("unittest") -> testingFrameworks.add("unittest")
                            name.contains("numpy") -> frameworks.add("NumPy")
                            name.contains("pandas") -> frameworks.add("Pandas")
                            name.contains("tensorflow") -> frameworks.add("TensorFlow")
                            name.contains("pytorch") -> frameworks.add("PyTorch")
                        }
                    }
                }
            } catch (e: Exception) {
                // Логируем ошибку, но продолжаем анализ
            }
        }

        // Анализ pyproject.toml
        val pyprojectFile = projectPath.resolve("pyproject.toml")
        if (Files.exists(pyprojectFile)) {
            try {
                val content = Files.readString(pyprojectFile)

                // Анализ зависимостей в [project.dependencies]
                val depsPattern = """\[\s*project\.dependencies\s*\]([\s\S]*?)(?=\[|\z)""".toRegex()
                depsPattern.find(content)?.let { match ->
                    match.value.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("[") && !trimmed.startsWith("#")) {
                            val dependency = trimmed.removePrefix("\"").removePrefix("'").removeSuffix("\"").removeSuffix("'")
                            if (dependency.isNotEmpty()) {
                                val parts = dependency.split("==", ">=", "<=", ">", "<", "!=")
                                val name = parts.first().trim()
                                val version = parts.getOrNull(1)?.trim()
                                dependencies.add(Dependency(name, version, DependencyType.COMPILE, null, "pyproject.toml"))
                            }
                        }
                    }
                }

                if (content.contains("poetry")) buildTools.add("Poetry")
                if (content.contains("setuptools")) buildTools.add("Setuptools")
            } catch (e: Exception) {
                // Логируем ошибку, но продолжаем анализ
            }
        }
    }

    /**
     * Анализ Node.js зависимостей
     */
    private fun analyzeNodeDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        buildTools.add("Node.js")

        val packageJsonFile = projectPath.resolve("package.json")
        if (!Files.exists(packageJsonFile)) return

        try {
            val content = Files.readString(packageJsonFile)

            // Простая regex для анализа JSON (без парсинга целого JSON)
            val dependenciesPattern = """"dependencies"\s*:\s*\{([^}]+)\}""".toRegex()
            val devDependenciesPattern = """"devDependencies"\s*:\s*\{([^}]+)\}""".toRegex()
            val peerDependenciesPattern = """"peerDependencies"\s*:\s*\{([^}]+)\}""".toRegex()

            processDependenciesSection(dependenciesPattern.find(content)?.groupValues?.get(1),
                                      DependencyType.COMPILE, "package.json", dependencies, frameworks, testingFrameworks)

            processDependenciesSection(devDependenciesPattern.find(content)?.groupValues?.get(1),
                                      DependencyType.DEVELOPMENT, "package.json", dependencies, frameworks, testingFrameworks)

            processDependenciesSection(peerDependenciesPattern.find(content)?.groupValues?.get(1),
                                      DependencyType.RUNTIME, "package.json", dependencies, frameworks, testingFrameworks)

            // Определение дополнительных инструментов
            when {
                content.contains("npm") -> buildTools.add("npm")
                content.contains("yarn") -> buildTools.add("yarn")
                content.contains("pnpm") -> buildTools.add("pnpm")
                content.contains("webpack") -> buildTools.add("webpack")
                content.contains("vite") -> buildTools.add("Vite")
                content.contains("rollup") -> buildTools.add("Rollup")
                content.contains("parcel") -> buildTools.add("Parcel")
                content.contains("babel") -> buildTools.add("Babel")
                content.contains("typescript") -> frameworks.add("TypeScript")
                content.contains("react") -> frameworks.add("React")
                content.contains("vue") -> frameworks.add("Vue.js")
                content.contains("angular") -> frameworks.add("Angular")
                content.contains("express") -> frameworks.add("Express.js")
                content.contains("jest") -> testingFrameworks.add("Jest")
                content.contains("mocha") -> testingFrameworks.add("Mocha")
                content.contains("jasmine") -> testingFrameworks.add("Jasmine")
            }

        } catch (e: Exception) {
            // Логируем ошибку, но продолжаем анализ
        }
    }

    /**
     * Анализ Rust зависимостей
     */
    private fun analyzeRustDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        buildTools.add("Cargo")

        val cargoFile = projectPath.resolve("Cargo.toml")
        if (!Files.exists(cargoFile)) return

        try {
            val content = Files.readString(cargoFile)

            // Анализ [dependencies]
            val depsPattern = """\[dependencies\]([\s\S]*?)(?=\[|\z)""".toRegex()
            depsPattern.find(content)?.let { match ->
                match.value.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("[") && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", " ", limit = 2)
                        if (parts.size >= 2) {
                            val name = parts[0].trim()
                            val version = parts[1].trim().removePrefix("\"").removeSuffix("\"")
                            dependencies.add(Dependency(name, version, DependencyType.COMPILE, null, "Cargo.toml"))
                        }
                    }
                }
            }

            // Анализ [dev-dependencies]
            val devDepsPattern = """\[dev-dependencies\]([\s\S]*?)(?=\[|\z)""".toRegex()
            devDepsPattern.find(content)?.let { match ->
                match.value.lines().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("[") && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", " ", limit = 2)
                        if (parts.size >= 2) {
                            val name = parts[0].trim()
                            val version = parts[1].trim().removePrefix("\"").removeSuffix("\"")
                            dependencies.add(Dependency(name, version, DependencyType.DEVELOPMENT, null, "Cargo.toml"))
                        }
                    }
                }
            }

            // Определение фреймворков
            when {
                content.contains("tokio") -> frameworks.add("Tokio")
                content.contains("serde") -> frameworks.add("Serde")
                content.contains("actix") -> frameworks.add("Actix")
                content.contains("rocket") -> frameworks.add("Rocket")
                content.contains("warp") -> frameworks.add("Warp")
                content.contains("criterion") -> testingFrameworks.add("Criterion")
                content.contains("proptest") -> testingFrameworks.add("proptest")
            }

        } catch (e: Exception) {
            // Логируем ошибку, но продолжаем анализ
        }
    }

    /**
     * Анализ зависимостей для generic проектов
     */
    private fun analyzeGenericDependencies(
        projectPath: Path,
        dependencies: MutableList<Dependency>,
        buildTools: MutableList<String>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        // Ищем общие файлы зависимостей
        val dependencyFiles = listOf(
            "package.json", "composer.json", "Gemfile", "requirements.txt",
            "pom.xml", "build.gradle", "Cargo.toml", "go.mod"
        )

        dependencyFiles.forEach { fileName ->
            val file = projectPath.resolve(fileName)
            if (Files.exists(file)) {
                buildTools.add(fileName)
                // Базовый анализ без детальной парсинга
                dependencies.add(Dependency("Dependencies in $fileName", null, DependencyType.BUILTIN, null, fileName))
            }
        }
    }

    /**
     * Обрабатывает секцию зависимостей в package.json
     */
    private fun processDependenciesSection(
        section: String?,
        type: DependencyType,
        sourceFile: String,
        dependencies: MutableList<Dependency>,
        frameworks: MutableList<String>,
        testingFrameworks: MutableList<String>
    ) {
        section?.let { content ->
            content.lines().forEach { line ->
                val trimmed = line.trim().removeSuffix(",")
                if (trimmed.isNotEmpty() && !trimmed.startsWith("//")) {
                    val match = """"([^"]+)"\s*:\s*"([^"]+)"""".toRegex().find(trimmed)
                    match?.let {
                        val name = it.groupValues[1].trim()
                        val version = it.groupValues[2].trim()
                        dependencies.add(Dependency(name, version, type, null, sourceFile))

                        // Определение фреймворков
                        when {
                            name.contains("react") -> frameworks.add("React")
                            name.contains("vue") -> frameworks.add("Vue.js")
                            name.contains("angular") -> frameworks.add("Angular")
                            name.contains("express") -> frameworks.add("Express.js")
                            name.contains("jest") -> testingFrameworks.add("Jest")
                            name.contains("mocha") -> testingFrameworks.add("Mocha")
                            name.contains("jasmine") -> testingFrameworks.add("Jasmine")
                            name.contains("webpack") -> frameworks.add("webpack")
                            name.contains("vite") -> frameworks.add("Vite")
                        }
                    }
                }
            }
        }
    }
}