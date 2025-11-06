package ru.marslab.ide.ride.ui.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Предоставляет подсказки для `@`-пикера по индексу IDE.
 * Возвращает workspace-relative пути файлов.
 */
class AtPickerSuggestionService {
    fun suggestFiles(project: Project, query: String, limit: Int = 50): List<String> {
        if (project.isDisposed || DumbService.isDumb(project)) return emptyList()
        val scope = GlobalSearchScope.projectScope(project)
        val basePath = project.basePath ?: ""

        // Если запрос пустой — показываем топ по имени (алфавитно)
        val names: Array<String> = try {
            ReadAction.compute<Array<String>, Throwable> { FilenameIndex.getAllFilenames(project) }
        } catch (_: Throwable) { emptyArray() }

        val filteredNames = if (query.isBlank()) names.asList() else names.filter {
            val q = query.trim()
            it.contains(q, ignoreCase = true) || camelCaseMatch(it, q)
        }

        // Получаем файлы по именам, собираем уникальные workspace-relative пути
        val results = LinkedHashSet<String>()
        for (name in filteredNames) {
            if (results.size >= limit) break
            val files: Collection<VirtualFile> = try {
                ReadAction.compute<Collection<VirtualFile>, Throwable> {
                    FilenameIndex.getVirtualFilesByName(name, true, scope)
                }
            } catch (_: Throwable) { emptyList() }
            for (vf in files) {
                val path = vf.path
                val rel = if (basePath.isNotBlank() && path.startsWith(basePath)) {
                    path.removePrefix(basePath).trimStart('/')
                } else path
                // Фильтрация только по имени файла (basename), поддержка camelCase
                val fname = vf.name
                val q = query.trim()
                val match = q.isBlank() || fname.contains(q, ignoreCase = true) || camelCaseMatch(fname, q)
                if (match) {
                    results.add(rel)
                    if (results.size >= limit) break
                }
            }
        }
        return results.toList()
    }

    private fun camelCaseMatch(text: String, pattern: String): Boolean {
        // Простейшая проверка: все заглавные буквы в тексте содержат последовательность символов pattern
        if (pattern.isBlank()) return false
        val capitals = text.filter { it.isUpperCase() }
        return capitals.contains(pattern.replace(" ", ""), ignoreCase = true)
    }
}
