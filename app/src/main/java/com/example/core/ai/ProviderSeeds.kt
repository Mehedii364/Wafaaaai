package com.example.core.ai

import com.example.core.database.AIModelEntity
import com.example.core.database.AIProviderEntity

object ProviderSeeds {

    fun defaultProviders(): List<AIProviderEntity> = listOf(
        AIProviderEntity(
            id = "xpiki",
            name = "XPiKi",
            category = "LLM / Multimodal",
            baseUrl = "https://api.xpiki.com/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 1,
            isFreeTier = true,
            freeTierNotes = "Supports model discovery, chat completions, streaming, and compatible image endpoints per docs.xpiki.com",
            docsUrl = "https://docs.xpiki.com/",
            capabilitiesCsv = "CHAT,CODING,WRITING,VISION,IMAGE_GEN,RESEARCH,STREAMING",
            selectedModelId = "gpt-4o-mini"
        ),
        AIProviderEntity(
            id = "gemini",
            name = "Google Gemini",
            category = "Multimodal / Long Context",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            authMethod = "x-goog-api-key Header",
            isEnabled = true,
            priority = 2,
            isFreeTier = true,
            freeTierNotes = "Free tier via Google AI Studio with RPM/TPM quotas; up to 1M+ context window",
            docsUrl = "https://ai.google.dev/gemini-api/docs",
            capabilitiesCsv = "CHAT,CODING,WRITING,DOCUMENT_ANALYSIS,VISION,IMAGE_GEN,AUDIO,VIDEO,RESEARCH,STREAMING",
            selectedModelId = "gemini-2.5-flash"
        ),
        AIProviderEntity(
            id = "openrouter",
            name = "OpenRouter",
            category = "Multi-Model Aggregator",
            baseUrl = "https://openrouter.ai/api/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 3,
            isFreeTier = true,
            freeTierNotes = "Hosts both paid and :free community models with unified billing",
            docsUrl = "https://openrouter.ai/docs",
            capabilitiesCsv = "CHAT,CODING,WRITING,DOCUMENT_ANALYSIS,VISION,RESEARCH,STREAMING",
            selectedModelId = "google/gemini-2.0-flash-exp:free"
        ),
        AIProviderEntity(
            id = "groq",
            name = "Groq",
            category = "Ultra-Fast Inference / Speech",
            baseUrl = "https://api.groq.com/openai/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 4,
            isFreeTier = true,
            freeTierNotes = "Generous developer free tier for Llama, Qwen, and Whisper STT",
            docsUrl = "https://console.groq.com/docs",
            capabilitiesCsv = "CHAT,CODING,WRITING,AUDIO,STREAMING",
            selectedModelId = "llama-3.3-70b-versatile"
        ),
        AIProviderEntity(
            id = "mistral",
            name = "Mistral AI",
            category = "LLM / Codestral",
            baseUrl = "https://api.mistral.ai/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 5,
            isFreeTier = true,
            freeTierNotes = "Experiment tier available; Codestral specialized for software engineering",
            docsUrl = "https://docs.mistral.ai/",
            capabilitiesCsv = "CHAT,CODING,WRITING,VISION,DOCUMENT_ANALYSIS,STREAMING",
            selectedModelId = "mistral-large-latest"
        ),
        AIProviderEntity(
            id = "cohere",
            name = "Cohere",
            category = "Enterprise RAG / Writing",
            baseUrl = "https://api.cohere.com/v2",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 6,
            isFreeTier = true,
            freeTierNotes = "Trial API key with rate limits; strong multilingual & document grounding",
            docsUrl = "https://docs.cohere.com/",
            capabilitiesCsv = "CHAT,WRITING,DOCUMENT_ANALYSIS,RESEARCH",
            selectedModelId = "command-a-03-2025"
        ),
        AIProviderEntity(
            id = "cloudflare",
            name = "Cloudflare Workers AI",
            category = "Serverless Edge AI",
            baseUrl = "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/v1",
            authMethod = "Bearer API Key + Account ID",
            isEnabled = true,
            priority = 7,
            isFreeTier = true,
            freeTierNotes = "10,000 Neurons/day free tier; requires Account ID in provider settings",
            docsUrl = "https://developers.cloudflare.com/workers-ai/",
            capabilitiesCsv = "CHAT,CODING,WRITING,STREAMING",
            selectedModelId = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
        ),
        AIProviderEntity(
            id = "sambanova",
            name = "SambaNova Cloud",
            category = "High-Speed Inference",
            baseUrl = "https://api.sambanova.ai/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 8,
            isFreeTier = true,
            freeTierNotes = "Developer free tier available with high token throughput",
            docsUrl = "https://docs.sambanova.ai/",
            capabilitiesCsv = "CHAT,CODING,WRITING,RESEARCH,STREAMING",
            selectedModelId = "Meta-Llama-3.3-70B-Instruct"
        ),
        AIProviderEntity(
            id = "nvidia",
            name = "NVIDIA NIM",
            category = "GPU Cloud Inference",
            baseUrl = "https://integrate.api.nvidia.com/v1",
            authMethod = "Bearer API Key",
            isEnabled = true,
            priority = 9,
            isFreeTier = true,
            freeTierNotes = "Free developer credits on build.nvidia.com",
            docsUrl = "https://docs.api.nvidia.com/nim/",
            capabilitiesCsv = "CHAT,CODING,WRITING,VISION,STREAMING",
            selectedModelId = "meta/llama-3.3-70b-instruct"
        ),
        AIProviderEntity(
            id = "huggingface",
            name = "Hugging Face",
            category = "Open Models Router",
            baseUrl = "https://router.huggingface.co/v1",
            authMethod = "Bearer HF Token",
            isEnabled = true,
            priority = 10,
            isFreeTier = true,
            freeTierNotes = "Free monthly inference credits on Hugging Face Router",
            docsUrl = "https://huggingface.co/docs/inference-providers",
            capabilitiesCsv = "CHAT,CODING,WRITING,STREAMING",
            selectedModelId = "Qwen/Qwen2.5-Coder-32B-Instruct"
        ),
        AIProviderEntity(
            id = "openai_compat",
            name = "OpenAI-Compatible / Proxy",
            category = "Custom Proxy / Enterprise",
            baseUrl = "https://api.openai.com/v1",
            authMethod = "Bearer API Key",
            isEnabled = false,
            priority = 11,
            isFreeTier = false,
            freeTierNotes = "Connect any OpenAI-compatible gateway, LiteLLM, or private backend proxy",
            docsUrl = "https://platform.openai.com/docs/api-reference",
            capabilitiesCsv = "CHAT,CODING,WRITING,VISION,IMAGE_GEN,AUDIO,STREAMING",
            selectedModelId = "gpt-4o-mini",
            isCustomProxy = true
        ),
        AIProviderEntity(
            id = "local_ollama",
            name = "Local AI (Ollama / LM Studio)",
            category = "Local LAN / On-Device Proxy",
            baseUrl = "http://127.0.0.1:11434/v1",
            authMethod = "Optional Bearer / None",
            isEnabled = false,
            priority = 12,
            isFreeTier = true,
            freeTierNotes = "Private local inference via Ollama or LM Studio on device/LAN IP",
            docsUrl = "https://github.com/ollama/ollama/blob/main/docs/openai.md",
            capabilitiesCsv = "CHAT,CODING,WRITING,STREAMING",
            selectedModelId = "llama3.2",
            isCustomProxy = true
        )
    )

