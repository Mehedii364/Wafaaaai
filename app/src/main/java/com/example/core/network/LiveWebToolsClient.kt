package com.example.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebCitation(
    val sourceName: String,
    val title: String,
    val url: String,
    val snippet: String
)

data class WeatherSnapshot(
    val cityName: String,
    val temperatureC: Double,
    val windSpeedKmh: Double,
    val humidityPercent: Int,
    val conditionLabel: String
)

data class CurrencyQuote(
    val amount: Double,
    val from: String,
    val to: String,
    val convertedAmount: Double,
    val date: String
)

/**
 * Real, zero-mock public API client powering live Weather, FX Currency, Research citations,
 * Web URL reading, and Translation without fabricating any data.
 */
class LiveWebToolsClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun searchVerifiedSources(query: String): List<WebCitation> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val results = mutableListOf<WebCitation>()

        // 1. Wikipedia Opensearch + Summary
        try {
            val req = Request.Builder()
                .url("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=3&format=json")
                .header("User-Agent", "WafaAiAndroid/1.0")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val arr = JSONObject(res.body?.string().orEmpty())
                        .optJSONObject("query")?.optJSONArray("search")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val title = item.optString("title")
                            val snippet = item.optString("snippet").replace(Regex("<[^>]*>"), "")
                            val slug = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                            results += WebCitation(
                                sourceName = "Wikipedia",
                                title = title,
                                url = "https://en.wikipedia.org/wiki/$slug",
                                snippet = snippet
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 2. DuckDuckGo Instant Answer
        try {
            val req = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "WafaAiAndroid/1.0")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JSONObject(res.body?.string().orEmpty())
                    val absText = json.optString("AbstractText")
                    val absUrl = json.optString("AbstractURL")
                    val heading = json.optString("Heading").ifBlank { q }
                    if (absText.isNotBlank() && absUrl.isNotBlank()) {
                        results += WebCitation(
                            sourceName = "DuckDuckGo",
                            title = heading,
                            url = absUrl,
                            snippet = absText
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 3. Crossref Academic Papers
        try {
            val req = Request.Builder()
                .url("https://api.crossref.org/works?query=$encoded&rows=2")
                .header("User-Agent", "WafaAiAndroid/1.0 (mailto:support@wafa-ai.local)")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val items = JSONObject(res.body?.string().orEmpty())
                        .optJSONObject("message")?.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val title = item.optJSONArray("title")?.optString(0).orEmpty()
                            val url = item.optString("URL")
                            val publisher = item.optString("publisher")
                            if (title.isNotBlank() && url.isNotBlank()) {
                                results += WebCitation(
                                    sourceName = "Crossref Academic",
                                    title = title,
                                    url = url,
                                    snippet = "Publisher: $publisher • DOI: ${item.optString("DOI")}"
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 4. arXiv Preprints
        try {
            val req = Request.Builder()
                .url("https://export.arxiv.org/api/query?search_query=all:$encoded&start=0&max_results=2")
                .header("User-Agent", "WafaAiAndroid/1.0")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    results += parseArxivXml(res.body?.string().orEmpty())
                }
            }
        } catch (_: Throwable) {
        }

        results
    }

    suspend fun fetchUrlMarkdown(url: String): Result<String> = withContext(Dispatchers.IO) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("URL must start with http:// or https://"))
        }
        return@withContext try {
            val req = Request.Builder()
                .url("https://r.jina.ai/$clean")
                .header("User-Agent", "WafaAiAndroid/1.0")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    Result.success(res.body?.string().orEmpty().take(12000))
                } else {
                    Result.failure(IllegalStateException("HTTP ${res.code} while reading URL"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchWeatherByCity(city: String): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        return@withContext try {
            val enc = URLEncoder.encode(city.trim(), "UTF-8")
            val geoReq = Request.Builder()
                .url("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=1&language=en&format=json")
                .build()
            val geoRes = client.newCall(geoReq).execute().use { it.body?.string().orEmpty() }
            val first = JSONObject(geoRes).optJSONArray("results")?.optJSONObject(0)
                ?: return@withContext Result.failure(IllegalStateException("City not found"))

            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            val resolvedName = listOfNotNull(
                first.optString("name").takeIf { it.isNotBlank() },
                first.optString("country").takeIf { it.isNotBlank() }
            ).joinToString(", ")

            fetchWeatherByCoords(lat, lon, resolvedName)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun fetchWeatherByCoords(lat: Double, lon: Double, label: String = "Current Location"): Result<WeatherSnapshot> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val req = Request.Builder()
                    .url("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code")
                    .build()
                val raw = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val current = JSONObject(raw).getJSONObject("current")
                val code = current.optInt("weather_code", 0)
                Result.success(
                    WeatherSnapshot(
                        cityName = label,
                        temperatureC = current.getDouble("temperature_2m"),
                        windSpeedKmh = current.getDouble("wind_speed_10m"),
                        humidityPercent = current.optInt("relative_humidity_2m", 0),
                        conditionLabel = weatherCodeToLabel(code)
                    )
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun convertCurrency(amount: Double, from: String, to: String): Result<CurrencyQuote> =
        withContext(Dispatchers.IO) {
            if (from.equals(to, ignoreCase = true)) {
                return@withContext Result.success(CurrencyQuote(amount, from.uppercase(), to.uppercase(), amount, "Today"))
            }
            return@withContext try {
                val req = Request.Builder()
                    .url("https://api.frankfurter.app/latest?amount=$amount&from=${from.uppercase()}&to=${to.uppercase()}")
                    .build()
                val res = client.newCall(req).execute()
                res.use {
                    if (!it.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("Unsupported currency pair or HTTP ${it.code}"))
                    }
                    val obj = JSONObject(it.body?.string().orEmpty())
                    val rateVal = obj.getJSONObject("rates").getDouble(to.uppercase())
                    Result.success(
                        CurrencyQuote(
                            amount = amount,
                            from = from.uppercase(),
                            to = to.uppercase(),
                            convertedAmount = rateVal,
                            date = obj.optString("date")
                        )
                    )
                }
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    suspend fun translateOfflineOrMyMemory(text: String, fromLang: String, toLang: String): Result<String> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val enc = URLEncoder.encode(text.take(450), "UTF-8")
                val req = Request.Builder()
                    .url("https://api.mymemory.translated.net/get?q=$enc&langpair=$fromLang|$toLang")
                    .build()
                val raw = client.newCall(req).execute().use { it.body?.string().orEmpty() }
                val translated = JSONObject(raw)
                    .optJSONObject("responseData")
                    ?.optString("translatedText")
                    .orEmpty()
                if (translated.isBlank()) Result.failure(IllegalStateException("Empty translation"))
                else Result.success(translated)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun parseArxivXml(xml: String): List<WebCitation> {
        val list = mutableListOf<WebCitation>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var inEntry = false
            var tag = ""
            var title = ""
            var id = ""
            var summary = ""

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        tag = parser.name
                        if (tag == "entry") {
                            inEntry = true
                            title = ""
                            id = ""
                            summary = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (inEntry) {
                        when (tag) {
                            "title" -> title += parser.text
                            "id" -> id += parser.text
                            "summary" -> summary += parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        if (title.isNotBlank() && id.isNotBlank()) {
                            list += WebCitation(
                                sourceName = "arXiv",
                                title = title.trim().replace(Regex("\\s+"), " "),
                                url = id.trim(),
                                snippet = summary.trim().replace(Regex("\\s+"), " ").take(260)
                            )
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Throwable) {
        }
        return list
    }

    private fun weatherCodeToLabel(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2, 3 -> "Partly Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        71, 73, 75 -> "Snow"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Variable Conditions"
    }
}
