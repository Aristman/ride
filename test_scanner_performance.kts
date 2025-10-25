#!/usr/bin/env kotlin

/**
 * Скрипт для тестирования производительности ProjectScannerToolAgent
 * на проектах разных размеров
 */

import java.io.File
import java.nio.file.Path
import kotlin.system.measureTimeMillis

// Создаем тестовую структуру проекта
fun createTestProject(baseDir: Path, numFiles: Int, numDirectories: Int) {
    println("Создание тестового проекта с $numFiles файлами в $numDirectories директориях...")

    baseDir.toFile().deleteRecursively()
    baseDir.toFile().mkdirs()

    // Создаем структуру директорий
    val dirs = mutableListOf<File>()
    repeat(numDirectories) { i ->
        val dir = File(baseDir.toFile(), "src/main/dir$i")
        dir.mkdirs()
        dirs.add(dir)
    }

    // Создаем файлы
    repeat(numFiles) { i ->
        val dirIndex = i % numDirectories
        val file = File(dirs[dirIndex], "File$i.kt")
        file.writeText("""
            class File$i {
                private val property = "test$i"

                fun calculate(): Int {
                    return i * 2
                }

                fun getName(): String {
                    return "File${i}"
                }
            }
        """.trimIndent())
    }

    // Создаем конфигурационные файлы
    File(baseDir.toFile(), "build.gradle.kts").writeText("""
        plugins {
            kotlin("jvm") version "1.9.0"
        }

        dependencies {
            implementation(kotlin("stdlib"))
        }
    """.trimIndent())

    File(baseDir.toFile(), "settings.gradle.kts").writeText("""
        rootProject.name = "test-project"
    """.trimIndent())

    println("Тестовый проект создан: ${baseDir}")
}

fun main() {
    val testSizes = listOf(
        Pair("Малый проект", 50, 5),
        Pair("Средний проект", 200, 20),
        Pair("Большой проект", 1000, 50)
    )

    testSizes.forEach { (name, numFiles, numDirs) ->
        println("\n=== Тестирование: $name ===")

        val tempDir = File("/tmp/test-scanner-${System.currentTimeMillis()}")
        createTestProject(tempDir.toPath(), numFiles, numDirs)

        try {
            val scanTime = measureTimeMillis {
                // Здесь будет вызов сканера
                println("Сканирование проекта...")
                Thread.sleep(100) // Имитация времени сканирования
            }

            println("Результаты для $name:")
            println("- Файлов: $numFiles")
            println("- Директорий: $numDirs")
            println("- Время сканирования: ${scanTime}ms")
            println("- Среднее время на файл: ${scanTime.toDouble() / numFiles} мс")

        } finally {
            tempDir.deleteRecursively()
        }
    }

    println("\n=== Тестирование завершено ===")
}