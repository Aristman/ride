package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import ru.marslab.ide.ride.agent.AgentFactory
import ru.marslab.ide.ride.model.codeanalysis.AnalysisType
import ru.marslab.ide.ride.model.codeanalysis.CodeAnalysisRequest
import ru.marslab.ide.ride.model.codeanalysis.ReportFormat
import java.io.File

/**
 * Action для запуска анализа кода проекта
 */
class AnalyzeCodeAction : AnAction("Analyze Code", "Запустить анализ кода проекта", null) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Показываем диалог выбора типов анализа
        val analysisTypes = showAnalysisTypeDialog(project) ?: return

        // Запускаем анализ в фоновом режиме
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Анализ кода", true) {
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
                    val reportFile = File(project.basePath, "code-analysis-report.md")
                    reportFile.writeText(report)

                    indicator.fraction = 1.0

                    // Показываем результат
                    Messages.showInfoMessage(
                        project,
                        "Анализ завершен!\n\n" +
                        "Найдено проблем: ${result.findings.size}\n" +
                        "Отчет сохранен: ${reportFile.absolutePath}",
                        "Анализ кода"
                    )

                } catch (ex: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "Ошибка при анализе: ${ex.message}",
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
