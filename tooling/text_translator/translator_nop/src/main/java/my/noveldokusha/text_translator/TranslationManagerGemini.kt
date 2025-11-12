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

    // Read API keys dynamically from preferences (supports multiple keys separated by newlines)
    private val apiKeys: List<String>
        get() = appPreferences.TRANSLATION_GEMINI_API_KEY.value
            .split("\n", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun getApiEndpoint(key: String): String {
        return "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:streamGenerateContent?key=$key"
    }

    override val available = true  // Always show settings UI, even without API key
    override val isUsingOnlineTranslation: Boolean
        get() = apiKeys.isNotEmpty()

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
        Log.d(TAG, "getTranslator: source=$source, target=$target, apiKeysConfigured=${apiKeys.size}")
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
        
        val availableKeys = apiKeys
        
        Log.d(TAG, "translateWithGemini: starting translation")
        Log.d(TAG, "  source=$sourceLanguage, target=$targetLanguage")
        Log.d(TAG, "  textLength=${text.length}, apiKeysAvailable=${availableKeys.size}")
        
        if (availableKeys.isEmpty()) {
            Log.e(TAG, "translateWithGemini: No API keys configured!")
            throw IllegalStateException("Gemini API key not configured")
        }

        val sourceLocale = Locale(sourceLanguage)
        val targetLocale = Locale(targetLanguage)
        val sourceLangName = sourceLocale.displayLanguage
        val targetLangName = targetLocale.displayLanguage

        val prompt = """
            You are an expert Chinese webnovel translator specializing in cultivation/xianxia novels. Translate the following text from $sourceLangName to $targetLangName.
            
            CRITICAL TRANSLATION RULES:
            1. PRESERVE character names in pinyin (e.g., Chen Fei, Lin Xi, Zhang Wei, Wang Hao)
            
            2. TRANSLATE EVERYTHING ELSE to English equivalents:
               - Location names: Translate to English (e.g., Lingxi Peak → Spiritual Rhinoceros Peak, Qingmu → Azure Wood/Green Wood)
               - Cultivation terms: Use standard English (e.g., gongde → merit/karma, lingqi → spiritual energy, dantian → energy core)
               - Technique names: Translate descriptively (e.g., Heavenly Dragon Palm, Nine Yang Divine Art)
               - Titles and honorifics: Use English (e.g., Sect Master, Senior Brother, Junior Sister, Elder)
               - Sect/organization names: Translate to English (e.g., Azure Cloud Sect, Demon Palace)
               - Realm names: Use established translations (e.g., Qi Condensation, Foundation Establishment, Golden Core)
               - Artifact names: Translate descriptively (e.g., Heaven-Piercing Sword, Soul-Devouring Banner)
            
            3. QUALITY STANDARDS:
               - Produce natural, fluent $targetLangName that reads smoothly
               - Use standard Wuxiaworld/webnovel terminology for cultivation concepts
               - Preserve the original tone, style, and emotional impact
               - Remove any advertisements, author notes, or promotional content
               - Maintain consistency throughout the translation
            
            4. FORMAT:
               - Provide ONLY the translation
               - No explanations, notes, or additional commentary
               - Maintain paragraph structure
            
            Text to translate:
            $text
        """.trimIndent()

        var lastException: Exception? = null
        val totalAttempts = retryCount * availableKeys.size // Try each key multiple times
        
        repeat(totalAttempts) { attempt ->
            // Rotate through API keys on each attempt
            val currentApiKey = availableKeys[attempt % availableKeys.size]
            val attemptWithinKey = attempt / availableKeys.size + 1
            
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
                    put("generationConfig", JSONObject().apply {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
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

                Log.d(TAG, "translateWithGemini: sending request (attempt ${attempt + 1}/$totalAttempts, key ${(attempt % availableKeys.size) + 1}/${availableKeys.size})")
                val response = client.newCall(request).execute()
                
                val code = response.code
                Log.d(TAG, "translateWithGemini: received response code=$code")
                
                when (code) {
                    429 -> {
                        // Rate limit hit - rotate to next API key immediately
                        Log.w(TAG, "translateWithGemini: Rate limit (429) on key ${(attempt % availableKeys.size) + 1}, rotating to next key")
                        if (attempt < totalAttempts - 1) {
                            // Small delay before next key
                            kotlinx.coroutines.delay(500)
                            return@repeat // Try next key
                        } else {
                            // All keys exhausted
                            return@withContext "[Translation rate limit exceeded on all API keys. Please wait and try again.]"
                        }
                    }
                    in 500..599 -> {
                        // Server error - retry with backoff
                        val waitTime = 2000L * attemptWithinKey
                        Log.w(TAG, "translateWithGemini: Server error ($code), waiting ${waitTime}ms before retry")
                        if (attempt < totalAttempts - 1) {
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
                    if (attempt < totalAttempts - 1) {
                        kotlinx.coroutines.delay(1000L * attemptWithinKey)
                        return@repeat
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "translateWithGemini: error on attempt ${attempt + 1} - ${e.message}", e)
                lastException = e
                if (attempt < totalAttempts - 1) {
                    kotlinx.coroutines.delay(1000L * attemptWithinKey)
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
        
        // Validate API keys
        val availableKeys = apiKeys
        if (availableKeys.isEmpty()) {
            Log.e(TAG, "translateBatch: No API keys configured!")
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
            You are an expert Chinese webnovel translator specializing in cultivation/xianxia novels. Translate these numbered paragraphs from $sourceLangName to $targetLangName.
            
            CRITICAL TRANSLATION RULES:
            1. PRESERVE character names in pinyin (e.g., Chen Fei, Lin Xi, Zhang Wei, Wang Hao)
            
            2. TRANSLATE EVERYTHING ELSE to English equivalents:
               - Location names: Translate to English (e.g., Lingxi Peak → Spiritual Rhinoceros Peak, Qingmu → Azure Wood/Green Wood)
               - Cultivation terms: Use standard English (e.g., gongde → merit/karma, lingqi → spiritual energy, dantian → energy core)
               - Technique names: Translate descriptively (e.g., Heavenly Dragon Palm, Nine Yang Divine Art)
               - Titles and honorifics: Use English (e.g., Sect Master, Senior Brother, Junior Sister, Elder)
               - Sect/organization names: Translate to English (e.g., Azure Cloud Sect, Demon Palace)
               - Realm names: Use established translations (e.g., Qi Condensation, Foundation Establishment, Golden Core)
               - Artifact names: Translate descriptively (e.g., Heaven-Piercing Sword, Soul-Devouring Banner)
            
            3. QUALITY STANDARDS:
               - Produce natural, fluent $targetLangName that reads smoothly
               - Use standard Wuxiaworld/webnovel terminology for cultivation concepts
               - Maintain consistency throughout all paragraphs
               - Remove any advertisements or promotional content
            
            4. FORMAT REQUIREMENTS:
               - Maintain exact numbering format (1., 2., 3., etc.)
               - Provide ONLY translations, no explanations
               - Keep paragraph structure intact
            
            Paragraphs to translate:
            $numberedTexts
        """.trimIndent()

        var lastException: Exception? = null
        val retryCount = 3
        val totalAttempts = retryCount * availableKeys.size
        
        repeat(totalAttempts) { attempt ->
            val currentApiKey = availableKeys[attempt % availableKeys.size]
            val attemptWithinKey = attempt / availableKeys.size + 1
            
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
                    put("generationConfig", JSONObject().apply {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
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

                Log.d(TAG, "translateBatch: sending request (attempt ${attempt + 1}/$totalAttempts, key ${(attempt % availableKeys.size) + 1}/${availableKeys.size})")
                val response = client.newCall(request).execute()
                
                val code = response.code
                Log.d(TAG, "translateBatch: received response code=$code")
                
                when (code) {
                    429 -> {
                        Log.w(TAG, "translateBatch: Rate limit (429) on key ${(attempt % availableKeys.size) + 1}, rotating to next key")
                        if (attempt < totalAttempts - 1) {
                            kotlinx.coroutines.delay(500)
                            return@repeat
                        } else {
                            return@withContext texts.associateWith { "[Rate limit exceeded on all API keys]" }
                        }
                    }
                    in 500..599 -> {
                        val waitTime = 2000L * attemptWithinKey
                        Log.w(TAG, "translateBatch: Server error ($code), waiting ${waitTime}ms")
                        if (attempt < totalAttempts - 1) {
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
                    if (attempt < totalAttempts - 1) {
                        kotlinx.coroutines.delay(1000L * attemptWithinKey)
                        return@repeat
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "translateBatch: error on attempt ${attempt + 1} - ${e.message}", e)
                lastException = e
                if (attempt < totalAttempts - 1) {
                    kotlinx.coroutines.delay(1000L * attemptWithinKey)
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
