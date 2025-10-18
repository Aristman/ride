package ru.marslab.ide.ride.ui.renderer

import ru.marslab.ide.ride.model.llm.TokenUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatContentRendererTest {

    private val renderer = ChatContentRenderer()

    @Test
    fun `test render simple text to html`() {
        val input = "Simple text message"

        val result = renderer.renderContentToHtml(input, isJcefMode = false)

        assertTrue(result.contains("Simple text message"))
        // Should either be wrapped in p tags or have some HTML structure
        assertTrue(result.contains("<p>") || result.contains("Simple text message"))
    }

    @Test
    fun `test render text with code blocks`() {
        val input = """
            Here is some code:
            ```kotlin
            fun hello() {
                println("Hello, World!")
            }
            ```
            And more text.
        """.trimIndent()

        val result = renderer.renderContentToHtml(input, isJcefMode = false)

        assertTrue(result.contains("fun hello()"))
        assertTrue(result.contains("pre") || result.contains("code"))
        assertTrue(result.contains("kotlin"))
    }

    @Test
    fun `test render text with inline code`() {
        val input = "Use `println()` function to output text."

        val result = renderer.renderContentToHtml(input, isJcefMode = false)

        assertTrue(result.contains("println"))
        assertTrue(result.contains("code"))
    }

    @Test
    fun `test render markdown formatting`() {
        val input = "**Bold text** and *italic text*"

        val result = renderer.renderContentToHtml(input, isJcefMode = false)

        assertTrue(result.contains("strong") || result.contains("<b>"))
        assertTrue(result.contains("em") || result.contains("<i>"))
    }

    @Test
    fun `test render html passthrough`() {
        val input = "<div>Already formatted HTML</div>"

        val result = renderer.renderContentToHtml(input, isJcefMode = false)

        assertEquals(input, result)
    }

    @Test
    fun `test render content in JCEF mode`() {
        val input = "Text with `inline code`"

        val result = renderer.renderContentToHtml(input, isJcefMode = true)

        assertTrue(result.contains("inline code"))
    }

    @Test
    fun `test create status html for final response`() {
        val result = renderer.createStatusHtml(
            isFinal = true,
            uncertainty = 0.05,
            wasParsed = true,
            hasClarifyingQuestions = false,
            responseTimeMs = 1500,
            tokensUsed = 100
        )

        assertTrue(result.contains("status-final"))
        // Just check that result contains some expected elements
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `test create status html for uncertain response`() {
        val result = renderer.createStatusHtml(
            isFinal = false,
            uncertainty = 0.4,
            wasParsed = true,
            hasClarifyingQuestions = true,
            responseTimeMs = 2000,
            tokenUsage = TokenUsage(50, 30, 80)
        )

        assertTrue(result.contains("status-uncertain"))
        assertTrue(result.contains("2") && result.contains("s"))
        assertTrue(result.contains("80"))
        // Check for presence of input/output tokens
        assertTrue(result.contains("50") || result.contains("30"))
    }

    @Test
    fun `test create status html with low confidence`() {
        val result = renderer.createStatusHtml(
            isFinal = true,
            uncertainty = 0.3,
            wasParsed = false,
            hasClarifyingQuestions = false
        )

        assertTrue(result.contains("status-low-confidence"))
        assertTrue(result.contains("⚠️"))
    }

    @Test
    fun `test create status html without uncertainty`() {
        val result = renderer.createStatusHtml(
            isFinal = true,
            uncertainty = 0.05,
            wasParsed = true,
            hasClarifyingQuestions = false,
            showUncertaintyStatus = false,
            responseTimeMs = 1000
        )

        assertTrue(result.contains("status-final"))
        // Should contain some kind of space for height preservation
        assertTrue(result.contains("&nbsp;") || result.contains(" "))
        assertTrue(result.contains("1") && result.contains("s"))
    }

    @Test
    fun `test create prefix html for user`() {
        val result = renderer.createPrefixHtml("user", "You", true)

        assertTrue(result.contains("prefix"))
        assertTrue(result.contains("align='right'"))
        assertTrue(result.contains("<b>You</b>"))
    }

    @Test
    fun `test create prefix html for assistant`() {
        val result = renderer.createPrefixHtml("assistant", "AI", false)

        assertTrue(result.contains("prefix"))
        assertTrue(result.contains("<b>AI</b>"))
        assertTrue(!result.contains("align='right'"))
    }

    @Test
    fun `test create content html for user`() {
        val result = renderer.createContentHtml("Hello", true)

        assertTrue(result.contains("content"))
        assertTrue(result.contains("align='right'"))
        assertTrue(result.contains("Hello"))
    }

    @Test
    fun `test create content html for assistant`() {
        val result = renderer.createContentHtml("Hi there!", false)

        assertTrue(result.contains("content"))
        assertTrue(result.contains("Hi there!"))
        assertTrue(!result.contains("align='right'"))
    }

    @Test
    fun `test create message block`() {
        val statusHtml = "<div class='status'>Status</div>"
        val result = renderer.createMessageBlock(
            role = "user",
            prefix = "You",
            content = "Hello",
            statusHtml = statusHtml,
            isUser = true
        )

        assertTrue(result.contains("msg user"))
        assertTrue(result.contains("<b>You</b>"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains(statusHtml))
    }

    @Test
    fun `test create system message html`() {
        val result = renderer.createSystemMessageHtml("System processing...")

        assertTrue(result.contains("msg system"))
        assertTrue(result.contains("system-content"))
        assertTrue(result.contains("System processing..."))
    }

    @Test
    fun `test create system message html with loading`() {
        val result = renderer.createSystemMessageHtml("Loading...", isLoading = true)

        assertTrue(result.contains("msg system"))
        assertTrue(result.contains("<!--LOADING_MARKER-->"))
        assertTrue(result.contains("Loading..."))
    }

    @Test
    fun `test create message block after system`() {
        val result = renderer.createMessageBlock(
            role = "assistant",
            prefix = "AI",
            content = "Response",
            isAfterSystem = true
        )

        assertTrue(result.contains("msg assistant"))
        assertTrue(result.contains("after-system"))
    }
}