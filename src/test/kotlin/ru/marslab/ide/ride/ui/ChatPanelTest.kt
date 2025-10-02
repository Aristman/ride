package ru.marslab.ide.ride.ui

import com.intellij.testFramework.LightPlatformTestCase
import javax.swing.JTextArea
import java.awt.Component
import java.awt.Container

class ChatPanelTest : LightPlatformTestCase() {

    fun testPanelInitializesAndShowsGreeting() {
        val panel = ChatPanel(project)
        assertNotNull(panel)

        // Находим все JTextArea и проверяем, что есть приветственное сообщение
        val areas = findTextAreas(panel)
        assertTrue(areas.isNotEmpty())
        val hasGreeting = areas.any { it.text.contains("Привет! Я AI-ассистент", ignoreCase = true) }
        assertTrue(hasGreeting)
    }

    private fun findTextAreas(container: Container): List<JTextArea> {
        val result = mutableListOf<JTextArea>()
        for (comp in container.components) {
            when (comp) {
                is JTextArea -> result.add(comp)
                is Container -> result.addAll(findTextAreas(comp))
            }
        }
        return result
    }
}
