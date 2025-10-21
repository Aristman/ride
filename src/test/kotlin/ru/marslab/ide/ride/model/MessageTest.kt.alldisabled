package ru.marslab.ide.ride.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageTest {
    
    @Test
    fun `test message creation with default values`() {
        val message = Message(
            content = "Test message",
            role = MessageRole.USER
        )
        
        assertEquals("Test message", message.content)
        assertEquals(MessageRole.USER, message.role)
        assertTrue(message.id.isNotBlank())
        assertTrue(message.timestamp > 0)
        assertTrue(message.metadata.isEmpty())
    }
    
    @Test
    fun `test isFromUser returns true for user messages`() {
        val message = Message(content = "Test", role = MessageRole.USER)
        assertTrue(message.isFromUser())
        assertFalse(message.isFromAssistant())
        assertFalse(message.isSystem())
    }
    
    @Test
    fun `test isFromAssistant returns true for assistant messages`() {
        val message = Message(content = "Test", role = MessageRole.ASSISTANT)
        assertTrue(message.isFromAssistant())
        assertFalse(message.isFromUser())
        assertFalse(message.isSystem())
    }
    
    @Test
    fun `test isSystem returns true for system messages`() {
        val message = Message(content = "Test", role = MessageRole.SYSTEM)
        assertTrue(message.isSystem())
        assertFalse(message.isFromUser())
        assertFalse(message.isFromAssistant())
    }
    
    @Test
    fun `test message with metadata`() {
        val metadata = mapOf("key" to "value", "count" to 42)
        val message = Message(
            content = "Test",
            role = MessageRole.USER,
            metadata = metadata
        )
        
        assertEquals(metadata, message.metadata)
        assertEquals("value", message.metadata["key"])
        assertEquals(42, message.metadata["count"])
    }
}
