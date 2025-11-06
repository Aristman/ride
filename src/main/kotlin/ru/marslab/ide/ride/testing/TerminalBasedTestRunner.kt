package ru.marslab.ide.ride.testing

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.marslab.ide.ride.agent.impl.TerminalAgent
import ru.marslab.ide.ride.model.agent.AgentRequest
import ru.marslab.ide.ride.model.chat.ChatContext
import ru.marslab.ide.ride.settings.PluginSettings

/**
 * Реализация TestRunner через существующий TerminalAgent.
 * Формирует команду для Gradle/Maven и конвертирует результат в TestRunResult.
 */
class TerminalBasedTestRunner(
    private val structureProvider: ProjectStructureProvider
) : TestRunner {

    override suspend fun run(scope: String?): TestRunResult = withContext(Dispatchers.IO) {
        val structure = structureProvider.getProjectStructure()
        val project = ProjectManager.getInstance().openProjects.firstOrNull()
        val workingDir = structure.root.toFile().absolutePath

        val command = when (structure.buildSystem) {
            BuildSystem.GRADLE -> gradleCommand(scope)
            BuildSystem.MAVEN -> mavenCommand(scope)
            BuildSystem.DART -> {
                // Для Flutter-монореп fallback на android Gradle только если wrapper существует.
                val androidWrapperExists = if (isWindows())
                    structure.root.resolve("android/gradlew.bat").toFile().exists()
                else
                    structure.root.resolve("android/gradlew").toFile().exists()

                if (androidWrapperExists && !scope.isNullOrBlank() && scope.matches(Regex("[A-Za-z0-9_.]+"))) {
                    androidGradleCommand(scope)
                } else {
                    dartOrFlutterCommand(structure.root, scope)
                }
            }

            else -> gradleCommand(scope) // дефолт
        }

        val terminal = TerminalAgent()
        val ctxProject: Project = project ?: return@withContext TestRunResult(
            success = false,
            passed = 0,
            failed = 0,
            skipped = 0,
            durationMs = 0,
            reportText = "Нет открытого проекта для запуска тестов",
            errors = listOf("Project is null")
        )
        val settings = service<PluginSettings>()
        val req = AgentRequest(
            request = command,
            context = ChatContext(project = ctxProject, history = emptyList()),
            parameters = ru.marslab.ide.ride.model.llm.LLMParameters(
                temperature = settings.temperature,
                maxTokens = settings.maxTokens
            )
        )
        val resp = terminal.ask(req)

        val exit = (resp.metadata["exitCode"] as? Int) ?: (if (resp.success) 0 else 1)
        val stdout = (resp.formattedOutput as? String) ?: resp.content
        val parsed = quickParse(stdout)

        TestRunResult(
            success = exit == 0,
            passed = parsed.passed,
            failed = parsed.failed,
            skipped = parsed.skipped,
            durationMs = ((resp.metadata["executionTime"]) as? Number)?.toLong() ?: 0L,
            reportText = stdout,
            errors = if (exit == 0) emptyList() else listOf(resp.error ?: "")
        )
    }

    private fun gradleCommand(scope: String?): String {
        val base = if (isWindows()) "gradlew.bat test" else "./gradlew test"
        return if (!scope.isNullOrBlank()) "$base --tests \"$scope\"" else base
    }

    private fun mavenCommand(scope: String?): String {
        val base = "mvn -q -DskipITs=true test"
        return if (!scope.isNullOrBlank()) "$base -Dtest=$scope" else base
    }

    private fun dartCommand(scope: String?): String {
        val base = "dart test"
        if (!scope.isNullOrBlank() && scope.endsWith("_test.dart", ignoreCase = true)) {
            return "$base $scope"
        }
        return base
    }

    private fun flutterCommand(scope: String?): String {
        val base = "flutter test"
        if (!scope.isNullOrBlank() && scope.endsWith("_test.dart", ignoreCase = true)) {
            return "$base $scope"
        }
        return base
    }

    private fun dartOrFlutterCommand(root: java.nio.file.Path, scope: String?): String {
        return if (isFlutterProject(root)) flutterCommand(scope) else dartCommand(scope)
    }

    /**
     * Запуск Gradle в подкаталоге android (для Flutter/ Dart монореп).
     */
    private fun androidGradleCommand(scope: String): String {
        // Запускаем gradle wrapper напрямую из подпапки android, без shell-комбинаций '&&'
        val base = if (isWindows()) "android/gradlew.bat test" else "./android/gradlew test"
        return "$base --tests \"$scope\""
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun isFlutterProject(root: java.nio.file.Path): Boolean {
        val pubspec = root.resolve("pubspec.yaml")
        return try {
            if (java.nio.file.Files.exists(pubspec)) {
                val text = java.nio.file.Files.readString(pubspec)
                // Признаки Flutter-проекта: секция flutter:, зависимость flutter или dev_dependency flutter_test
                Regex("(?m)^flutter:\\s*$").containsMatchIn(text) ||
                        Regex(
                            "(?m)^\\s*dependencies:\\s*[\\s\\S]*?^\\s*flutter:\\s*\\n?\\s*sdk:\\s*flutter",
                            RegexOption.MULTILINE
                        ).containsMatchIn(text) ||
                        Regex(
                            "(?m)^\\s*dev_dependencies:\\s*[\\s\\S]*?^\\s*flutter_test:\\s*\\n?\\s*sdk:\\s*flutter",
                            RegexOption.MULTILINE
                        ).containsMatchIn(text)
            } else false
        } catch (_: Throwable) {
            false
        }
    }

    private data class Parsed(val passed: Int, val failed: Int, val skipped: Int)

    private fun quickParse(output: String): Parsed {
        // Простая эвристика: пытаемся найти числа в стандартных сводках Gradle/Maven
        val gradleRe = Regex(
            pattern = """(\r|\n)\s*(\d+) tests? completed,\s*(\d+) failed,\s*(\d+) skipped""",
            option = RegexOption.IGNORE_CASE
        )
        val mavenRe = Regex(
            pattern = """Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)""",
            option = RegexOption.IGNORE_CASE
        )

        gradleRe.find(output)?.let {
            val total = it.groupValues[2].toIntOrNull() ?: 0
            val failed = it.groupValues[3].toIntOrNull() ?: 0
            val skipped = it.groupValues[4].toIntOrNull() ?: 0
            val passed = (total - failed - skipped).coerceAtLeast(0)
            return Parsed(passed, failed, skipped)
        }
        mavenRe.find(output)?.let {
            val run = it.groupValues[1].toIntOrNull() ?: 0
            val failures = it.groupValues[2].toIntOrNull() ?: 0
            val errors = it.groupValues[3].toIntOrNull() ?: 0
            val skipped = it.groupValues[4].toIntOrNull() ?: 0
            val failed = failures + errors
            val passed = (run - failed - skipped).coerceAtLeast(0)
            return Parsed(passed, failed, skipped)
        }
        return Parsed(passed = 0, failed = if (output.contains("FAIL", true)) 1 else 0, skipped = 0)
    }
}