    fun defaultModels(): List<AIModelEntity> = listOf(
        // XPiKi
        AIModelEntity("xpiki", "gpt-4o-mini", "XPiKi Fast Omni (gpt-4o-mini)", 128000, supportsVision = true, supportsCoding = true),
        AIModelEntity("xpiki", "gpt-4o", "XPiKi Omni Pro (gpt-4o)", 128000, supportsVision = true, supportsCoding = true),
        AIModelEntity("xpiki", "claude-3-5-sonnet", "Claude 3.5 Sonnet (via XPiKi)", 200000, supportsVision = true, supportsCoding = true),
        AIModelEntity("xpiki", "dall-e-3", "Image Studio Model (dall-e-3)", 4096, supportsCoding = false, supportsImageGen = true),

        // Gemini
        AIModelEntity("gemini", "gemini-2.5-flash", "Gemini 2.5 Flash", 1048576, supportsVision = true, supportsCoding = true, supportsAudio = true, supportsSearch = true),
        AIModelEntity("gemini", "gemini-2.5-pro", "Gemini 2.5 Pro", 1048576, supportsVision = true, supportsCoding = true, supportsAudio = true, supportsSearch = true),
        AIModelEntity("gemini", "gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, supportsVision = true, supportsCoding = true, supportsAudio = true),
        AIModelEntity("gemini", "imagen-3.0-generate-002", "Imagen 3.0", 4096, supportsCoding = false, supportsImageGen = true),

        // OpenRouter
        AIModelEntity("openrouter", "google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash (Free)", 1048576, supportsVision = true, supportsCoding = true),
        AIModelEntity("openrouter", "qwen/qwen-2.5-coder-32b-instruct:free", "Qwen 2.5 Coder 32B (Free)", 32768, supportsCoding = true),
        AIModelEntity("openrouter", "deepseek/deepseek-chat-v3-0324:free", "DeepSeek V3 (Free)", 65536, supportsCoding = true),
        AIModelEntity("openrouter", "anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet", 200000, supportsVision = true, supportsCoding = true),

        // Groq
        AIModelEntity("groq", "llama-3.3-70b-versatile", "Llama 3.3 70B Versatile", 128000, supportsCoding = true),
        AIModelEntity("groq", "llama-3.1-8b-instant", "Llama 3.1 8B Instant", 128000, supportsCoding = true),
        AIModelEntity("groq", "qwen-qwq-32b", "Qwen QwQ 32B Reasoning", 128000, supportsCoding = true),
        AIModelEntity("groq", "whisper-large-v3-turbo", "Whisper Large V3 Turbo (STT)", 4096, supportsCoding = false, supportsAudio = true),

        // Mistral
        AIModelEntity("mistral", "mistral-large-latest", "Mistral Large Latest", 128000, supportsCoding = true),
        AIModelEntity("mistral", "codestral-latest", "Codestral Latest", 256000, supportsCoding = true),
        AIModelEntity("mistral", "pixtral-large-latest", "Pixtral Large Vision", 128000, supportsVision = true, supportsCoding = true),

        // Cohere
        AIModelEntity("cohere", "command-a-03-2025", "Command A (03-2025)", 256000, supportsCoding = true, supportsSearch = true),
        AIModelEntity("cohere", "command-r-plus", "Command R+", 128000, supportsCoding = true, supportsSearch = true),

        // Cloudflare
        AIModelEntity("cloudflare", "@cf/meta/llama-3.3-70b-instruct-fp8-fast", "Llama 3.3 70B FP8 Fast", 24000, supportsCoding = true),
        AIModelEntity("cloudflare", "@cf/qwen/qwen2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B", 32768, supportsCoding = true),

        // SambaNova
        AIModelEntity("sambanova", "Meta-Llama-3.3-70B-Instruct", "Llama 3.3 70B Instruct", 128000, supportsCoding = true),
        AIModelEntity("sambanova", "DeepSeek-R1", "DeepSeek R1", 65536, supportsCoding = true),

        // NVIDIA NIM
        AIModelEntity("nvidia", "meta/llama-3.3-70b-instruct", "Llama 3.3 70B NIM", 128000, supportsCoding = true),
        AIModelEntity("nvidia", "qwen/qwen2.5-coder-32b-instruct", "Qwen 2.5 Coder 32B NIM", 32768, supportsCoding = true),

        // Hugging Face
        AIModelEntity("huggingface", "Qwen/Qwen2.5-Coder-32B-Instruct", "Qwen 2.5 Coder 32B", 32768, supportsCoding = true),
        AIModelEntity("huggingface", "meta-llama/Llama-3.3-70B-Instruct", "Llama 3.3 70B Instruct", 128000, supportsCoding = true),

        // OpenAI-compatible
        AIModelEntity("openai_compat", "gpt-4o-mini", "GPT-4o Mini Compatible", 128000, supportsVision = true, supportsCoding = true),

        // Local Ollama
        AIModelEntity("local_ollama", "llama3.2", "Llama 3.2 Local", 128000, supportsCoding = true),
        AIModelEntity("local_ollama", "qwen2.5-coder", "Qwen 2.5 Coder Local", 32768, supportsCoding = true)
    )
}
