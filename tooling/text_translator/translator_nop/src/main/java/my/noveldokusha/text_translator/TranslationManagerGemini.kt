package my.noveldokusha.text_translator

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Translation manager using Google Gemini API
 * Requires API key configuration
 * FOSS version - API-only, no MLKit
 */
class TranslationManagerGemini(
    private val coroutineScope: AppCoroutineScope,
    private val apiKey: String
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val apiEndpoint by lazy {
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$apiKey"
    }

    override val available = true  // Always show settings UI, even without API key
    override val isUsingOnlineTranslation = apiKey.isNotBlank()

    // Gemini supports many languages without needing model downloads
    override val models = mutableStateListOf<TranslationModelState>().apply {
        // Common languages supported by Gemini
        val supportedLanguages = listOf(
            "en", "zh", "ja", "ko", "es", "fr", "de", "it", "pt", "ru",
            "ar", "hi", "th", "vi", "id", "tr", "pl", "nl", "sv", "da",
            "fi", "no", "cs", "el", "he", "ro", "hu", "uk", "bg", "hr"
        )
        
        addAll(supportedLanguages.map { lang ->
            TranslationModelState(
                language = lang,
                available = true, // Always available via API
                downloading = false,
                downloadingFailed = false
            )
        })
    }

    override suspend fun hasModelDownloaded(language: String): TranslationModelState? {
        // For API-based translation, all models are "available" if API key is valid
        return models.firstOrNull { it.language == language }
    }

    override fun getTranslator(source: String, target: String): TranslatorState {
        return TranslatorState(
            source = source,
            target = target,
            translate = { input -> translateWithGemini(input, source, target) }
        )
    }

    private suspend fun translateWithGemini(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key not configured")
        }

        val sourceLocale = Locale(sourceLanguage)
        val targetLocale = Locale(targetLanguage)
        val sourceLangName = sourceLocale.displayLanguage
        val targetLangName = targetLocale.displayLanguage

        val prompt = """
            Translate the following text from $sourceLangName to $targetLangName.
            Only provide the translation, without any explanations or additional text.
            
            Text to translate:
            $text
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val mediaType = "application/json".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(apiEndpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        
        val code = response.code
        if (code !in 200..299) {
            val message = response.message
            throw Exception("Translation failed: $code - $message")
        }

        val responseBody = response.body?.string() ?: ""

        try {
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val content = candidates.getJSONObject(0)
                    .getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return@withContext parts.getJSONObject(0)
                        .getString("text")
                        .trim()
                }
            }
            throw Exception("Invalid response format from Gemini API")
        } catch (e: Exception) {
            throw Exception("Failed to parse Gemini response: ${e.message}")
        }
    }

    override fun downloadModel(language: String) {
        // No-op for API-based translation - models are always available
    }

    override fun removeModel(language: String) {
        // No-op for API-based translation - models can't be removed
    }
}
