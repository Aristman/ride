package ru.marslab.ide.ride.integration.llm

import org.junit.Test
import ru.marslab.ide.ride.integration.llm.impl.TiktokenCounter
import ru.marslab.ide.ride.model.ConversationMessage
import ru.marslab.ide.ride.model.ConversationRole
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для TiktokenCounter
 */
class TiktokenCounterTest {
    
    private val counter = TiktokenCounter.forGPT()
    
    @Test
    fun `test count tokens for empty string`() {
        val tokens = counter.countTokens("")
        assertEquals(0, tokens, "Empty string should have 0 tokens")
    }
    
    @Test
    fun `test count tokens for simple text`() {
        val text = "Hello, world!"
        val tokens = counter.countTokens(text)
        assertTrue(tokens > 0, "Simple text should have positive token count")
        assertTrue(tokens < 10, "Simple text should have less than 10 tokens")
    }
    
    @Test
    fun `test count tokens for longer text`() {
        val text = """
            This is a longer text that contains multiple sentences.
            It should have more tokens than a simple greeting.
            The token counter should accurately count all the tokens in this text.
        """.trimIndent()
        val tokens = counter.countTokens(text)
        assertTrue(tokens > 20, "Longer text should have more than 20 tokens")
        assertTrue(tokens < 100, "This text should have less than 100 tokens")
    }
    
    @Test
    fun `test count tokens for russian text`() {
        val text = "Привет, мир! Как дела?"
        val tokens = counter.countTokens(text)
        assertTrue(tokens > 0, "Russian text should have positive token count")
        // Русский текст обычно требует больше токенов
        assertTrue(tokens > 5, "Russian text typically requires more tokens")
    }
    
    @Test
    fun `test count tokens for code`() {
        val code = """
            fun main() {
                println("Hello, world!")
            }
        """.trimIndent()
        val tokens = counter.countTokens(code)
        assertTrue(tokens > 10, "Code should have positive token count")
    }
    
    @Test
    fun `test count tokens for conversation messages`() {
        val messages = listOf(
            ConversationMessage(ConversationRole.USER, "What is Kotlin?"),
            ConversationMessage(ConversationRole.ASSISTANT, "Kotlin is a modern programming language.")
        )
        val tokens = counter.countTokens(messages)
        assertTrue(tokens > 10, "Conversation should have positive token count")
    }
    
    @Test
    fun `test count tokens for empty conversation`() {
        val messages = emptyList<ConversationMessage>()
        val tokens = counter.countTokens(messages)
        assertEquals(0, tokens, "Empty conversation should have 0 tokens")
    }
    
    @Test
    fun `test count request tokens`() {
        val systemPrompt = "You are a helpful assistant."
        val userMessage = "What is the weather today?"
        val history = listOf(
            ConversationMessage(ConversationRole.USER, "Hello"),
            ConversationMessage(ConversationRole.ASSISTANT, "Hi there!")
        )
        
        val tokens = counter.countRequestTokens(systemPrompt, userMessage, history)
        
        // Должно быть больше суммы отдельных частей из-за overhead
        val systemTokens = counter.countTokens(systemPrompt)
        val messageTokens = counter.countTokens(userMessage)
        val historyTokens = counter.countTokens(history)
        
        assertTrue(tokens >= systemTokens + messageTokens + historyTokens,
            "Request tokens should be at least sum of parts")
    }
    
    @Test
    fun `test count request tokens with empty history`() {
        val systemPrompt = "You are a helpful assistant."
        val userMessage = "Hello"
        val history = emptyList<ConversationMessage>()
        
        val tokens = counter.countRequestTokens(systemPrompt, userMessage, history)
        assertTrue(tokens > 0, "Request with empty history should still have tokens")
    }
    
    @Test
    fun `test count request tokens with blank system prompt`() {
        val systemPrompt = ""
        val userMessage = "Hello"
        val history = emptyList<ConversationMessage>()
        
        val tokens = counter.countRequestTokens(systemPrompt, userMessage, history)
        assertTrue(tokens > 0, "Request with blank system prompt should still have tokens")
    }
    
    @Test
    fun `test token count consistency`() {
        val text = "This is a test message"
        val tokens1 = counter.countTokens(text)
        val tokens2 = counter.countTokens(text)
        
        assertEquals(tokens1, tokens2, "Token count should be consistent for same text")
    }
    
    @Test
    fun `test tokenizer name`() {
        val name = counter.getTokenizerName()
        assertTrue(name.contains("Tiktoken"), "Tokenizer name should contain 'Tiktoken'")
    }
    
    @Test
    fun `test large text token count`() {
        // Создаём текст примерно на 2000 токенов
        val largeText = "This is a test sentence. ".repeat(300)
        val tokens = counter.countTokens(largeText)
        
        assertTrue(tokens > 1000, "Large text should have many tokens")
        assertTrue(tokens < 3000, "Token count should be reasonable")
    }
    
    @Test
    fun `test special characters`() {
        val text = "Hello! @#$%^&*() 123 <html> {code}"
        val tokens = counter.countTokens(text)
        assertTrue(tokens > 0, "Text with special characters should have positive token count")
    }
}
