package ru.marslab.ide.ride.service

import ru.marslab.ide.ride.model.Message
import ru.marslab.ide.ride.model.MessageRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageHistoryTest {
    
    @Test
    fun `test empty history`() {
        val history = MessageHistory()
        
        assertTrue(history.isEmpty())
        assertEquals(0, history.getMessageCount())
        assertTrue(history.getMessages().isEmpty())
    }
    
    @Test
    fun `test add message`() {
        val history = MessageHistory()
        val message = Message(content = "Test", role = MessageRole.USER)
        
        history.addMessage(message)
        
        assertFalse(history.isEmpty())
        assertEquals(1, history.getMessageCount())
        assertEquals(listOf(message), history.getMessages())
    }
    
    @Test
    fun `test add multiple messages`() {
        val history = MessageHistory()
        val message1 = Message(content = "First", role = MessageRole.USER)
        val message2 = Message(content = "Second", role = MessageRole.ASSISTANT)
        val message3 = Message(content = "Third", role = MessageRole.USER)
        
        history.addMessage(message1)
        history.addMessage(message2)
        history.addMessage(message3)
        
        assertEquals(3, history.getMessageCount())
        assertEquals(listOf(message1, message2, message3), history.getMessages())
    }
    
    @Test
    fun `test get recent messages`() {
        val history = MessageHistory()
        val messages = (1..10).map { 
            Message(content = "Message $it", role = MessageRole.USER)
        }
        
        messages.forEach { history.addMessage(it) }
        
        val recent = history.getRecentMessages(3)
        
        assertEquals(3, recent.size)
        assertEquals("Message 8", recent[0].content)
        assertEquals("Message 9", recent[1].content)
        assertEquals("Message 10", recent[2].content)
    }
    
    @Test
    fun `test get recent messages when less than requested`() {
        val history = MessageHistory()
        val message = Message(content = "Only one", role = MessageRole.USER)
        
        history.addMessage(message)
        
        val recent = history.getRecentMessages(5)
        
        assertEquals(1, recent.size)
        assertEquals(message, recent[0])
    }
    
    @Test
    fun `test clear history`() {
        val history = MessageHistory()
        history.addMessage(Message(content = "Test 1", role = MessageRole.USER))
        history.addMessage(Message(content = "Test 2", role = MessageRole.ASSISTANT))
        
        assertEquals(2, history.getMessageCount())
        
        history.clear()
        
        assertTrue(history.isEmpty())
        assertEquals(0, history.getMessageCount())
    }
}
