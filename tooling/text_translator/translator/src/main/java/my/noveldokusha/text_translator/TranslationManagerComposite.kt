package my.noveldokusha.text_translator

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
    
    val isUsingGemini: Boolean
        get() = false // Disabled temporarily: appPreferences.TRANSLATION_PREFER_ONLINE.value && geminiManager != null

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
        val mlkitTranslator = mlkitManager.getTranslator(source, target)
        val geminiTranslator = geminiManager?.getTranslator(source, target)

        return when {
            // Prefer online if configured and available - read preference dynamically
            false && geminiTranslator != null -> { // Disabled temporarily: appPreferences.TRANSLATION_PREFER_ONLINE.value
                TranslatorState(
                    source = source,
                    target = target,
                    translate = { input ->
                        var lastException: Exception? = null
                        // Retry up to 3 times for Gemini
                        repeat(3) { attempt ->
                            try {
                                return@TranslatorState geminiTranslator.translate(input)
                            } catch (e: Exception) {
                                lastException = e
                                if (attempt < 2) {
                                    // Wait before retry (exponential backoff)
                                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                                }
                            }
                        }
                        // All retries failed, fallback to MLKit
                        try {
                            mlkitTranslator.translate(input)
                        } catch (e: Exception) {
                            throw lastException ?: e
                        }
                    }
                )
            }
            // Use MLKit if available
            else -> mlkitTranslator
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
}
