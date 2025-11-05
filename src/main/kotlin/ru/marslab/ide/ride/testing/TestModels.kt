package ru.marslab.ide.ride.testing

import java.nio.file.Path

/**
 * Единица тестирования, полученная из анализа исходного кода.
 * Может описывать публичный класс/метод и его контракт.
 */
data class TestUnit(
    val sourceFile: Path,
    val packageName: String?,
    val className: String,
    val publicApiName: String?, // null для класс-уровня
    val language: String,
)

/**
 * Результат генерации теста для конкретного TestUnit.
 */
data class GeneratedTest(
    val targetPackage: String?,
    val className: String,
    val fileName: String,
    val content: String,
)

/**
 * Сводный результат запуска тестов.
 */
data class TestRunResult(
    val success: Boolean,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val durationMs: Long,
    val reportText: String,
    val errors: List<String> = emptyList()
)

