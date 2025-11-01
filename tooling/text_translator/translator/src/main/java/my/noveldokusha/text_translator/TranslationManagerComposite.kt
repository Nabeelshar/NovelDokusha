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

/**
 * Composite translation manager that can use multiple translation providers
 * Falls back to offline MLKit if online Gemini fails
 */
class TranslationManagerComposite(
    private val coroutineScope: AppCoroutineScope,
    private val mlkitManager: TranslationManagerMLKit,
    private val geminiManager: TranslationManagerGemini?,
    private val appPreferences: AppPreferences
) : TranslationManager {

    override val available: Boolean
        get() = mlkitManager.available || geminiManager?.available == true
    
    override val isUsingOnlineTranslation: Boolean
        get() = appPreferences.TRANSLATION_PREFER_ONLINE.value && 
                geminiManager != null && 
                geminiManager.available  // Check if API key is configured

    override val models = mutableStateListOf<TranslationModelState>()

    init {
        // Merge models from both providers
        val allLanguages = mutableSetOf<String>()
        allLanguages.addAll(mlkitManager.models.map { it.language })
        geminiManager?.models?.let { allLanguages.addAll(it.map { it.language }) }

        models.addAll(allLanguages.map { lang ->
            val mlkitModel = mlkitManager.models.firstOrNull { it.language == lang }
            val geminiModel = geminiManager?.models?.firstOrNull { it.language == lang }

            TranslationModelState(
                language = lang,
                available = mlkitModel?.available == true || geminiModel?.available == true,
                downloading = mlkitModel?.downloading == true,
                downloadingFailed = mlkitModel?.downloadingFailed == true
            )
        })
    }

    override suspend fun hasModelDownloaded(language: String): TranslationModelState? {
        // Check both providers
        val mlkitModel = mlkitManager.hasModelDownloaded(language)
        val geminiModel = geminiManager?.hasModelDownloaded(language)

        return when {
            mlkitModel != null -> mlkitModel
            geminiModel != null -> geminiModel
            else -> null
        }
    }

    override fun getTranslator(source: String, target: String): TranslatorState {
        Log.d(TAG, "getTranslator: source=$source, target=$target")
        Log.d(TAG, "  preferOnline=${appPreferences.TRANSLATION_PREFER_ONLINE.value}")
        Log.d(TAG, "  geminiAvailable=${geminiManager?.available == true}")
        
        val mlkitTranslator = mlkitManager.getTranslator(source, target)
        val geminiTranslator = if (geminiManager?.available == true) {
            geminiManager.getTranslator(source, target)
        } else {
            null
        }

        return when {
            // Prefer online if configured and available - read preference dynamically
            appPreferences.TRANSLATION_PREFER_ONLINE.value && geminiTranslator != null -> {
                Log.d(TAG, "getTranslator: using Gemini (online) with MLKit fallback")
                TranslatorState(
                    source = source,
                    target = target,
                    translate = { input ->
                        var lastException: Exception? = null
                        // Retry up to 3 times for Gemini
                        repeat(3) { attempt ->
                            try {
                                Log.d(TAG, "Gemini attempt ${attempt + 1}/3")
                                val result = geminiTranslator.translate(input)
                                Log.d(TAG, "Gemini translation succeeded")
                                return@TranslatorState result
                            } catch (e: Exception) {
                                Log.e(TAG, "Gemini attempt ${attempt + 1} failed: ${e.message}", e)
                                lastException = e
                                if (attempt < 2) {
                                    // Wait before retry (exponential backoff)
                                    val delay = 1000L * (attempt + 1)
                                    Log.d(TAG, "Retrying in ${delay}ms")
                                    kotlinx.coroutines.delay(delay)
                                }
                            }
                        }
                        // All retries failed, fallback to MLKit
                        Log.w(TAG, "Gemini failed, falling back to MLKit")
                        try {
                            val result = mlkitTranslator.translate(input)
                            Log.d(TAG, "MLKit fallback succeeded")
                            result
                        } catch (e: Exception) {
                            Log.e(TAG, "MLKit fallback also failed: ${e.message}", e)
                            throw lastException ?: e
                        }
                    }
                )
            }
            // Use MLKit if available
            else -> {
                Log.d(TAG, "getTranslator: using MLKit (offline)")
                mlkitTranslator
            }
        }
    }

    override fun downloadModel(language: String) {
        // Only download for MLKit (offline models)
        mlkitManager.downloadModel(language)
    }

    override fun removeModel(language: String) {
        // Only remove for MLKit (offline models)
        mlkitManager.removeModel(language)
    }

    companion object {
        private const val TAG = "TranslationComposite"
    }
}
