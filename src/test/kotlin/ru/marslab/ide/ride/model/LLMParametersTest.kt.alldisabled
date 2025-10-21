package ru.marslab.ide.ride.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LLMParametersTest {
    
    @Test
    fun `test default parameters`() {
        val params = LLMParameters()
        
        assertEquals(0.7, params.temperature)
        assertEquals(2000, params.maxTokens)
    }
    
    @Test
    fun `test creative parameters`() {
        val params = LLMParameters.CREATIVE
        
        assertEquals(0.9, params.temperature)
        assertEquals(2000, params.maxTokens)
    }
    
    @Test
    fun `test precise parameters`() {
        val params = LLMParameters.PRECISE
        
        assertEquals(0.3, params.temperature)
        assertEquals(2000, params.maxTokens)
    }
    
    @Test
    fun `test custom parameters`() {
        val params = LLMParameters(
            temperature = 0.5,
            maxTokens = 1000,
            topP = 0.9
        )
        
        assertEquals(0.5, params.temperature)
        assertEquals(1000, params.maxTokens)
        assertEquals(0.9, params.topP)
    }
    
    @Test
    fun `test temperature validation - too low`() {
        assertFailsWith<IllegalArgumentException> {
            LLMParameters(temperature = -0.1)
        }
    }
    
    @Test
    fun `test temperature validation - too high`() {
        assertFailsWith<IllegalArgumentException> {
            LLMParameters(temperature = 1.1)
        }
    }
    
    @Test
    fun `test maxTokens validation`() {
        assertFailsWith<IllegalArgumentException> {
            LLMParameters(maxTokens = 0)
        }
    }
    
    @Test
    fun `test topP validation - too low`() {
        assertFailsWith<IllegalArgumentException> {
            LLMParameters(topP = -0.1)
        }
    }
    
    @Test
    fun `test topP validation - too high`() {
        assertFailsWith<IllegalArgumentException> {
            LLMParameters(topP = 1.1)
        }
    }
}
