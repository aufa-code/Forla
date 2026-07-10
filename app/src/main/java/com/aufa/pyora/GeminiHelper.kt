package com.aufa.pyora

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class FoodAiResult(
    val name: String,
    val calories: Double,
    val protein: Double,
    val fat: Double,
    val carbs: Double
)

private data class HttpResult(val code: Int, val body: String)

object GeminiHelper {
    // Dicoba BERURUTAN. Kalau satu sibuk (503) / gak ada (404), lanjut model berikutnya.
    // -lite ditaro depan karena paling jarang overload.
    private val MODELS = listOf(
        "gemini-2.0-flash",        // akurasi lebih baik, dicoba dulu
        "gemini-flash-latest",     // model terbaru
        "gemini-2.0-flash-lite",   // cadangan pas yang atas sibuk
        "gemini-flash-lite-latest"
    )

    suspend fun analyzeFood(apiKey: String, bitmap: Bitmap, takaran: String): FoodAiResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw RuntimeException("API key kosong. Cek local.properties.")

            val scaled = scaleDown(bitmap, 768)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Kamu ahli gizi. Analisis makanan/minuman pada foto ini.
                Takaran dari user: "${takaran.ifBlank { "tidak disebut, perkirakan porsi wajar" }}".
                Jawab HANYA JSON valid tanpa teks lain, format persis:
                {"name":"nama singkat","calories":angka,"protein":angka,"fat":angka,"carbs":angka}
                Satuan: calories = kkal; protein/fat/carbs = gram. Semua nilai berupa angka (boleh desimal).
            """.trimIndent()

            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", b64)
                        }))
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }.toString()

            var lastError = "tidak diketahui"
            // 2 putaran: tiap putaran coba semua model. Sibuk semua -> tunggu, ulang sekali.
            repeat(2) { round ->
                for (model in MODELS) {
                    val res = postGemini(model, apiKey, body)
                    if (res.code in 200..299) return@withContext parse(res.body)

                    lastError = "error ${res.code} ($model)"
                    val skipToNext = res.code == 503 || res.code == 404 ||
                            res.code == 429 || res.code == 500
                    if (!skipToNext) throw RuntimeException("Gemini ${res.code}: ${res.body}")
                }
                if (round == 0) delay(2000)
            }
            throw RuntimeException("Semua model Gemini lagi sibuk. Tunggu bentar & coba lagi. ($lastError)")
        }

    private fun postGemini(model: String, apiKey: String, body: String): HttpResult {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 30000
            readTimeout = 30000
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResult(code, text)
        } catch (e: Exception) {
            HttpResult(-1, e.message ?: "network error")
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(resp: String): FoodAiResult {
        val text = JSONObject(resp)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text")
        val json = JSONObject(extractJson(text))
        return FoodAiResult(
            name = json.optString("name", ""),
            calories = json.optDouble("calories", 0.0),
            protein = json.optDouble("protein", 0.0),
            fat = json.optDouble("fat", 0.0),
            carbs = json.optDouble("carbs", 0.0)
        )
    }

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val max = maxOf(bmp.width, bmp.height)
        if (max <= maxDim) return bmp
        val scale = maxDim.toFloat() / max
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }

    private fun extractJson(text: String): String {
        val s = text.indexOf('{'); val e = text.lastIndexOf('}')
        return if (s >= 0 && e > s) text.substring(s, e + 1) else text
    }
}