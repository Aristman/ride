package ru.marslab.ide.ride.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager

/**
 * Action для открытия файла по пути с позиционированием на указанные строки
 *
 * Формат команды: open?path={path}&startLine={n}&endLine={m}
 */
class OpenFileAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        // Этот action будет вызываться программно, не через UI
        throw UnsupportedOperationException("Use execute() method instead")
    }

    companion object {
        private const val SCHEME = "open"

        /**
         * Выполняет открытие файла и позиционирование курсора
         *
         * @param project проект в котором открыть файл
         * @param command команда в формате: open?path={path}&startLine={n}&endLine={m}
         */
        fun execute(project: Project, command: String): Boolean {
            try {
                val params = parseCommand(command)
                return openFileWithPosition(project, params)
            } catch (e: Exception) {
                // Логирование ошибки можно добавить если понадобится
                return false
            }
        }

        /**
         * Парсит команду open?path={path}&startLine={n}&endLine={m}
         */
        private fun parseCommand(command: String): FileOpenParams {
            if (!command.startsWith("$SCHEME?")) {
                throw IllegalArgumentException("Invalid command format: $command")
            }

            val query = command.substringAfter("$SCHEME?")
            val params = query.split("&")
                .mapNotNull { param ->
                    val (key, value) = param.split("=", limit = 2)
                    key to value
                }
                .toMap()

            val path = params["path"] ?: throw IllegalArgumentException("Missing path parameter")
            val startLine = params["startLine"]?.toIntOrNull() ?: 1
            val endLine = params["endLine"]?.toIntOrNull() ?: startLine

            return FileOpenParams(path, startLine, endLine)
        }

        /**
         * Открывает файл и позиционирует курсор
         */
        private fun openFileWithPosition(project: Project, params: FileOpenParams): Boolean {
            val psiManager = PsiManager.getInstance(project)
            val virtualFileManager = VirtualFileManager.getInstance()

            // Ищем виртуальный файл
            val virtualFile: VirtualFile? = when {
                params.path.startsWith("/") -> {
                    // Абсолютный путь
                    virtualFileManager.findFileByUrl("file://${params.path}")
                }
                else -> {
                    // Относительный путь от корня проекта
                    val basePath = project.basePath
                    if (basePath != null) {
                        val fullPath = "$basePath/${params.path}"
                        virtualFileManager.findFileByUrl("file://$fullPath")
                    } else null
                }
            }

            if (virtualFile == null || !virtualFile.exists()) {
                return false
            }

            // Открываем файл в редакторе
            val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            val editor = fileEditorManager.openTextEditor(
                OpenFileDescriptor(project, virtualFile, params.startLine - 1, 0),
                true
            )

            if (editor != null) {
                // Выделяем строки от startLine до endLine
                val document = editor.document
                val startOffset = document.getLineStartOffset((params.startLine - 1).coerceIn(0, document.lineCount - 1))
                val endOffset = document.getLineEndOffset((params.endLine - 1).coerceIn(0, document.lineCount - 1))

                editor.selectionModel.setSelection(startOffset, endOffset)
                editor.caretModel.moveToOffset(startOffset)

                return true
            }

            return false
        }
    }

    /**
     * Параметры открытия файла
     */
    data class FileOpenParams(
        val path: String,
        val startLine: Int,
        val endLine: Int
    )
}