package com.example

import com.example.core.ai.ModelRouter
import com.example.core.ai.ProviderSeeds
import com.example.core.ai.TaskCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRouterTest {

    private val router = ModelRouter()
    private val providers = ProviderSeeds.defaultProviders()
    private val models = ProviderSeeds.defaultModels()

    @Test
    fun `coding task routes to coding capable model`() {
        val decision = router.selectRoute(
            taskCategory = TaskCategory.CODING,
            providers = providers,
            models = models,
            configuredProviderIds = setOf("mistral", "xpiki")
        )
        assertNotNull(decision)
        assertTrue(decision!!.primaryModel.supportsCoding)
    }

    @Test
    fun `vision task routes to vision capable model`() {
        val decision = router.selectRoute(
            taskCategory = TaskCategory.VISION,
            providers = providers,
            models = models,
            configuredProviderIds = setOf("groq", "gemini"),
            requiresVision = true
        )
        assertNotNull(decision)
        assertEquals("gemini", decision!!.primaryProvider.id)
        assertTrue(decision.primaryModel.supportsVision)
    }

    @Test
    fun `document analysis prefers long context model`() {
        val decision = router.selectRoute(
            taskCategory = TaskCategory.DOCUMENT_ANALYSIS,
            providers = providers,
            models = models,
            configuredProviderIds = setOf("groq", "gemini"),
            requiresLongContext = true
        )
        assertNotNull(decision)
        assertTrue(decision!!.primaryModel.contextWindow >= 128000)
    }

    @Test
    fun `image generation routes to image gen capable model`() {
        val decision = router.selectRoute(
            taskCategory = TaskCategory.IMAGE_GEN,
            providers = providers,
            models = models,
            configuredProviderIds = setOf("xpiki", "groq")
        )
        assertNotNull(decision)
        assertEquals("xpiki", decision!!.primaryProvider.id)
        assertTrue(decision.primaryModel.supportsImageGen)
    }

    @Test
    fun `manual override forces selected provider`() {
        val decision = router.selectRoute(
            taskCategory = TaskCategory.CHAT,
            providers = providers,
            models = models,
            configuredProviderIds = setOf("xpiki", "groq"),
            routingMode = "MANUAL",
            preferredProviderId = "groq",
            preferredModelId = "llama-3.3-70b-versatile"
        )
        assertNotNull(decision)
        assertEquals("groq", decision!!.primaryProvider.id)
        assertEquals("llama-3.3-70b-versatile", decision.primaryModel.modelId)
    }
}
