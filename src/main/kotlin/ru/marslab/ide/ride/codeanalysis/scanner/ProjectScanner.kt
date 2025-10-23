package ru.marslab.ide.ride.codeanalysis.scanner

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
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
        println("    Project: ${project.name}")
        println("    Base path: ${project.basePath}")
        println("    Project file: ${project.projectFile}")
        println("    Is open: ${project.isOpen}")
        println("    Is initialized: ${project.isInitialized}")
        println("    Include patterns: $filePatterns")
        println("    Exclude patterns: $excludePatterns")

        val includeMatchers = filePatterns.map { createGlobMatcher(it) }
        val excludeMatchers = excludePatterns.map { createGlobMatcher(it) }

        println("    Created ${includeMatchers.size} include matchers and ${excludeMatchers.size} exclude matchers")

        // Пытаемся получить файлы через ProjectFileIndex
        val projectFileIndex = ProjectFileIndex.getInstance(project)
        println("    ProjectFileIndex initialized: $projectFileIndex")

        // Получаем корневые директории проекта через ModuleManager
        val moduleManager = ModuleManager.getInstance(project)
        val projectRoots = moduleManager.modules.flatMap { module ->
            ModuleRootManager.getInstance(module).contentRoots.toList()
        }

        println("    Found ${projectRoots.size} project roots:")
        projectRoots.forEachIndexed { index, root ->
            println("      Root $index: ${root.path}")
            println("        Exists: ${root.exists()}")
            println("        Is directory: ${root.isDirectory}")
            println("        Children: ${if (root.isDirectory) root.children?.size ?: 0 else 0}")
        }

        // Собираем все файлы из корневых директорий
        val allFiles = mutableListOf<VirtualFile>()

        // Если есть корневые директории, используем их
        if (projectRoots.isNotEmpty()) {
            projectRoots.forEach { root ->
                if (root.isDirectory) {
                    collectFiles(root, allFiles, includeMatchers, excludeMatchers)
                }
            }
        } else {
            // Если корневых директорий нет, используем базовый путь проекта
            val baseDir = project.basePath?.let { com.intellij.openapi.vfs.VfsUtil.findFileByIoFile(File(it), true) }
            if (baseDir != null) {
                collectFiles(baseDir, allFiles, includeMatchers, excludeMatchers)
            }
        }

        println("    Found ${allFiles.size} files matching the patterns")
        if (allFiles.size <= 10) {
            allFiles.take(10).forEachIndexed { index, file ->
                println("      File $index: ${file.path}")
            }
        } else {
            allFiles.take(5).forEachIndexed { index, file ->
                println("      File $index: ${file.path}")
            }
            println("      ... and ${allFiles.size - 5} more files")
        }

        return allFiles
    }

    /**
     * Рекурсивно собирает файлы из директории, соответствующие паттернам
     */
    private fun collectFiles(
        dir: VirtualFile,
        result: MutableList<VirtualFile>,
        includeMatchers: List<PathMatcher>,
        excludeMatchers: List<PathMatcher>
    ) {
        try {
            if (!dir.isDirectory) {
                if (shouldIncludeFile(dir, includeMatchers, excludeMatchers)) {
                    result.add(dir)
                }
                return
            }

            // Пропускаем скрытые директории (начинающиеся с .)
            if (dir.name.startsWith(".")) {
                return
            }

            // Рекурсивно обходим дочерние элементы
            dir.children?.forEach { child ->
                if (child.isDirectory) {
                    // Пропускаем исключенные директории
                    val path = java.nio.file.Paths.get(child.path)
                    if (excludeMatchers.none { it.matches(path) }) {
                        collectFiles(child, result, includeMatchers, excludeMatchers)
                    }
                } else if (shouldIncludeFile(child, includeMatchers, excludeMatchers)) {
                    result.add(child)
                }
            }
        } catch (e: Exception) {
            println("      Error scanning ${dir.path}: ${e.message}")
        }
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
        val pathObj = java.nio.file.Paths.get(path)

        // Проверяем исключения
        if (excludeMatchers.any { it.matches(pathObj) }) {
            return false
        }

        // Проверяем включения
        return includeMatchers.any { it.matches(pathObj) }
    }

    /**
     * Создает PathMatcher из glob паттерна
     */
    private fun createGlobMatcher(pattern: String): PathMatcher {
        return FileSystems.getDefault().getPathMatcher("glob:$pattern")
    }
}
