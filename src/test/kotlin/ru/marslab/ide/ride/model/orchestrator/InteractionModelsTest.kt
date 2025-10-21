package ru.marslab.ide.ride.model.orchestrator

import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты для моделей интерактивного взаимодействия
 */
class InteractionModelsTest {
    
    @Test
    fun `test UserPrompt validation - confirmation valid`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Продолжить?",
            options = listOf("Да", "Нет")
        )
        
        val result = prompt.validate("Да")
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `test UserPrompt validation - choice valid`() {
        val prompt = UserPrompt(
            type = InteractionType.CHOICE,
            message = "Выберите вариант",
            options = listOf("A", "B", "C")
        )
        
        val result = prompt.validate("B")
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun `test UserPrompt validation - choice invalid`() {
        val prompt = UserPrompt(
            type = InteractionType.CHOICE,
            message = "Выберите вариант",
            options = listOf("A", "B", "C")
        )
        
        val result = prompt.validate("D")
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test UserPrompt validation - multi choice valid`() {
        val prompt = UserPrompt(
            type = InteractionType.MULTI_CHOICE,
            message = "Выберите варианты",
            options = listOf("A", "B", "C", "D")
        )
        
        val result = prompt.validate("A, C")
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun `test UserPrompt validation - multi choice invalid`() {
        val prompt = UserPrompt(
            type = InteractionType.MULTI_CHOICE,
            message = "Выберите варианты",
            options = listOf("A", "B", "C")
        )
        
        val result = prompt.validate("A, D")
        
        assertFalse(result.isValid)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun `test UserPrompt validation - input empty without default`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите текст"
        )
        
        val result = prompt.validate("")
        
        assertFalse(result.isValid)
    }
    
    @Test
    fun `test UserPrompt validation - input empty with default`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите текст",
            defaultValue = "default"
        )
        
        val result = prompt.validate("")
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun `test UserPrompt validation - custom validator success`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите число",
            validator = { input -> input.toIntOrNull() != null }
        )
        
        val result = prompt.validate("123")
        
        assertTrue(result.isValid)
    }
    
    @Test
    fun `test UserPrompt validation - custom validator failure`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите число",
            validator = { input -> input.toIntOrNull() != null }
        )
        
        val result = prompt.validate("abc")
        
        assertFalse(result.isValid)
    }
    
    @Test
    fun `test UserPrompt format - confirmation`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Подтвердите действие",
            options = listOf("Да", "Нет")
        )
        
        val formatted = prompt.format()
        
        assertTrue(formatted.contains("Подтвердите действие"))
        assertTrue(formatted.contains("Да"))
        assertTrue(formatted.contains("Нет"))
    }
    
    @Test
    fun `test UserPrompt format - choice`() {
        val prompt = UserPrompt(
            type = InteractionType.CHOICE,
            message = "Выберите вариант",
            options = listOf("Вариант 1", "Вариант 2", "Вариант 3")
        )
        
        val formatted = prompt.format()
        
        assertTrue(formatted.contains("Выберите вариант"))
        assertTrue(formatted.contains("1. Вариант 1"))
        assertTrue(formatted.contains("2. Вариант 2"))
        assertTrue(formatted.contains("3. Вариант 3"))
    }
    
    @Test
    fun `test UserPrompt format - input with default`() {
        val prompt = UserPrompt(
            type = InteractionType.INPUT,
            message = "Введите имя",
            defaultValue = "John"
        )
        
        val formatted = prompt.format()
        
        assertTrue(formatted.contains("Введите имя"))
        assertTrue(formatted.contains("John"))
    }
    
    @Test
    fun `test UserPrompt format - with timeout`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Подтвердите",
            timeout = 30000L
        )
        
        val formatted = prompt.format()
        
        assertTrue(formatted.contains("30с"))
    }
    
    @Test
    fun `test InteractionHistory add and retrieve`() {
        val history = InteractionHistory("plan1")
        
        val prompt1 = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "First prompt"
        )
        
        val prompt2 = UserPrompt(
            type = InteractionType.INPUT,
            message = "Second prompt"
        )
        
        history.addInteraction(prompt1)
        history.addInteraction(prompt2)
        
        assertEquals(2, history.interactions.size)
        assertEquals("Second prompt", history.getLastInteraction()?.prompt?.message)
    }
    
    @Test
    fun `test InteractionHistory get by prompt id`() {
        val history = InteractionHistory("plan1")
        
        val prompt = UserPrompt(
            id = "prompt123",
            type = InteractionType.CONFIRMATION,
            message = "Test prompt"
        )
        
        history.addInteraction(prompt)
        
        val interaction = history.getInteractionByPromptId("prompt123")
        
        assertNotNull(interaction)
        assertEquals("Test prompt", interaction?.prompt?.message)
    }
    
    @Test
    fun `test Interaction has response`() {
        val prompt = UserPrompt(
            type = InteractionType.CONFIRMATION,
            message = "Test"
        )
        
        val interactionWithoutResponse = Interaction(prompt)
        assertFalse(interactionWithoutResponse.hasResponse())
        
        val response = UserResponse(
            promptId = prompt.id,
            input = "Да"
        )
        
        val interactionWithResponse = Interaction(prompt, response)
        assertTrue(interactionWithResponse.hasResponse())
    }
    
    @Test
    fun `test ValidationResult success`() {
        val result = ValidationResult.success()
        
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }
    
    @Test
    fun `test ValidationResult failure`() {
        val result = ValidationResult.failure("Error 1", "Error 2")
        
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.contains("Error 1"))
        assertTrue(result.errors.contains("Error 2"))
    }
}
