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
        println("  ProjectScanner.scanProject() called")
        println("    Include patterns: $filePatterns")
        println("    Exclude patterns: $excludePatterns")
        
        val files = mutableListOf<VirtualFile>()
        val includeMatchers = filePatterns.map { createGlobMatcher(it) }
        val excludeMatchers = excludePatterns.map { createGlobMatcher(it) }
        
        println("    Created ${includeMatchers.size} include matchers and ${excludeMatchers.size} exclude matchers")

        var totalFiles = 0
        var includedFiles = 0
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            totalFiles++
            if (!file.isDirectory && shouldIncludeFile(file, includeMatchers, excludeMatchers)) {
                files.add(file)
                includedFiles++
                if (includedFiles <= 5) {
                    println("      Included: ${file.path}")
                }
            }
            true
        }

        println("    Scanned $totalFiles files, included ${files.size} files")
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
