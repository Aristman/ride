package ru.marslab.ide.ride.integration.llm.impl

import io.mockk.coEvery
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceModel
import ru.marslab.ide.ride.integration.llm.impl.HuggingFaceProvider
import ru.marslab.ide.ride.model.LLMResponse

class HuggingFaceProviderTest {

    @Test
    fun `should create provider with correct configuration`() {
        // Given
        val apiKey = "test-api-key"
        val model = HuggingFaceModel.DEEPSEEK_R1.modelId
        val config = HuggingFaceConfig(
            apiKey = apiKey,
            model = model
        )

        // When
        val provider = HuggingFaceProvider(config)

        // Then
        assertEquals("HuggingFace", provider.getProviderName())
        assertTrue(provider.isAvailable())
    }

    @Test
    fun `should return not available when api key is blank`() {
        // Given
        val config = HuggingFaceConfig(
            apiKey = "",
            model = HuggingFaceModel.DEEPSEEK_R1.modelId
        )

        // When
        val provider = HuggingFaceProvider(config)

        // Then
        assertFalse(provider.isAvailable())
    }

    @Test
    fun `should have all required models available`() {
        // When & Then
        assertEquals(3, HuggingFaceModel.values().size)
        assertNotNull(HuggingFaceModel.fromModelId("deepseek-ai/DeepSeek-R1:fireworks-ai"))
        assertNotNull(HuggingFaceModel.fromModelId("deepseek-ai/DeepSeek-V3.1-Terminus:novita"))
        assertNotNull(HuggingFaceModel.fromModelId("OpenBuddy/openbuddy-llama3-8b-v21.1-8k:featherless-ai"))
        assertNull(HuggingFaceModel.fromModelId("non-existent-model"))
    }
}