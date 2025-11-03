package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
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
    private val appPreferences: AppPreferences
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Read API key dynamically from preferences
    private val apiKey: String
        get() = appPreferences.TRANSLATION_GEMINI_API_KEY.value

    private fun getApiEndpoint(key: String): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=$key"
    }

    override val available = true  // Always show settings UI, even without API key
    override val isUsingOnlineTranslation: Boolean
        get() = apiKey.isNotBlank()

    // Cache for batch translations to avoid re-translating same chapter
    private val translationCache = mutableMapOf<String, String>()
    
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
        Log.d(TAG, "getTranslator: source=$source, target=$target, apiKeyLength=${apiKey.length}")
        return TranslatorState(
            source = source,
            target = target,
            translate = { input -> translateWithGemini(input, source, target) }
        )
    }

    private suspend fun translateWithGemini(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        retryCount: Int = 3
    ): String = withContext(Dispatchers.IO) {
        // Check cache first
        val cacheKey = "$sourceLanguage-$targetLanguage:$text"
        translationCache[cacheKey]?.let {
            Log.d(TAG, "translateWithGemini: using cached translation")
            return@withContext it
        }
        
        // Read API key fresh each time
        val currentApiKey = apiKey
        
        Log.d(TAG, "translateWithGemini: starting translation")
        Log.d(TAG, "  source=$sourceLanguage, target=$targetLanguage")
        Log.d(TAG, "  textLength=${text.length}, apiKeyConfigured=${currentApiKey.isNotBlank()}")
        
        if (currentApiKey.isBlank()) {
            Log.e(TAG, "translateWithGemini: API key is blank!")
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

        var lastException: Exception? = null
        
        repeat(retryCount) { attempt ->
            try {
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
                    .url(getApiEndpoint(currentApiKey))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", currentApiKey)
                    .post(requestBody)
                    .build()

                Log.d(TAG, "translateWithGemini: sending API request (attempt ${attempt + 1}/$retryCount)")
                val response = client.newCall(request).execute()
                
                val code = response.code
                Log.d(TAG, "translateWithGemini: received response code=$code")
                
                when (code) {
                    429 -> {
                        // Rate limit - wait with exponential backoff
                        val waitTime = (1000L * (attempt + 1) * (attempt + 1)) // 1s, 4s, 9s
                        Log.w(TAG, "translateWithGemini: Rate limit (429), waiting ${waitTime}ms before retry")
                        if (attempt < retryCount - 1) {
                            kotlinx.coroutines.delay(waitTime)
                            return@repeat // Try again
                        } else {
                            // Last attempt failed
                            return@withContext "[Translation rate limit exceeded. Please wait a moment and try again.]"
                        }
                    }
                    in 500..599 -> {
                        // Server error - retry
                        val waitTime = 2000L * (attempt + 1)
                        Log.w(TAG, "translateWithGemini: Server error ($code), waiting ${waitTime}ms before retry")
                        if (attempt < retryCount - 1) {
                            kotlinx.coroutines.delay(waitTime)
                            return@repeat
                        } else {
                            return@withContext "[Translation service temporarily unavailable ($code)]"
                        }
                    }
                    !in 200..299 -> {
                        val message = response.message
                        Log.e(TAG, "translateWithGemini: API error $code - $message")
                        return@withContext "[Translation failed: $code]"
                    }
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "translateWithGemini: response body length=${responseBody.length}")

                try {
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0)
                            .getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            val translatedText = parts.getJSONObject(0)
                                .getString("text")
                                .trim()
                            Log.d(TAG, "translateWithGemini: success, result length=${translatedText.length}")
                            // Cache the result
                            translationCache[cacheKey] = translatedText
                            return@withContext translatedText
                        }
                    }
                    Log.e(TAG, "translateWithGemini: Invalid response format")
                    return@withContext "[Translation failed: invalid response]"
                } catch (e: Exception) {
                    Log.e(TAG, "translateWithGemini: parse error - ${e.message}", e)
                    lastException = e
                    if (attempt < retryCount - 1) {
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                        return@repeat
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "translateWithGemini: error on attempt ${attempt + 1} - ${e.message}", e)
                lastException = e
                if (attempt < retryCount - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                } else {
                    return@withContext "[Translation error: ${e.message?.take(50) ?: "unknown"}]"
                }
            }
        }
        
        return@withContext "[Translation failed after $retryCount attempts]"
    }

    /**
     * Translate multiple paragraphs at once for efficiency
     * Returns map of original text to translated text
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()
        
        Log.d(TAG, "translateBatch: translating ${texts.size} paragraphs at once")
        
        // Read API key
        val currentApiKey = apiKey
        if (currentApiKey.isBlank()) {
            Log.e(TAG, "translateBatch: API key is blank!")
            return@withContext texts.associateWith { "[API key not configured]" }
        }

        val sourceLocale = Locale(sourceLanguage)
        val targetLocale = Locale(targetLanguage)
        val sourceLangName = sourceLocale.displayLanguage
        val targetLangName = targetLocale.displayLanguage

        // Create numbered list for translation
        val numberedTexts = texts.mapIndexed { index, text ->
            "${index + 1}. $text"
        }.joinToString("\n\n")

        val prompt = """
            Translate the following numbered paragraphs from $sourceLangName to $targetLangName.
            Maintain the same numbering format (1., 2., 3., etc.) in your response.
            Only provide the translations, without any explanations or additional text.
            
            Paragraphs to translate:
            $numberedTexts
        """.trimIndent()

        var lastException: Exception? = null
        val retryCount = 3
        
        repeat(retryCount) { attempt ->
            try {
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
                    .url(getApiEndpoint(currentApiKey))
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", currentApiKey)
                    .post(requestBody)
                    .build()

                Log.d(TAG, "translateBatch: sending API request (attempt ${attempt + 1}/$retryCount)")
                val response = client.newCall(request).execute()
                
                val code = response.code
                Log.d(TAG, "translateBatch: received response code=$code")
                
                when (code) {
                    429 -> {
                        val waitTime = (1000L * (attempt + 1) * (attempt + 1))
                        Log.w(TAG, "translateBatch: Rate limit (429), waiting ${waitTime}ms")
                        if (attempt < retryCount - 1) {
                            kotlinx.coroutines.delay(waitTime)
                            return@repeat
                        } else {
                            return@withContext texts.associateWith { "[Rate limit exceeded]" }
                        }
                    }
                    in 500..599 -> {
                        val waitTime = 2000L * (attempt + 1)
                        Log.w(TAG, "translateBatch: Server error ($code), waiting ${waitTime}ms")
                        if (attempt < retryCount - 1) {
                            kotlinx.coroutines.delay(waitTime)
                            return@repeat
                        } else {
                            return@withContext texts.associateWith { "[Service unavailable]" }
                        }
                    }
                    !in 200..299 -> {
                        Log.e(TAG, "translateBatch: API error $code")
                        return@withContext texts.associateWith { "[Translation failed: $code]" }
                    }
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "translateBatch: response length=${responseBody.length}")

                try {
                    val jsonResponse = JSONObject(responseBody)
                    val candidates = jsonResponse.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        if (parts.length() > 0) {
                            val translatedText = parts.getJSONObject(0).getString("text").trim()
                            Log.d(TAG, "translateBatch: success, parsing ${texts.size} translations")
                            
                            // Parse numbered translations back into map
                            val translations = mutableMapOf<String, String>()
                            val lines = translatedText.split("\n").filter { it.isNotBlank() }
                            var currentIndex = 0
                            var currentTranslation = StringBuilder()
                            
                            for (line in lines) {
                                val numberMatch = Regex("^(\\d+)\\.\\s*").find(line)
                                if (numberMatch != null) {
                                    // New numbered paragraph
                                    if (currentTranslation.isNotEmpty() && currentIndex > 0) {
                                        val originalText = texts.getOrNull(currentIndex - 1)
                                        if (originalText != null) {
                                            translations[originalText] = currentTranslation.toString().trim()
                                        }
                                        currentTranslation.clear()
                                    }
                                    currentIndex = numberMatch.groupValues[1].toIntOrNull() ?: (currentIndex + 1)
                                    currentTranslation.append(line.substring(numberMatch.range.last + 1))
                                } else {
                                    // Continuation of current paragraph
                                    if (currentTranslation.isNotEmpty()) {
                                        currentTranslation.append(" ")
                                    }
                                    currentTranslation.append(line.trim())
                                }
                            }
                            
                            // Add last translation
                            if (currentTranslation.isNotEmpty() && currentIndex > 0) {
                                val originalText = texts.getOrNull(currentIndex - 1)
                                if (originalText != null) {
                                    translations[originalText] = currentTranslation.toString().trim()
                                }
                            }
                            
                            // Fill in any missing translations with originals
                            texts.forEach { text ->
                                if (!translations.containsKey(text)) {
                                    translations[text] = text
                                }
                            }
                            
                            Log.d(TAG, "translateBatch: parsed ${translations.size} translations")
                            return@withContext translations
                        }
                    }
                    Log.e(TAG, "translateBatch: Invalid response format")
                    return@withContext texts.associateWith { "[Invalid response]" }
                } catch (e: Exception) {
                    Log.e(TAG, "translateBatch: parse error - ${e.message}", e)
                    lastException = e
                    if (attempt < retryCount - 1) {
                        kotlinx.coroutines.delay(1000L * (attempt + 1))
                        return@repeat
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "translateBatch: error on attempt ${attempt + 1} - ${e.message}", e)
                lastException = e
                if (attempt < retryCount - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }
        
        Log.e(TAG, "translateBatch: failed after $retryCount attempts")
        return@withContext texts.associateWith { "[Translation failed]" }
    }

    override fun downloadModel(language: String) {
        // No-op for API-based translation - models are always available
    }

    override fun removeModel(language: String) {
        // No-op for API-based translation - models can't be removed
    }

    /**
     * Invalidate cached translation(s).
     * If text is null, clears all cached entries for the given source/target pair.
     * If text is provided, clears the exact cache entry for source-target:text
     */
    fun invalidateCacheFor(sourceLanguage: String, targetLanguage: String, text: String? = null) {
        Log.d(TAG, "invalidateCacheFor: source=$sourceLanguage, target=$targetLanguage, text=${if (text == null) "ALL" else "specific"}")
        if (text == null) {
            val prefix = "$sourceLanguage-$targetLanguage:"
            val keysToRemove = translationCache.keys.filter { it.startsWith(prefix) }
            Log.d(TAG, "invalidateCacheFor: clearing ${keysToRemove.size} cached entries")
            keysToRemove.forEach { translationCache.remove(it) }
        } else {
            val key = "$sourceLanguage-$targetLanguage:$text"
            val removed = translationCache.remove(key)
            Log.d(TAG, "invalidateCacheFor: ${if (removed != null) "cleared" else "no entry found for"} specific key")
        }
    }

    companion object {
        private const val TAG = "TranslationGemini"
    }
}
