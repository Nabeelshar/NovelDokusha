package my.noveldokusha.text_translator

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Translation manager using free Google Translate API
 * No API key required - uses unofficial endpoint
 * Limit: ~13-14k characters per request
 */
class TranslationManagerGoogleFree(
    private val coroutineScope: AppCoroutineScope
) : TranslationManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override val available = true
    override val isUsingOnlineTranslation = true

    // Cache for translations
    private val translationCache = mutableMapOf<String, String>()

    // Google Translate supports many languages
    override val models = mutableStateListOf<TranslationModelState>().apply {
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
        return models.firstOrNull { it.language == language }
    }

    override fun getTranslator(source: String, target: String): TranslatorState {
        Log.d(TAG, "getTranslator: source=$source, target=$target")
        return TranslatorState(
            source = source,
            target = target,
            translate = { input -> translateWithGoogleFree(input, source, target) }
        )
    }

    private suspend fun translateWithGoogleFree(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        retryCount: Int = 2
    ): String = withContext(Dispatchers.IO) {
        // Check cache first
        val cacheKey = "$sourceLanguage-$targetLanguage:$text"
        translationCache[cacheKey]?.let {
            Log.d(TAG, "translateWithGoogleFree: using cached translation")
            return@withContext it
        }

        Log.d(TAG, "translateWithGoogleFree: starting translation")
        Log.d(TAG, "  source=$sourceLanguage, target=$targetLanguage, textLength=${text.length}")

        // Split text if it exceeds the limit (13-14k chars)
        val maxChars = 13000
        if (text.length > maxChars) {
            Log.d(TAG, "translateWithGoogleFree: text too long (${text.length}), splitting...")
            return@withContext translateLongText(text, sourceLanguage, targetLanguage)
        }

        var lastException: Exception? = null
        repeat(retryCount) { attempt ->
            try {
                Log.d(TAG, "translateWithGoogleFree: attempt ${attempt + 1}/$retryCount")
                
                // Use POST for texts longer than 500 chars to avoid URL length limits
                val request = if (text.length > 500) {
                    val formBody = okhttp3.FormBody.Builder()
                        .add("client", "gtx")
                        .add("sl", sourceLanguage)
                        .add("tl", targetLanguage)
                        .add("dt", "t")
                        .add("q", text)
                        .build()
                    
                    Request.Builder()
                        .url("https://translate.googleapis.com/translate_a/single")
                        .post(formBody)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()
                } else {
                    val url = "https://translate.googleapis.com/translate_a/single?" +
                            "client=gtx&" +
                            "sl=$sourceLanguage&" +
                            "tl=$targetLanguage&" +
                            "dt=t&" +
                            "q=${java.net.URLEncoder.encode(text, "UTF-8")}"
                    
                    Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .build()
                }
                
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                
                Log.d(TAG, "translateWithGoogleFree: response code=${response.code}, bodyLength=${responseBody.length}")
                if (responseBody.length < 200) {
                    Log.d(TAG, "translateWithGoogleFree: short response body: $responseBody")
                }
                
                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    // Parse JSON response: [["translated", "original", null, null, 3], ...]
                    val jsonArray = org.json.JSONArray(responseBody)
                    val translations = StringBuilder()
                    
                    if (jsonArray.length() > 0) {
                        val firstArray = jsonArray.getJSONArray(0)
                        for (i in 0 until firstArray.length()) {
                            val item = firstArray.optJSONArray(i)
                            if (item != null && item.length() > 0) {
                                translations.append(item.getString(0))
                            }
                        }
                    }
                    
                    val result = translations.toString().trim()
                    if (result.isNotEmpty()) {
                        Log.d(TAG, "translateWithGoogleFree: success, result length=${result.length}")
                        translationCache[cacheKey] = result
                        return@withContext result
                    }
                }
                
                Log.w(TAG, "translateWithGoogleFree: empty or failed response (code=${response.code})")
            } catch (e: Exception) {
                Log.e(TAG, "translateWithGoogleFree: error on attempt ${attempt + 1} - ${e.message}", e)
                lastException = e
                if (attempt < retryCount - 1) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                }
            }
        }

        return@withContext "[Translation error: ${lastException?.message?.take(50) ?: "unknown"}]"
    }

    /**
     * Split text into two parts at sentence boundaries and translate each part
     */
    private suspend fun translateLongText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        Log.d(TAG, "translateLongText: splitting text (${text.length} chars)")

        val (firstPart, secondPart) = splitTextIntoTwoParts(text)
        Log.d(TAG, "translateLongText: part1=${firstPart.length} chars, part2=${secondPart.length} chars")

        if (firstPart.isEmpty() && secondPart.isEmpty()) {
            return ""
        }

        val translatedFirst = if (firstPart.isNotEmpty()) {
            translateWithGoogleFree(firstPart, sourceLanguage, targetLanguage)
        } else ""

        val translatedSecond = if (secondPart.isNotEmpty()) {
            translateWithGoogleFree(secondPart, sourceLanguage, targetLanguage)
        } else ""

        return if (translatedFirst.isNotEmpty() && translatedSecond.isNotEmpty()) {
            "$translatedFirst $translatedSecond"
        } else {
            translatedFirst + translatedSecond
        }
    }

    /**
     * Split text into two parts at sentence boundaries
     * Tries to split evenly while respecting sentence structure
     */
    private fun splitTextIntoTwoParts(text: String): Pair<String, String> {
        // Split text into sentences using basic regex
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) {
            return Pair("", "")
        }

        // Split point: first half gets slightly more if odd number
        val midIndex = (sentences.size + 1) / 2

        val firstPartSentences = sentences.take(midIndex)
        val secondPartSentences = sentences.drop(midIndex)

        val firstPartText = firstPartSentences.joinToString(" ")
        val secondPartText = secondPartSentences.joinToString(" ")

        return Pair(firstPartText, secondPartText)
    }

    /**
     * Translate all paragraphs as one large chunk for maximum context
     * Combines all text up to 12k characters in single request
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyMap()

        Log.d(TAG, "translateBatch: translating ${texts.size} texts as large chunk")

        val translations = mutableMapOf<String, String>()
        
        // Combine ALL texts into one large string with newline separators
        // Using double newline to preserve paragraph boundaries
        val combinedText = texts.joinToString("\n\n")
        val totalChars = combinedText.length
        
        Log.d(TAG, "translateBatch: combined ${texts.size} paragraphs into $totalChars characters")
        
        if (totalChars > 12000) {
            Log.w(TAG, "translateBatch: text too long ($totalChars chars), splitting into chunks")
            // Split into reasonable chunks
            val maxCharsPerChunk = 10000
            val chunks = mutableListOf<List<String>>()
            var currentChunk = mutableListOf<String>()
            var currentLength = 0
            
            texts.forEach { text ->
                if (currentLength + text.length + 1 > maxCharsPerChunk && currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toList())
                    currentChunk = mutableListOf()
                    currentLength = 0
                }
                currentChunk.add(text)
                currentLength += text.length + 1
            }
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
            }
            
            Log.d(TAG, "translateBatch: split into ${chunks.size} chunks")
            
            // Translate each chunk
            chunks.forEach { chunk ->
                val chunkText = chunk.joinToString("\n\n")
                try {
                    val translated = translateWithGoogleFree(chunkText, sourceLanguage, targetLanguage)
                    // Split translated text back into paragraphs
                    val translatedParagraphs = translated.split("\n\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    
                    // Map original texts to translated paragraphs
                    chunk.forEachIndexed { index, originalText ->
                        translations[originalText] = translatedParagraphs.getOrNull(index) 
                            ?: translated // Fallback to full text
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "translateBatch: chunk failed - ${e.message}")
                    chunk.forEach { originalText ->
                        translations[originalText] = originalText
                    }
                }
                kotlinx.coroutines.delay(500L)
            }
        } else {
            // Single large translation
            try {
                val translated = translateWithGoogleFree(combinedText, sourceLanguage, targetLanguage)
                Log.d(TAG, "translateBatch: translation successful, result length=${translated.length}")
                
                // Split translated text back into paragraphs using double newline
                val translatedParagraphs = translated.split("\n\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                Log.d(TAG, "translateBatch: split result into ${translatedParagraphs.size} paragraphs (expected ${texts.size})")
                
                // Map original texts to translated paragraphs
                // If counts don't match, fall back to best effort matching
                if (translatedParagraphs.size == texts.size) {
                    texts.forEachIndexed { index, originalText ->
                        translations[originalText] = translatedParagraphs[index]
                    }
                } else {
                    // Mismatch - try to map by position, use full translation as fallback
                    Log.w(TAG, "translateBatch: paragraph count mismatch, using fallback mapping")
                    texts.forEachIndexed { index, originalText ->
                        translations[originalText] = translatedParagraphs.getOrNull(index) 
                            ?: translated // Fallback to full text
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "translateBatch: failed - ${e.message}", e)
                // Fallback: keep originals
                texts.forEach { text ->
                    translations[text] = text
                }
            }
        }

        Log.d(TAG, "translateBatch: completed, ${translations.size}/${texts.size} entries")
        return@withContext translations
    }

    override fun downloadModel(language: String) {
        // No-op for online translation
    }

    override fun removeModel(language: String) {
        // No-op for online translation
    }

    /**
     * Invalidate cached translation(s)
     */
    fun invalidateCacheFor(sourceLanguage: String, targetLanguage: String, text: String? = null) {
        Log.d(TAG, "invalidateCacheFor: source=$sourceLanguage, target=$targetLanguage")
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
        private const val TAG = "TranslationGoogleFree"
    }
}
