package ru.marslab.ide.ride.agent.testing

import ru.marslab.ide.ride.testing.GeneratedTest

/**
 * Языковой агент: генерирует тесты для конкретного языка (Kotlin/Java/Dart).
 */
interface LanguageTestingAgent {
    fun supports(filePath: String): Boolean
    suspend fun generate(sourceContent: String): List<GeneratedTest>
}