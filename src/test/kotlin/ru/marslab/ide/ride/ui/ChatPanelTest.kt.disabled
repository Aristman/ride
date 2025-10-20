package ru.marslab.ide.ride.ui

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Container
import javax.swing.JEditorPane
import ru.marslab.ide.ride.service.ChatService

class ChatPanelTest : LightPlatformTestCase() {

    fun testPanelInitializesAndShowsGreeting() {
        service<ChatService>().clearHistory()

        val panel = ChatPanel(project)
        assertNotNull(panel)

        UIUtil.dispatchAllInvocationEvents()

        // Находим все JEditorPane и проверяем, что есть приветственное сообщение
        val editors = findEditorPanes(panel)
        assertTrue(editors.isNotEmpty())
        val hasGreeting = editors.any { editor ->
            val html = editor.text
            val docText = runCatching { editor.document.getText(0, editor.document.length) }.getOrElse { "" }
            html.contains("Привет! Я AI-ассистент", ignoreCase = true) ||
                    docText.contains("Привет! Я AI-ассистент", ignoreCase = true)
        }
        assertTrue(hasGreeting)
    }

    private fun findEditorPanes(container: Container): List<JEditorPane> {
        val result = mutableListOf<JEditorPane>()
        for (comp in container.components) {
            when (comp) {
                is JEditorPane -> result.add(comp)
                is Container -> result.addAll(findEditorPanes(comp))
            }
        }
        return result
    }
}
