package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed class ProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
class QuotaExceededException(val provider: String, message: String) : ProviderException(message)
class TimeoutException(val provider: String, message: String) : ProviderException(message)
class NetworkException(val provider: String, message: String, cause: Throwable? = null) : ProviderException(message, cause)
class AuthenticationException(val provider: String, message: String) : ProviderException(message)
class ConfigurationException(val provider: String, message: String) : ProviderException(message)
class MalformedResponseException(val provider: String, message: String) : ProviderException(message)
class TransientProviderException(val provider: String, val statusCode: Int, message: String) : ProviderException(message)
class AuthProviderException(val provider: String, message: String) : ProviderException(message)

interface DocumentStructuringProvider {
    val providerName: String
    suspend fun organizeDocument(meetingTitle: String, verbatimTranscript: String): String
}

class GeminiDocumentProvider : DocumentStructuringProvider {
    override val providerName: String = "Gemini 4 Argon"

    private val thinkingModel by lazy {
        val config = generationConfig {
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.HIGH
            }
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = config
        )
    }

    private val flashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    override suspend fun organizeDocument(meetingTitle: String, verbatimTranscript: String): String = withContext(Dispatchers.IO) {
        val prompt = buildStructuringPrompt(meetingTitle, verbatimTranscript)
        try {
            val responseText = withTimeout(60_000L) {
                try {
                    thinkingModel.generateContent(prompt).text
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("GeminiDocProvider", "Gemini 4 Argon failed, trying gemini-3.8-flash", e)
                    flashModel.generateContent(prompt).text
                }
            }

            if (!responseText.isNullOrBlank()) {
                return@withContext responseText.trim()
            } else {
                throw MalformedResponseException(providerName, "Gemini returned empty document content.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Rethrow cancellation immediately without translating or triggering fallback
            throw e
        } catch (e: TimeoutCancellationException) {
            throw TimeoutException(providerName, "Gemini document structuring timed out after 60s.")
        } catch (e: Exception) {
            val msg = e.localizedMessage.orEmpty()
            if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("Quota")) {
                throw QuotaExceededException(providerName, "Gemini quota/rate limit reached: $msg")
            } else if (msg.contains("401") || msg.contains("403") || msg.contains("PERMISSION_DENIED")) {
                throw AuthenticationException(providerName, "Gemini App Check attestation / permission error: $msg")
            } else if (e is IOException || e is SocketTimeoutException) {
                throw NetworkException(providerName, "Gemini network failure: $msg", e)
            }
            throw TransientProviderException(providerName, 500, "Gemini failed: $msg")
        }
    }
}

class GroqDocumentProvider(private val context: Context) : DocumentStructuringProvider {
    override val providerName: String = "Groq Llama 3.3"

    companion object {
        private const val GROQ_ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "llama-3.3-70b-versatile"
        private const val PREFS_NAME = "groq_preferences"
        private const val KEY_CUSTOM_KEY = "custom_groq_api_key"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        // First check custom key entered by user in settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userKey = prefs.getString(KEY_CUSTOM_KEY, null)
        if (!userKey.isNullOrBlank()) {
            return userKey.trim()
        }
        // Then check BuildConfig.GROQ_API_KEY
        return try {
            val field = BuildConfig::class.java.getField("GROQ_API_KEY")
            val key = field.get(null) as? String
            key?.trim().orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    fun setCustomApiKey(key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CUSTOM_KEY, key.trim()).apply()
    }

    fun getCustomApiKey(): String = getApiKey()
    fun saveCustomApiKey(key: String) = setCustomApiKey(key)

    override suspend fun organizeDocument(meetingTitle: String, verbatimTranscript: String): String = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank() || apiKey == "GROQ_API_KEY_PLACEHOLDER") {
            throw ConfigurationException(
                providerName,
                "API Key Groq belum dikonfigurasi. Masukkan kunci di pengaturan atau Secrets panel."
            )
        }

        val prompt = buildStructuringPrompt(meetingTitle, verbatimTranscript)

        val requestJson = JSONObject().apply {
            put("model", GROQ_MODEL)
            put("temperature", 0.3)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put(
                        "content",
                        "Kamu adalah notulis profesional dan editor dokumentasi eksekutif. " +
                                "Tugasmu menyusun rekaman pertemuan ke dalam dokumen rapat Markdown lengkap. " +
                                "ATURAN MUTLAK: ZERO-OMISSION. Pertahankan seluruh isi pembicaraan, sapaan pembuka, " +
                                "pertanyaan, jawaban, sanggahan, dan tindak lanjut tanpa memotong informasi penting."
                    )
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(GROQ_ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                when (response.code) {
                    401, 403 -> throw AuthenticationException(providerName, "Groq API Key tidak valid (HTTP ${response.code}).")
                    429 -> throw QuotaExceededException(providerName, "Groq rate limit exceeded (HTTP 429).")
                    500, 502, 503, 504 -> throw TransientProviderException(providerName, response.code, "Groq service error (HTTP ${response.code}).")
                    else -> throw TransientProviderException(providerName, response.code, "Groq HTTP ${response.code}: $responseBody")
                }
            }

            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val messageObj = choices.getJSONObject(0).optJSONObject("message")
                val content = messageObj?.optString("content")
                if (!content.isNullOrBlank()) {
                    return@withContext content.trim()
                }
            }
            throw MalformedResponseException(providerName, "Groq returned empty document content.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ProviderException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw TimeoutException(providerName, "Groq request timed out.")
        } catch (e: IOException) {
            throw NetworkException(providerName, "Groq network error: ${e.localizedMessage}", e)
        }
    }
}

private fun buildStructuringPrompt(meetingTitle: String, verbatimTranscript: String): String {
    return """
        Susun transkrip pertemuan berikut menjadi Dokumen Rekaman Pertemuan Lengkap yang terstruktur, rapi, dan mudah dibaca.
        
        ATURAN UTAMA (ZERO OMISSION):
        1. DILARANG KERAS meringkas menjadi poin-poin pendek yang menghilangkan dialog asli.
        2. PERTAHANKAN seluruh isi: sapaan pembuka (small talk), pertanyaan, jawaban, klarifikasi, perdebatan, dan kalimat yang belum tuntas.
        3. Gunakan struktur Markdown yang elegan:
           # $meetingTitle
           **Dokumen Rekaman Pertemuan Lengkap**
           ---
           ## 1. Pembuka dan Sambutan
           (Tuliskan percakapan pembuka dan sapaan awal secara mengalir dan lengkap)
           ## 2. Agenda dan Pembahasan Mendalam
           (Sub-topik pembahasan dengan kutipan argumen masing-masing peserta)
           ## 3. Pertanyaan, Jawaban & Klarifikasi
           (Rincian setiap pertanyaan peserta dan klarifikasi yang diberikan)
           ## 4. Diskusi Khusus & Konteks Tambahan
           (Catatan seputar kendala teknis, masukan informal, atau saran yang dibicarakan)
           ## 5. Kesimpulan & Tindak Lanjut
           (Kesepakatan nyata yang diambil pada akhir sesi)
        
        Transkrip Verbatim:
        $verbatimTranscript
    """.trimIndent()
}
