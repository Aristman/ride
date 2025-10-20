package ru.marslab.ide.ride.codeanalysis.scanner

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Сканер файлов проекта
 * 
 * Обходит файлы проекта с учетом паттернов включения и исключения
 */
class ProjectScanner(
    private val project: Project
) {
    /**
     * Сканирует проект и возвращает список файлов
     * 
     * @param filePatterns Glob паттерны для включения файлов
     * @param excludePatterns Glob паттерны для исключения файлов
     * @return Список найденных файлов
     */
    fun scanProject(
        filePatterns: List<String>,
        excludePatterns: List<String>
    ): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val includeMatchers = filePatterns.map { createGlobMatcher(it) }
        val excludeMatchers = excludePatterns.map { createGlobMatcher(it) }

        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (!file.isDirectory && shouldIncludeFile(file, includeMatchers, excludeMatchers)) {
                files.add(file)
            }
            true
        }

        return files
    }

    /**
     * Проверяет, должен ли файл быть включен в анализ
     */
    private fun shouldIncludeFile(
        file: VirtualFile,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>
    ): Boolean {
        val path = file.path
        
        // Проверяем исключения
        if (excludeMatchers.any { it.matches(java.nio.file.Paths.get(path)) }) {
            return false
        }
        
        // Проверяем включения
        return includeMatchers.any { it.matches(java.nio.file.Paths.get(path)) }
    }

    /**
     * Создает PathMatcher из glob паттерна
     */
    private fun createGlobMatcher(pattern: String): PathMatcher {
        return FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
}
