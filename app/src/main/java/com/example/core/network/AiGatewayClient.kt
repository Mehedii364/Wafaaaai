package com.example.core.network

import com.example.core.database.AIModelEntity
import com.example.core.database.AIProviderEntity
import com.example.core.security.SecureCredentialVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChatTurn(
    val role: String, // "system", "user", "assistant"
    val text: String,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)

sealed class StreamEvent {
    data class Delta(val text: String) : StreamEvent()
    data class Completed(
        val fullText: String,
        val latencyMs: Long,
        val promptTokensEst: Int,
        val completionTokensEst: Int
    ) : StreamEvent()
    data class Failure(val statusCode: Int, val message: String, val isRateLimit: Boolean = false) : StreamEvent()
}

data class ConnectionTestResult(
    val success: Boolean,
    val statusCode: Int,
    val latencyMs: Long,
    val discoveredModels: List<AIModelEntity>,
    val message: String
)

data class ImageGenResult(
    val success: Boolean,
    val imageBytes: ByteArray? = null,
    val revisedPrompt: String? = null,
    val error: String? = null
)

class AiGatewayClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun testConnectionAndDiscoverModels(
        provider: AIProviderEntity,
        apiKey: String?,
        accountId: String? = null
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        if (provider.id != "local_ollama" && apiKey.isNullOrBlank()) {
            return@withContext ConnectionTestResult(
                success = false,
                statusCode = 401,
                latencyMs = 0L,
                discoveredModels = emptyList(),
                message = "API key is not configured for ${provider.name}."
            )
        }

        val resolvedBase = resolveBaseUrl(provider.baseUrl, accountId)
        if (resolvedBase.contains("{accountId}")) {
            return@withContext ConnectionTestResult(
                success = false,
                statusCode = 400,
                latencyMs = 0L,
                discoveredModels = emptyList(),
                message = "Account ID is required in URL for ${provider.name}."
            )
        }

        val start = System.currentTimeMillis()
        val requestBuilder = Request.Builder()
        when (provider.id) {
            "gemini" -> {
                requestBuilder
                    .url("$resolvedBase/models")
                    .addHeader("x-goog-api-key", apiKey.orEmpty())
            }
            "cohere" -> {
                requestBuilder
                    .url("$resolvedBase/models")
                    .addHeader("Authorization", "Bearer ${apiKey.orEmpty()}")
            }
            else -> {
                requestBuilder.url("$resolvedBase/models")
                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
            }
        }

        try {
            client.newCall(requestBuilder.get().build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val safeErr = SecureCredentialVault.redactSecretsFromText(bodyStr.take(300))
                    return@withContext ConnectionTestResult(
                        success = false,
                        statusCode = response.code,
                        latencyMs = latency,
                        discoveredModels = emptyList(),
                        message = "HTTP ${response.code}: ${extractApiErrorMessage(safeErr)}"
                    )
                }

                val discovered = parseDiscoveredModels(provider.id, bodyStr)
                return@withContext ConnectionTestResult(
                    success = true,
                    statusCode = response.code,
                    latencyMs = latency,
                    discoveredModels = discovered,
                    message = "Connected (${latency}ms) • ${discovered.size} models discovered"
                )
            }
        } catch (t: Throwable) {
            val latency = System.currentTimeMillis() - start
            return@withContext ConnectionTestResult(
                success = false,
                statusCode = -1,
                latencyMs = latency,
                discoveredModels = emptyList(),
                message = SecureCredentialVault.redactSecretsFromText(t.message ?: "Network error")
            )
        }
    }

    fun streamChat(
        provider: AIProviderEntity,
        modelId: String,
        apiKey: String?,
        turns: List<ChatTurn>,
        accountId: String? = null,
        temperature: Double = 0.6
    ): Flow<StreamEvent> = flow {
        val resolvedBase = resolveBaseUrl(provider.baseUrl, accountId)
        if (resolvedBase.contains("{accountId}")) {
            emit(StreamEvent.Failure(400, "Cloudflare Account ID is missing."))
            return@flow
        }
        if (provider.id != "local_ollama" && apiKey.isNullOrBlank()) {
            emit(StreamEvent.Failure(401, "No API key saved for ${provider.name}."))
            return@flow
        }

        var attempt = 0
        val maxRetries = 2
        while (attempt <= maxRetries) {
            val start = System.currentTimeMillis()
            val promptEst = turns.sumOf { it.text.length / 4 }.coerceAtLeast(1)

            val request = when (provider.id) {
                "gemini" -> buildGeminiStreamRequest(resolvedBase, modelId, apiKey.orEmpty(), turns, temperature)
                "cohere" -> buildCohereRequest(resolvedBase, modelId, apiKey.orEmpty(), turns, temperature)
                else -> buildOpenAiCompatibleStreamRequest(resolvedBase, modelId, apiKey, turns, temperature, provider.id == "openrouter")
            }

            var retryableStatus = false
            var lastCode = -1
            var lastMsg = ""

            try {
                val call = client.newCall(request)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        lastCode = response.code
                        val errBody = response.body?.string().orEmpty()
                        lastMsg = extractApiErrorMessage(SecureCredentialVault.redactSecretsFromText(errBody))
                        retryableStatus = response.code == 429 || response.code >= 500
                    } else {
                        val contentType = response.header("Content-Type").orEmpty()
                        val fullText = StringBuilder()

                        if (provider.id == "cohere" || !contentType.contains("event-stream", ignoreCase = true)) {
                            val raw = response.body?.string().orEmpty()
                            val text = parseNonStreamingResponse(provider.id, raw)
                            if (text.isNotEmpty()) {
                                fullText.append(text)
                                emit(StreamEvent.Delta(text))
                            }
                        } else {
                            val source = response.body?.byteStream()
                            if (source != null) {
                                BufferedReader(InputStreamReader(source)).use { reader ->
                                    var line: String?
                                    while (currentCoroutineContext().isActive) {
                                        line = reader.readLine() ?: break
                                        val trimmed = line.trim()
                                        if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                                        if (trimmed.startsWith("data:")) {
                                            val data = trimmed.removePrefix("data:").trim()
                                            if (data == "[DONE]") break
                                            val chunk = parseStreamChunk(provider.id, data)
                                            if (chunk.isNotEmpty()) {
                                                fullText.append(chunk)
                                                emit(StreamEvent.Delta(chunk))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val latency = System.currentTimeMillis() - start
                        val resultText = fullText.toString()
                        emit(
                            StreamEvent.Completed(
                                fullText = resultText,
                                latencyMs = latency,
                                promptTokensEst = promptEst,
                                completionTokensEst = (resultText.length / 4).coerceAtLeast(1)
                            )
                        )
                        return@flow
                    }
                }
            } catch (t: Throwable) {
                lastCode = -1
                lastMsg = SecureCredentialVault.redactSecretsFromText(t.message ?: "Connection failed")
                retryableStatus = attempt < maxRetries
            }

            if (retryableStatus && attempt < maxRetries) {
                attempt++
                delay(700L * attempt)
            } else {
                emit(
                    StreamEvent.Failure(
                        statusCode = lastCode,
                        message = lastMsg.ifBlank { "Request failed (HTTP $lastCode)" },
                        isRateLimit = lastCode == 429
                    )
                )
                return@flow
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateImage(
        provider: AIProviderEntity,
        modelId: String,
        apiKey: String?,
        prompt: String,
        size: String = "1024x1024"
    ): ImageGenResult = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext ImageGenResult(false, error = "API key required for ${provider.name}")
        }

        val resolvedBase = provider.baseUrl
        try {
            if (provider.id == "gemini") {
                val targetModel = if (modelId.contains("imagen")) modelId else "imagen-3.0-generate-002"
                val json = JSONObject().apply {
                    put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                    put("parameters", JSONObject().put("sampleCount", 1))
                }
                val req = Request.Builder()
                    .url("$resolvedBase/models/$targetModel:predict")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return@withContext ImageGenResult(false, error = "HTTP ${res.code}: ${extractApiErrorMessage(raw)}")
                    }
                    val root = JSONObject(raw)
                    val b64 = root.optJSONArray("predictions")
                        ?.optJSONObject(0)
                        ?.optString("bytesBase64Encoded")
                        .orEmpty()
                    if (b64.isBlank()) {
                        return@withContext ImageGenResult(false, error = "No image bytes returned by Imagen endpoint.")
                    }
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    return@withContext ImageGenResult(true, imageBytes = bytes, revisedPrompt = prompt)
                }
            } else {
                // OpenAI-compatible /images/generations (XPiKi, OpenAI-compatible, proxies)
                val targetModel = if (modelId.contains("dall-e") || modelId.contains("image") || modelId.contains("flux")) {
                    modelId
                } else {
                    "dall-e-3"
                }
                val json = JSONObject().apply {
                    put("model", targetModel)
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)
                    put("response_format", "b64_json")
                }
                val req = Request.Builder()
                    .url("$resolvedBase/images/generations")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        return@withContext ImageGenResult(false, error = "HTTP ${res.code}: ${extractApiErrorMessage(raw)}")
                    }
                    val dataItem = JSONObject(raw).optJSONArray("data")?.optJSONObject(0)
                    val b64 = dataItem?.optString("b64_json").orEmpty()
                    val imageUrl = dataItem?.optString("url").orEmpty()
                    val revised = dataItem?.optString("revised_prompt").takeIf { !it.isNullOrBlank() }

                    val bytes = when {
                        b64.isNotBlank() -> android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        imageUrl.isNotBlank() -> downloadBytes(imageUrl)
                        else -> null
                    }
                    return@withContext if (bytes != null && bytes.isNotEmpty()) {
                        ImageGenResult(true, imageBytes = bytes, revisedPrompt = revised)
                    } else {
                        ImageGenResult(false, error = "Provider returned no downloadable image.")
                    }
                }
            }
        } catch (t: Throwable) {
            return@withContext ImageGenResult(
                false,
                error = SecureCredentialVault.redactSecretsFromText(t.message ?: "Image generation error")
            )
        }
    }

    suspend fun transcribeAudioFile(
        provider: AIProviderEntity,
        apiKey: String?,
        audioFile: File,
        languageCode: String = "en"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("API key required for ${provider.name}"))
        }
        val model = if (provider.id == "groq") "whisper-large-v3-turbo" else "whisper-1"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/m4a".toMediaType())
            )
            .addFormDataPart("model", model)
            .addFormDataPart("language", languageCode)
            .build()

        val req = Request.Builder()
            .url("${provider.baseUrl}/audio/transcriptions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        return@withContext try {
            client.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Result.failure(IllegalStateException("HTTP ${res.code}: ${extractApiErrorMessage(raw)}"))
                } else {
                    val text = JSONObject(raw).optString("text")
                    Result.success(text.ifBlank { raw })
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        val req = Request.Builder().url(url).get().build()
        return client.newCall(req).execute().use { res ->
            if (res.isSuccessful) res.body?.bytes() else null
        }
    }

    private fun buildOpenAiCompatibleStreamRequest(
        baseUrl: String,
        modelId: String,
        apiKey: String?,
        turns: List<ChatTurn>,
        temperature: Double,
        isOpenRouter: Boolean
    ): Request {
        val messagesArr = JSONArray()
        for (turn in turns) {
            val msgObj = JSONObject().put("role", turn.role)
            if (!turn.imageBase64.isNullOrBlank()) {
                val mime = turn.imageMimeType ?: "image/jpeg"
                val contentArr = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", turn.text))
                    .put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", "data:$mime;base64,${turn.imageBase64}"))
                    )
                msgObj.put("content", contentArr)
            } else {
                msgObj.put("content", turn.text)
            }
            messagesArr.put(msgObj)
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArr)
            put("stream", true)
            put("temperature", temperature)
        }

        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (!apiKey.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        if (isOpenRouter) {
            builder.addHeader("HTTP-Referer", "https://github.com/wafa-ai/WAFA-AI")
            builder.addHeader("X-Title", "WAFA AI Android")
        }
        return builder.build()
    }

    private fun buildGeminiStreamRequest(
        baseUrl: String,
        modelId: String,
        apiKey: String,
        turns: List<ChatTurn>,
        temperature: Double
    ): Request {
        val contents = JSONArray()
        var systemText: String? = null

        for (turn in turns) {
            if (turn.role == "system") {
                systemText = turn.text
                continue
            }
            val geminiRole = if (turn.role == "assistant") "model" else "user"
            val parts = JSONArray().put(JSONObject().put("text", turn.text))
            if (!turn.imageBase64.isNullOrBlank()) {
                parts.put(
                    JSONObject().put(
                        "inlineData",
                        JSONObject()
                            .put("mimeType", turn.imageMimeType ?: "image/jpeg")
                            .put("data", turn.imageBase64)
                    )
                )
            }
            contents.put(JSONObject().put("role", geminiRole).put("parts", parts))
        }

        val payload = JSONObject().apply {
            put("contents", contents)
            if (!systemText.isNullOrBlank()) {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemText))))
            }
            put("generationConfig", JSONObject().put("temperature", temperature))
        }

        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models/$modelId:streamGenerateContent?alt=sse")
            .addHeader("x-goog-api-key", apiKey)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildCohereRequest(
        baseUrl: String,
        modelId: String,
        apiKey: String,
        turns: List<ChatTurn>,
        temperature: Double
    ): Request {
        val messagesArr = JSONArray()
        for (turn in turns) {
            messagesArr.put(
                JSONObject()
                    .put("role", turn.role)
                    .put("content", turn.text)
            )
        }
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesArr)
            put("temperature", temperature)
        }
        return Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun parseStreamChunk(providerId: String, jsonData: String): String {
        return try {
            val obj = JSONObject(jsonData)
            if (providerId == "gemini") {
                val candidates = obj.optJSONArray("candidates") ?: return ""
                val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
                }
                sb.toString()
            } else {
                val choices = obj.optJSONArray("choices") ?: return ""
                val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                delta?.optString("content")
                    ?: choices.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun parseNonStreamingResponse(providerId: String, raw: String): String {
        return try {
            val obj = JSONObject(raw)
            when (providerId) {
                "cohere" -> {
                    val content = obj.optJSONObject("message")?.optJSONArray("content")
                    content?.optJSONObject(0)?.optString("text").orEmpty()
                }
                "gemini" -> {
                    obj.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optString("text").orEmpty()
                }
                else -> {
                    obj.optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty()
                }
            }
        } catch (_: Throwable) {
            raw.take(2000)
        }
    }

    private fun parseDiscoveredModels(providerId: String, raw: String): List<AIModelEntity> {
        val result = mutableListOf<AIModelEntity>()
        try {
            val root = JSONObject(raw)
            if (providerId == "gemini") {
                val arr = root.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val fullName = m.optString("name").removePrefix("models/")
                    if (fullName.isBlank()) continue
                    val display = m.optString("displayName").ifBlank { fullName }
                    val limit = m.optInt("inputTokenLimit", 32768)
                    val isImg = fullName.contains("imagen", true)
                    result += AIModelEntity(
                        providerId = providerId,
                        modelId = fullName,
                        displayName = display,
                        contextWindow = limit,
                        supportsVision = fullName.contains("gemini", true),
                        supportsCoding = !isImg,
                        supportsImageGen = isImg,
                        supportsAudio = fullName.contains("gemini", true),
                        isCustomDiscovered = true
                    )
                }
            } else {
                val arr = root.optJSONArray("data") ?: root.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val id = m.optString("id").ifBlank { m.optString("name") }
                    if (id.isBlank()) continue
                    val ctx = m.optInt("context_length", m.optInt("context_window", 32768))
                    result += AIModelEntity(
                        providerId = providerId,
                        modelId = id,
                        displayName = id,
                        contextWindow = ctx,
                        supportsVision = id.contains("vision", true) || id.contains("4o", true) || id.contains("pixtral", true) || id.contains("gemini", true),
                        supportsCoding = !id.contains("whisper", true) && !id.contains("dall-e", true),
                        supportsImageGen = id.contains("dall-e", true) || id.contains("flux", true) || id.contains("image", true),
                        supportsAudio = id.contains("whisper", true),
                        isCustomDiscovered = true
                    )
                }
            }
        } catch (_: Throwable) {
        }
        return result.take(40)
    }

    private fun resolveBaseUrl(template: String, accountId: String?): String {
        return if (!accountId.isNullOrBlank()) {
            template.replace("{accountId}", accountId.trim())
        } else {
            template
        }
    }

    private fun extractApiErrorMessage(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            obj.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: raw.take(240)
        } catch (_: Throwable) {
            raw.take(240)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
