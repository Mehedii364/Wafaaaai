package com.example.core.ai

data class ExternalApiIntegration(
    val id: Int,
    val name: String,
    val category: String,
    val baseUrl: String,
    val authMethod: String,
    val capabilities: String,
    val freeTierStatus: String,
    val usageLimits: String,
    val docsUrl: String,
    val liveCallableInApp: Boolean = false
)

object ExternalApiCatalog {

    val categories: List<String> = listOf(
        "All",
        "LLM",
        "Coding",
        "Search",
        "Research",
        "Weather",
        "Location",
        "Maps",
        "Translation",
        "OCR",
        "Image",
        "Video",
        "Audio",
        "Speech",
        "Documents",
        "Data",
        "Finance",
        "Developer tools",
        "Messaging",
        "Utilities"
    )

    /**
     * Curated registry of 75 documented external API integrations across all 19 operational domains.
     * Free-tier status is never assumed; each record documents real auth, limits, and official docs.
     */
    val integrations: List<ExternalApiIntegration> = listOf(
        // 1. LLM (6)
        ExternalApiIntegration(1, "Google Gemini API", "LLM", "https://generativelanguage.googleapis.com/v1beta", "API Key (x-goog-api-key)", "Chat, Multimodal, Long Context, JSON", "Free Tier + Pay-as-you-go", "RPM/TPM limits by tier", "https://ai.google.dev/gemini-api/docs", true),
        ExternalApiIntegration(2, "XPiKi AI API", "LLM", "https://api.xpiki.com/v1", "Bearer Token", "Chat, Model Discovery, Streaming, Image", "BYOK Plan Dependent", "Per-account token quota", "https://docs.xpiki.com/", true),
        ExternalApiIntegration(3, "OpenRouter", "LLM", "https://openrouter.ai/api/v1", "Bearer Token", "Unified 200+ Model Routing", "Free Models + Credits", "20 RPM on :free models", "https://openrouter.ai/docs", true),
        ExternalApiIntegration(4, "Groq Cloud", "LLM", "https://api.groq.com/openai/v1", "Bearer Token", "Fast LPU LLM & Whisper STT", "Free Developer Tier", "30 RPM / daily token cap", "https://console.groq.com/docs", true),
        ExternalApiIntegration(5, "Mistral AI", "LLM", "https://api.mistral.ai/v1", "Bearer Token", "Chat, Function Calling, Vision", "Free Experiment Tier", "Rate limited on free tier", "https://docs.mistral.ai/", true),
        ExternalApiIntegration(6, "Cohere v2", "LLM", "https://api.cohere.com/v2", "Bearer Token", "RAG Chat, Rerank, Embeddings", "Trial Key Available", "1000 calls/month trial", "https://docs.cohere.com/", true),

        // 2. Coding (4)
        ExternalApiIntegration(7, "Mistral Codestral", "Coding", "https://codestral.mistral.ai/v1", "Bearer Token", "FIM & Multi-file Code Gen", "Free Tier Available", "RPM/monthly token cap", "https://docs.mistral.ai/capabilities/code_generation/", true),
        ExternalApiIntegration(8, "SambaNova Cloud", "Coding", "https://api.sambanova.ai/v1", "Bearer Token", "DeepSeek-R1 & Llama Coding", "Free Tier Available", "RPM rate limit", "https://docs.sambanova.ai/", true),
        ExternalApiIntegration(9, "NVIDIA NIM", "Coding", "https://integrate.api.nvidia.com/v1", "Bearer Token", "Qwen Coder, Nemotron", "Free Developer Credits", "1000-5000 trial credits", "https://docs.api.nvidia.com/nim/", true),
        ExternalApiIntegration(10, "Hugging Face Router", "Coding", "https://router.huggingface.co/v1", "Bearer Token", "Open-Weights Code Models", "Free Monthly Quota", "Usage-capped free credits", "https://huggingface.co/docs/inference-providers", true),

        // 3. Search (4)
        ExternalApiIntegration(11, "DuckDuckGo Instant Answer", "Search", "https://api.duckduckgo.com/", "None (Public)", "Entity Summaries & Topics", "Free / No Key", "Fair-use rate limit", "https://duckduckgo.com/api", true),
        ExternalApiIntegration(12, "Tavily Search API", "Search", "https://api.tavily.com", "API Key (Body/Bearer)", "AI-Ready Web Search & Extracts", "Free Tier (1000/mo)", "1000 credits/month free", "https://docs.tavily.com/", false),
        ExternalApiIntegration(13, "Brave Search API", "Search", "https://api.search.brave.com/res/v1", "X-Subscription-Token", "Independent Web & News Search", "Free Tier (2000/mo)", "1 QPS / 2000 req/month", "https://brave.com/search/api/", false),
        ExternalApiIntegration(14, "Serper Google Search", "Search", "https://google.serper.dev", "X-API-KEY Header", "SERP, News, Scholar Results", "Free Trial Credits", "2500 free queries once", "https://serper.dev/", false),

        // 4. Research (4)
        ExternalApiIntegration(15, "Wikipedia REST API", "Research", "https://en.wikipedia.org/api/rest_v1", "None (User-Agent)", "Encyclopedia Summaries & Citations", "Free / Open", "200 req/sec fair use", "https://en.wikipedia.org/api/rest_v1/", true),
        ExternalApiIntegration(16, "arXiv Export API", "Research", "https://export.arxiv.org/api/query", "None (Public)", "Academic Preprints Search", "Free / Open", "1 req / 3s recommended", "https://info.arxiv.org/help/api/index.html", true),
        ExternalApiIntegration(17, "Crossref REST API", "Research", "https://api.crossref.org", "None (Polite Pool)", "DOI & Scholarly Paper Metadata", "Free / Open", "Polite pool higher rate", "https://api.crossref.org/swagger-ui/index.html", true),
        ExternalApiIntegration(18, "Semantic Scholar API", "Research", "https://api.semanticscholar.org/graph/v1", "Optional x-api-key", "Academic Citations & Abstracts", "Free Public / Keyed", "100 req / 5 min unkeyed", "https://api.semanticscholar.org/api-docs/", false),

        // 5. Weather (4)
        ExternalApiIntegration(19, "Open-Meteo Weather", "Weather", "https://api.open-meteo.com/v1", "None (No Key)", "Live Forecast, Hourly & Daily", "Free Non-Commercial", "10,000 calls/day", "https://open-meteo.com/en/docs", true),
        ExternalApiIntegration(20, "OpenWeatherMap", "Weather", "https://api.openweathermap.org/data/2.5", "Query appid", "Current, 5-Day, Air Quality", "Free Tier", "60 calls/min, 1M/mo", "https://openweathermap.org/api", false),
        ExternalApiIntegration(21, "WeatherAPI.com", "Weather", "https://api.weatherapi.com/v1", "Query key", "Forecast, Astronomy, Alerts", "Free Tier", "1M calls/month", "https://www.weatherapi.com/docs/", false),
        ExternalApiIntegration(22, "Tomorrow.io", "Weather", "https://api.tomorrow.io/v4", "apikey Header/Query", "Hyperlocal Weather & Insights", "Free Tier", "500 calls/day", "https://docs.tomorrow.io/", false),

        // 6. Location (3)
        ExternalApiIntegration(23, "Open-Meteo Geocoding", "Location", "https://geocoding-api.open-meteo.com/v1", "None (No Key)", "City Name to Lat/Lon & TZ", "Free Non-Commercial", "Fair-use limit", "https://open-meteo.com/en/docs/geocoding-api", true),
        ExternalApiIntegration(24, "Nominatim OpenStreetMap", "Location", "https://nominatim.openstreetmap.org", "User-Agent Header", "Forward & Reverse Geocoding", "Free Public Policy", "Max 1 req/second", "https://nominatim.org/release-docs/latest/api/Overview/", false),
        ExternalApiIntegration(25, "ipapi.co Geolocation", "Location", "https://ipapi.co/json/", "None / Key", "IP Timezone & Country Lookup", "Free Tier", "1000 req/day", "https://ipapi.co/api/", false),

        // 7. Maps (3)
        ExternalApiIntegration(26, "OSRM Routing API", "Maps", "https://router.project-osrm.org", "None (Demo Server)", "Turn-by-Turn Route Geometry", "Free Demo / Self-Host", "Fair-use public server", "http://project-osrm.org/docs/v5.24.0/api/", false),
        ExternalApiIntegration(27, "Mapbox API", "Maps", "https://api.mapbox.com", "access_token Query", "Directions, Tiles, Geocoding", "Free Monthly Tier", "100k requests/month", "https://docs.mapbox.com/api/overview/", false),
        ExternalApiIntegration(28, "Geoapify Maps", "Maps", "https://api.geoapify.com/v1", "apiKey Query", "Places, Routing, Isolines", "Free Tier", "3000 credits/day", "https://apidocs.geoapify.com/", false),

        // 8. Translation (4)
        ExternalApiIntegration(29, "MyMemory Translation", "Translation", "https://api.mymemory.translated.net", "None / Email", "EN <-> BN & 100+ Languages", "Free Daily Quota", "5000 chars/day anonymous", "https://mymemory.translated.net/doc/spec.php", true),
        ExternalApiIntegration(30, "LibreTranslate", "Translation", "https://libretranslate.com", "Optional API Key", "Open Neural Translation", "Paid Hosted / Free Self-Host", "Instance dependent", "https://libretranslate.com/docs/", false),
        ExternalApiIntegration(31, "DeepL API", "Translation", "https://api-free.deepl.com/v2", "DeepL-Auth-Key", "High-Accuracy Document Translation", "Free Tier", "500,000 chars/month", "https://developers.deepl.com/docs", false),
        ExternalApiIntegration(32, "Google Cloud Translation", "Translation", "https://translation.googleapis.com/language/translate/v2", "API Key / OAuth", "Neural MT (130+ Languages)", "Pay-as-you-go ($20 credit)", "500k chars/mo free tier", "https://cloud.google.com/translate/docs", false),

        // 9. OCR (4)
        ExternalApiIntegration(33, "OCR.space API", "OCR", "https://api.ocr.space/parse/image", "apikey Header", "Image & PDF Text Extraction", "Free Tier", "25,000 req/mo, 1MB limit", "https://ocr.space/OCRAPI", false),
        ExternalApiIntegration(34, "Mistral OCR", "OCR", "https://api.mistral.ai/v1/ocr", "Bearer Token", "Structured PDF/Image Markdown OCR", "Pay-as-you-go / Trial", "Per-page pricing", "https://docs.mistral.ai/capabilities/document/", false),
        ExternalApiIntegration(35, "Google Cloud Vision OCR", "OCR", "https://vision.googleapis.com/v1/images:annotate", "API Key / OAuth", "Dense Text & Handwriting OCR", "1000 units/month free", "Per-unit billing after", "https://cloud.google.com/vision/docs/ocr", false),
        ExternalApiIntegration(36, "Azure AI Document Intelligence", "OCR", "https://YOUR_RESOURCE.cognitiveservices.azure.com", "Ocp-Apim-Subscription-Key", "Tables, Invoices, Receipts", "Free F0 Tier", "500 pages/month free", "https://learn.microsoft.com/azure/ai-services/document-intelligence/", false),

        // 10. Image (5)
        ExternalApiIntegration(37, "Google Imagen 3 (Gemini API)", "Image", "https://generativelanguage.googleapis.com/v1beta", "x-goog-api-key", "Text-to-Image Generation", "Paid Tier Required", "Per-image billing", "https://ai.google.dev/gemini-api/docs/imagen", true),
        ExternalApiIntegration(38, "Cloudflare Workers AI Image", "Image", "https://api.cloudflare.com/client/v4/accounts/{id}/ai/run", "Bearer Token", "Flux / SDXL Text-to-Image", "Free Neurons Tier", "10,000 neurons/day", "https://developers.cloudflare.com/workers-ai/models/", false),
        ExternalApiIntegration(39, "Stability AI REST", "Image", "https://api.stability.ai/v2beta", "Bearer Token", "SD3.5, Upscale, Inpaint", "Credits Required", "25 free starter credits", "https://platform.stability.ai/docs/api-reference", false),
        ExternalApiIntegration(40, "Remove.bg API", "Image", "https://api.remove.bg/v1.0/removebg", "X-Api-Key Header", "Background Removal", "Free Preview + 50/mo", "50 low-res calls/month", "https://www.remove.bg/api", false),
        ExternalApiIntegration(41, "Unsplash API", "Image", "https://api.unsplash.com", "Client-ID Header", "High-Res Photography Search", "Free Demo Tier", "50 req/hour demo", "https://unsplash.com/documentation", false),

        // 11. Video (4)
        ExternalApiIntegration(42, "Google Gemini Video Analysis", "Video", "https://generativelanguage.googleapis.com/v1beta", "x-goog-api-key", "Video Scene, Audio & Transcript QA", "Free Tier + Paid", "File API size & duration limits", "https://ai.google.dev/gemini-api/docs/video-understanding", true),
        ExternalApiIntegration(43, "Runway Gen API", "Video", "https://api.dev.runwayml.com/v1", "Bearer Token", "Image-to-Video Generation", "Paid Credits Only", "Credit-based per second", "https://docs.dev.runwayml.com/", false),
        ExternalApiIntegration(44, "Luma Dream Machine API", "Video", "https://api.lumalabs.ai/dream-machine/v1", "Bearer Token", "Text/Image to Video", "Paid Credits", "Per-generation credits", "https://docs.lumalabs.ai/docs/api", false),
        ExternalApiIntegration(45, "Pexels Video Search", "Video", "https://api.pexels.com/videos", "Authorization Header", "Royalty-Free Stock Video Search", "Free Tier", "200 req/hr, 20k/mo", "https://www.pexels.com/api/documentation/", false),

        // 12. Audio (4)
        ExternalApiIntegration(46, "Groq Whisper Audio", "Audio", "https://api.groq.com/openai/v1/audio/transcriptions", "Bearer Token", "Fast Multilingual Audio Transcription", "Free Developer Tier", "25MB file size limit", "https://console.groq.com/docs/speech-to-text", true),
        ExternalApiIntegration(47, "AssemblyAI", "Audio", "https://api.assemblyai.com/v2", "Authorization Header", "Transcription, Diarization, Summaries", "Free Starter Credits", "$50 free trial credits", "https://www.assemblyai.com/docs", false),
        ExternalApiIntegration(48, "Deepgram Nova", "Audio", "https://api.deepgram.com/v1", "Token Header", "Real-Time & Pre-recorded Audio", "Free Starter Credits", "$200 trial credits", "https://developers.deepgram.com/docs", false),
        ExternalApiIntegration(49, "Suno / Udio Compatible Gateway", "Audio", "https://api.aimlapi.com/v2", "Bearer Token", "Music & Audio Synthesis Proxy", "Paid Usage", "Per-generation credits", "https://docs.aimlapi.com/", false),

        // 13. Speech (4)
        ExternalApiIntegration(50, "Android SpeechRecognizer & TTS", "Speech", "on-device://android.speech", "Device Permission", "Offline/Online STT & TTS (EN/BN)", "Free (System Built-in)", "Device engine dependent", "https://developer.android.com/reference/android/speech/SpeechRecognizer", true),
        ExternalApiIntegration(51, "ElevenLabs TTS", "Speech", "https://api.elevenlabs.io/v1", "xi-api-key Header", "Neural Expressive Voices & Dubbing", "Free Tier", "10,000 chars/month", "https://elevenlabs.io/docs/api-reference", false),
        ExternalApiIntegration(52, "Cartesia Sonic TTS", "Speech", "https://api.cartesia.ai", "X-API-Key Header", "Ultra-low Latency Voice Synthesis", "Free Tier", "10,000 credits/month", "https://docs.cartesia.ai/", false),
        ExternalApiIntegration(53, "OpenAI Audio Speech", "Speech", "https://api.openai.com/v1/audio/speech", "Bearer Token", "TTS & Whisper STT", "Pay-as-you-go", "Per-character / minute", "https://platform.openai.com/docs/guides/text-to-speech", false),

        // 14. Documents (4)
        ExternalApiIntegration(54, "WAFA Native OOXML & PDF Engine", "Documents", "on-device://wafa.ooxml", "None (Local)", "DOCX, XLSX, PPTX, PDF Create/Read", "Free / Unlimited Local", "Device memory bounded", "https://developer.android.com/reference/android/graphics/pdf/PdfDocument", true),
        ExternalApiIntegration(55, "LlamaParse (LlamaCloud)", "Documents", "https://api.cloud.llamaindex.ai/api/v1/parsing", "Bearer Token", "Complex PDF Table Extraction", "Free Tier", "1000 pages/day free", "https://docs.cloud.llamaindex.ai/", false),
        ExternalApiIntegration(56, "Unstructured.io API", "Documents", "https://api.unstructuredapp.io/general/v0", "unstructured-api-key", "Document Partitioning & Chunking", "Free Tier / Serverless", "1000 pages trial", "https://docs.unstructured.io/api-reference/overview", false),
        ExternalApiIntegration(57, "Jina Reader API", "Documents", "https://r.jina.ai/", "Optional Bearer", "URL / Web Page to Clean Markdown", "Free Rate-Limited", "20 RPM free / higher keyed", "https://jina.ai/reader/", true),

        // 15. Data (4)
        ExternalApiIntegration(58, "World Bank Open Data", "Data", "https://api.worldbank.org/v2", "None (Public)", "Global Macroeconomic Indicators", "Free / Open", "Public fair use", "https://datahelpdesk.worldbank.org/knowledgebase/topics/125589", false),
        ExternalApiIntegration(59, "REST Countries API", "Data", "https://restcountries.com/v3.1", "None (Public)", "Country Codes, Currencies, Capitals", "Free / Open", "Fair use", "https://restcountries.com/", false),
        ExternalApiIntegration(60, "Supabase REST / PostgREST", "Data", "https://YOUR_PROJECT.supabase.co/rest/v1", "apikey + Bearer", "Cloud Postgres Tables & Auth", "Free Tier (500MB)", "2 active free projects", "https://supabase.com/docs/guides/api", false),
        ExternalApiIntegration(61, "Airtable REST API", "Data", "https://api.airtable.com/v0", "Bearer PAT", "Spreadsheet-Database Sync", "Free Tier", "1000 records/base, 5 req/s", "https://airtable.com/developers/web/api/introduction", false),

        // 16. Finance (4)
        ExternalApiIntegration(62, "Frankfurter ECB FX API", "Finance", "https://api.frankfurter.app", "None (No Key)", "Live Central Bank Currency Rates", "Free / Open Source", "Unlimited fair use", "https://www.frankfurter.app/docs/", true),
        ExternalApiIntegration(63, "ExchangeRate-API", "Finance", "https://v6.exchangerate-api.com/v6", "URL API Key", "160+ Fiat Currency Conversions", "Free Tier", "1500 req/month", "https://www.exchangerate-api.com/docs/overview", false),
        ExternalApiIntegration(64, "CoinGecko Public API", "Finance", "https://api.coingecko.com/api/v3", "Optional x-cg-demo-api-key", "Crypto Market Prices & Charts", "Free Demo Tier", "30 calls/min", "https://docs.coingecko.com/", false),
        ExternalApiIntegration(65, "Alpha Vantage", "Finance", "https://www.alphavantage.co/query", "Query apikey", "Equities, FX, Indicators", "Free Tier", "25 req/day", "https://www.alphavantage.co/documentation/", false),

        // 17. Developer tools (4)
        ExternalApiIntegration(66, "GitHub REST API", "Developer tools", "https://api.github.com", "Optional Bearer PAT", "Repos, Issues, Workflows, Gists", "Free Public + Keyed", "60 req/hr unauth, 5000 auth", "https://docs.github.com/en/rest", true),
        ExternalApiIntegration(67, "GitLab REST API", "Developer tools", "https://gitlab.com/api/v4", "PRIVATE-TOKEN Header", "Repos, Pipelines, Snippets", "Free Tier", "Rate limited per IP/user", "https://docs.gitlab.com/ee/api/rest/", false),
        ExternalApiIntegration(68, "Judge0 CE Code Sandbox", "Developer tools", "https://ce.judge0.com", "Optional RapidAPI Key", "Remote Multi-Language Code Execution", "Free Community / Keyed", "Submission quotas apply", "https://ce.judge0.com/", false),
        ExternalApiIntegration(69, "Piston Execution API", "Developer tools", "https://emkc.org/api/v2/piston", "None (Public)", "Remote Sandboxed Code Execution", "Free Public", "5 req/sec", "https://piston.readthedocs.io/", false),

        // 18. Messaging (3)
        ExternalApiIntegration(70, "Telegram Bot API", "Messaging", "https://api.telegram.org", "Bot Token in URL", "Send Alerts, Files & Summaries", "Free", "30 msg/sec broadcast", "https://core.telegram.org/bots/api", false),
        ExternalApiIntegration(71, "Discord Webhook API", "Messaging", "https://discord.com/api/webhooks", "Webhook URL Secret", "Channel Notifications & Embeds", "Free", "30 req/min per webhook", "https://discord.com/developers/docs/resources/webhook", false),
        ExternalApiIntegration(72, "Slack Incoming Webhooks", "Messaging", "https://hooks.slack.com/services", "Webhook URL Secret", "Workspace Alerts & Briefings", "Free Plan Included", "1 msg/sec burst", "https://api.slack.com/messaging/webhooks", false),

        // 19. Utilities (3)
        ExternalApiIntegration(73, "QR Code Local ZXing Engine", "Utilities", "on-device://zxing.core", "None (Local)", "QR & Barcode Generate / Decode", "Free / Unlimited Local", "Offline capable", "https://github.com/zxing/zxing", true),
        ExternalApiIntegration(74, "Open Trivia / Utility TimeAPI", "Utilities", "https://timeapi.io/api", "None (Public)", "World Timezone & DST Conversions", "Free / Open", "Fair-use limit", "https://timeapi.io/swagger/index.html", false),
        ExternalApiIntegration(75, "Nager.Date Public Holidays", "Utilities", "https://date.nager.at/api/v3", "None (Public)", "100+ Country Holiday Calendars", "Free / Open", "Fair-use limit", "https://date.nager.at/Api", false)
    )
}
