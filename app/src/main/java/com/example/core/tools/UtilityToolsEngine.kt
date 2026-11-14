package com.example.core.tools

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object UtilityToolsEngine {

    private val secureRandom = SecureRandom()

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    fun decodeBarcodeOrQr(bitmap: Bitmap): String? {
        return try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val source = RGBLuminanceSource(w, h, pixels)
            val binary = BinaryBitmap(HybridBinarizer(source))
            MultiFormatReader().decode(binary).text
        } catch (_: Throwable) {
            null
        }
    }

    fun generatePassword(length: Int = 18, includeSymbols: Boolean = true): String {
        val upper = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val lower = "abcdefghijkmnopqrstuvwxyz"
        val digits = "23456789"
        val symbols = "!@#\$%^&*()-_=+[]{}"
        val pool = upper + lower + digits + if (includeSymbols) symbols else ""
        val safeLen = length.coerceIn(8, 64)
        return buildString {
            append(upper[secureRandom.nextInt(upper.length)])
            append(lower[secureRandom.nextInt(lower.length)])
            append(digits[secureRandom.nextInt(digits.length)])
            if (includeSymbols) append(symbols[secureRandom.nextInt(symbols.length)])
            while (this.length < safeLen) {
                append(pool[secureRandom.nextInt(pool.length)])
            }
        }.toList().shuffled(secureRandom).joinToString("")
    }

    fun formatJson(raw: String): Result<String> {
        val t = raw.trim()
        return try {
            val pretty = when {
                t.startsWith("{") -> JSONObject(t).toString(2)
                t.startsWith("[") -> JSONArray(t).toString(2)
                else -> return Result.failure(IllegalArgumentException("JSON must start with { or ["))
            }
            Result.success(pretty)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun testRegex(pattern: String, input: String): Result<List<String>> {
        return try {
            val rx = Regex(pattern)
            Result.success(rx.findAll(input).map { it.value }.toList())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun encodeBase64(text: String): String =
        Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    fun decodeBase64(b64: String): Result<String> = try {
        val bytes = Base64.decode(b64.trim(), Base64.DEFAULT)
        Result.success(String(bytes, StandardCharsets.UTF_8))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    fun encodeUrl(text: String): String = URLEncoder.encode(text, "UTF-8")

    fun decodeUrl(text: String): Result<String> = try {
        Result.success(URLDecoder.decode(text, "UTF-8"))
    } catch (t: Throwable) {
        Result.failure(t)
    }

    fun convertTimeZone(sourceZone: String, targetZone: String): Result<String> {
        return try {
            val now = Instant.now()
            val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            val src = now.atZone(ZoneId.of(sourceZone)).format(fmt)
            val dst = now.atZone(ZoneId.of(targetZone)).format(fmt)
            Result.success("$src  ->  $dst")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun convertUnit(value: Double, category: String, from: String, to: String): Double {
        if (category == "Temperature") {
            val celsius = when (from) {
                "C" -> value
                "F" -> (value - 32.0) * 5.0 / 9.0
                "K" -> value - 273.15
                else -> value
            }
            return when (to) {
                "C" -> celsius
                "F" -> celsius * 9.0 / 5.0 + 32.0
                "K" -> celsius + 273.15
                else -> celsius
            }
        }
        val toBaseFactor: Map<String, Double> = when (category) {
            "Length" -> mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mi" to 1609.344, "ft" to 0.3048)
            "Mass" -> mapOf("kg" to 1.0, "g" to 0.001, "lb" to 0.45359237, "oz" to 0.028349523)
            "Data" -> mapOf("MB" to 1.0, "KB" to 1.0 / 1024.0, "GB" to 1024.0, "TB" to 1024.0 * 1024.0)
            else -> mapOf("m" to 1.0)
        }
        val f1 = toBaseFactor[from] ?: 1.0
        val f2 = toBaseFactor[to] ?: 1.0
        return (value * f1) / f2
    }

    /** Safe recursive-descent arithmetic parser supporting +, -, *, /, ^, and parentheses. */
    fun evaluateMath(expression: String): Result<Double> {
        val clean = expression.replace(" ", "")
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Empty expression"))
        return try {
            val parser = object {
                var pos = -1
                var ch = 0
                fun nextChar() {
                    ch = if (++pos < clean.length) clean[pos].code else -1
                }
                fun eat(charToEat: Int): Boolean {
                    while (ch == ' '.code) nextChar()
                    if (ch == charToEat) {
                        nextChar()
                        return true
                    }
                    return false
                }
                fun parse(): Double {
                    nextChar()
                    val x = parseExpression()
                    if (pos < clean.length) throw IllegalArgumentException("Unexpected char: ${clean[pos]}")
                    return x
                }
                fun parseExpression(): Double {
                    var x = parseTerm()
                    while (true) {
                        x = when {
                            eat('+'.code) -> x + parseTerm()
                            eat('-'.code) -> x - parseTerm()
                            else -> return x
                        }
                    }
                }
                fun parseTerm(): Double {
                    var x = parseFactor()
                    while (true) {
                        x = when {
                            eat('*'.code) -> x * parseFactor()
                            eat('/'.code) -> {
                                val d = parseFactor()
                                if (d == 0.0) throw ArithmeticException("Division by zero")
                                x / d
                            }
                            else -> return x
                        }
                    }
                }
                fun parseFactor(): Double {
                    if (eat('+'.code)) return parseFactor()
                    if (eat('-'.code)) return -parseFactor()
                    var x: Double
                    val startPos = pos
                    if (eat('('.code)) {
                        x = parseExpression()
                        if (!eat(')'.code)) throw IllegalArgumentException("Missing ')'")
                    } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                        while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                        x = clean.substring(startPos, pos).toDouble()
                    } else {
                        throw IllegalArgumentException("Invalid token")
                    }
                    if (eat('^'.code)) x = Math.pow(x, parseFactor())
                    return x
                }
            }
            Result.success(parser.parse())
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
