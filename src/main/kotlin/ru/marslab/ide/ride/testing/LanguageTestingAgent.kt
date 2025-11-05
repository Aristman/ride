package ru.marslab.ide.ride.testing

/**
 * Языковой агент: генерирует тесты для конкретного языка (Kotlin/Java/Dart).
 */
interface LanguageTestingAgent {
    fun supports(filePath: String): Boolean
    suspend fun generate(sourceContent: String): List<GeneratedTest>
}
