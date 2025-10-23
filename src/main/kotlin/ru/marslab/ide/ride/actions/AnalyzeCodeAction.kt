package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.codeanalysis.AnalysisType
import ru.marslab.ide.ride.model.codeanalysis.CodeAnalysisRequest
import ru.marslab.ide.ride.model.codeanalysis.CodeAnalysisResult
import ru.marslab.ide.ride.model.codeanalysis.ReportFormat
import java.io.File

/**
 * Action для запуска анализа кода проекта
 */
class AnalyzeCodeAction : AnAction("Analyze Code", "Запустить анализ кода проекта", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Отладочная информация о проекте
        println("=== Project Info ===")
        println("Project name: ${project.name}")
        println("Base path: ${project.basePath}")
        println("Project file: ${project.projectFile}")
        println("Project dir: ${project.projectFilePath}")
        println("Is open: ${project.isOpen}")
        println("Is initialized: ${project.isInitialized}")

        // Проверяем модули
        val moduleManager = ModuleManager.getInstance(project)
        println("\n=== Modules (${moduleManager.modules.size}) ===")
        moduleManager.modules.forEach { module ->
            println("Module: ${module.name}")
            println("  Path: ${module.moduleFilePath}")
            val rootManager = ModuleRootManager.getInstance(module)
            println("  Content roots (${rootManager.contentRoots.size}):")
            rootManager.contentRoots.forEach { root ->
                println("    - ${root.path}")
            }
        }
        println("===================\n")

        // Показываем диалог выбора типов анализа
        val analysisTypes = showAnalysisTypeDialog(project) ?: return

        // Запускаем анализ в фоновом режиме
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Анализ кода", true) {
            private var analysisResult: CodeAnalysisResult? = null
            private var reportFile: File? = null
            private var error: Exception? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Инициализация анализа..."
                indicator.isIndeterminate = false

                try {
                    val agent = AgentFactory.createCodeAnalysisAgent(project)

                    val request = CodeAnalysisRequest(
                        projectPath = project.basePath ?: "",
                        analysisTypes = analysisTypes
                    )

                    indicator.text = "Анализ кода..."
                    indicator.fraction = 0.3

                    val result = runBlocking {
                        agent.analyzeProject(request)
                    }

                    indicator.fraction = 0.8

                    // Генерируем отчет
                    indicator.text = "Генерация отчета..."
                    val report = agent.generateReport(result, ReportFormat.MARKDOWN)

                    indicator.fraction = 0.9

                    // Сохраняем отчет
                    val file = File(project.basePath, "code-analysis-report.md")
                    file.writeText(report)

                    indicator.fraction = 1.0

                    // Сохраняем результаты для показа в EDT
                    analysisResult = result
                    reportFile = file

                } catch (ex: Exception) {
                    error = ex
                }
            }

            override fun onSuccess() {
                // Показываем результат в EDT
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Ошибка при анализе: ${error?.message}",
                            "Ошибка анализа кода"
                        )
                    } else if (analysisResult != null && reportFile != null) {
                        Messages.showInfoMessage(
                            project,
                            "Анализ завершен!\n\n" +
                                    "Найдено проблем: ${analysisResult!!.findings.size}\n" +
                                    "Отчет сохранен: ${reportFile!!.absolutePath}",
                            "Анализ кода"
                        )
                    }
                }
            }

            override fun onThrowable(error: Throwable) {
                // Показываем ошибку в EDT
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Ошибка при анализе: ${error.message}",
                        "Ошибка анализа кода"
                    )
                }
            }
        })
    }

    /**
     * Показывает диалог выбора типов анализа
     */
    private fun showAnalysisTypeDialog(project: Project): Set<AnalysisType>? {
        val options = arrayOf(
            "Все типы анализа",
            "Поиск багов",
            "Анализ архитектуры",
            "Качество кода",
            "Безопасность"
        )

        val choice = Messages.showChooseDialog(
            project,
            "Выберите тип анализа:",
            "Анализ кода",
            null,
            options,
            options[0]
        )

        return when (choice) {
            0 -> setOf(AnalysisType.ALL)
            1 -> setOf(AnalysisType.BUG_DETECTION)
            2 -> setOf(AnalysisType.ARCHITECTURE)
            3 -> setOf(AnalysisType.CODE_QUALITY)
            4 -> setOf(AnalysisType.SECURITY)
            else -> null
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
