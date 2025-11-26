package my.noveldokusha.features.reader.features

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.feature.local_database.DAOs.ChapterTranslationDao
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.domain.TranslationModelState
import my.noveldokusha.text_translator.domain.TranslatorState

internal data class LiveTranslationSettingData(
    val isAvailable: Boolean,
    val enable: MutableState<Boolean>,
    val listOfAvailableModels: SnapshotStateList<TranslationModelState>,
    val source: MutableState<TranslationModelState?>,
    val target: MutableState<TranslationModelState?>,
    val onEnable: (Boolean) -> Unit,
    val onSourceChange: (TranslationModelState?) -> Unit,
    val onTargetChange: (TranslationModelState?) -> Unit,
    val onDownloadTranslationModel: (language: String) -> Unit,
    val onRedoTranslation: () -> Unit,
)

internal class ReaderLiveTranslation(
    private val translationManager: TranslationManager,
    private val appPreferences: AppPreferences,
    private val chapterTranslationDao: ChapterTranslationDao? = null,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("LiveTranslator")
    )
) {
    // Callback to clear chapter cache (set by ReaderSession)
    var onClearChapterCache: (() -> Unit)? = null

    val state = LiveTranslationSettingData(
        isAvailable = translationManager.available,
        listOfAvailableModels = translationManager.models,
        enable = mutableStateOf(appPreferences.GLOBAL_TRANSLATION_ENABLED.value),
        source = mutableStateOf(null),
        target = mutableStateOf(null),
        onEnable = ::onEnable,
        onSourceChange = ::onSourceChange,
        onTargetChange = ::onTargetChange,
        onDownloadTranslationModel = translationManager::downloadModel
        , onRedoTranslation = ::onRedoTranslation
    )

    var translatorState: TranslatorState? = null
        private set

    private val _onTranslatorChanged = MutableSharedFlow<Unit>()
    val onTranslatorChanged = _onTranslatorChanged.asSharedFlow()

    suspend fun init() {
        Log.d(TAG, "init: starting")
        val source = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
        val target = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
        Log.d(TAG, "init: source=$source, target=$target")
        Log.d(TAG, "init: translationAvailable=${translationManager.available}")

        state.source.value = getValidTranslatorOrNull(source)
        state.target.value = getValidTranslatorOrNull(target)
        Log.d(TAG, "init: sourceModel=${state.source.value?.language}, targetModel=${state.target.value?.language}")

        updateTranslatorState()
        Log.d(TAG, "init: complete, translatorState=${translatorState != null}")
    }

    private suspend fun getValidTranslatorOrNull(language: String): TranslationModelState? {
        if (language.isBlank()) return null
        return translationManager.hasModelDownloaded(language)
    }

    /**
     * @return true if reader session needs to be updated
     */
    private fun updateTranslatorState(): Boolean {
        val isEnabled = state.enable.value
        val source = state.source.value
        val target = state.target.value

        Log.d(TAG, "updateTranslatorState: enabled=$isEnabled, source=${source?.language}, target=${target?.language}")

        val old = translatorState
        val new = when {
            !isEnabled -> {
                Log.d(TAG, "updateTranslatorState: translation disabled")
                null
            }
            source == null || target == null -> {
                Log.d(TAG, "updateTranslatorState: missing source or target model")
                null
            }
            source.language == target.language -> {
                Log.d(TAG, "updateTranslatorState: source and target are the same")
                null
            }
            else -> {
                try {
                    Log.d(TAG, "updateTranslatorState: creating translator")
                    translationManager.getTranslator(
                        source = source.language,
                        target = target.language
                    ).also {
                        Log.d(TAG, "updateTranslatorState: translator created successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "updateTranslatorState: failed to create translator", e)
                    throw e
                }
            }
        }.also { this.translatorState = it }


        return when {
            old == null && new == null -> false
            old != null && new != null -> when {
                old.source != new.source && old.target != new.target -> true
                else -> false
            }
            old == null && new != null -> new.source != new.target
            old != null && new == null -> old.source != old.target
            else -> true
        }
    }

    private fun onEnable(it: Boolean) {
        Log.d(TAG, "onEnable: $it")
        try {
            state.enable.value = it
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value = it
            val update = updateTranslatorState()
            Log.d(TAG, "onEnable: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onEnable: error", e)
            throw e
        }
    }

    private fun onSourceChange(it: TranslationModelState?) {
        Log.d(TAG, "onSourceChange: ${it?.language}")
        try {
            state.source.value = it
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value = it?.language ?: ""
            val update = updateTranslatorState()
            Log.d(TAG, "onSourceChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onSourceChange: error", e)
            throw e
        }
    }

    private fun onTargetChange(it: TranslationModelState?) {
        Log.d(TAG, "onTargetChange: ${it?.language}")
        try {
            state.target.value = it
            appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value = it?.language ?: ""
            val update = updateTranslatorState()
            Log.d(TAG, "onTargetChange: updateRequired=$update")
            if (update) scope.launch {
                _onTranslatorChanged.emit(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "onTargetChange: error", e)
            throw e
        }
    }

    fun isUsingGemini(): Boolean {
        return translationManager.isUsingOnlineTranslation
    }

    private fun onRedoTranslation() {
        scope.launch {
            try {
                Log.d(TAG, "onRedoTranslation: starting")
                val source = state.source.value?.language ?: run {
                    Log.w(TAG, "onRedoTranslation: source is null")
                    return@launch
                }
                val target = state.target.value?.language ?: run {
                    Log.w(TAG, "onRedoTranslation: target is null")
                    return@launch
                }

                Log.d(TAG, "onRedoTranslation: invalidating cache for source=$source, target=$target")

                // 1. Clear in-memory cache using reflection (for Gemini)
                val methods = translationManager.javaClass.methods
                val method = methods.find { it.name == "invalidateCacheFor" }

                if (method != null) {
                    try {
                        Log.d(TAG, "onRedoTranslation: found invalidateCacheFor method, invoking...")
                        // Call with (String, String, String?) - third param null clears all for this pair
                        method.invoke(translationManager, source, target, null)
                        Log.d(TAG, "onRedoTranslation: cache invalidation successful")
                    } catch (e: Exception) {
                        Log.e(TAG, "onRedoTranslation: reflection call failed", e)
                    }
                } else {
                    Log.w(TAG, "onRedoTranslation: invalidateCacheFor method not found on ${translationManager.javaClass.simpleName}")
                }

                // 2. Clear ALL database cached translations (not just current language pair)
                // This ensures translations from previous sessions are also cleared
                chapterTranslationDao?.let { dao ->
                    try {
                        Log.d(TAG, "onRedoTranslation: clearing ALL database cached translations")
                        val deletedCount = withContext(Dispatchers.IO) {
                            dao.deleteAllTranslations()
                        }
                        Log.d(TAG, "onRedoTranslation: database cache cleared successfully (deleted $deletedCount translations)")
                    } catch (e: Exception) {
                        Log.e(TAG, "onRedoTranslation: failed to clear database cache", e)
                    }
                } ?: Log.w(TAG, "onRedoTranslation: chapterTranslationDao is null, skipping database clear")

                // 3. Clear ReaderChaptersLoader in-memory pre-translation cache
                onClearChapterCache?.invoke()
                    ?.also { Log.d(TAG, "onRedoTranslation: chapter cache cleared") }
                    ?: Log.w(TAG, "onRedoTranslation: no chapter cache clear callback")

                // 4. Force recreate translator state to trigger full reload
                // This mimics what happens when user changes source/target language
                Log.d(TAG, "onRedoTranslation: forcing translator state update")
                translatorState = null
                val update = updateTranslatorState()
                Log.d(TAG, "onRedoTranslation: translator state updated, triggering reload")

                // Emit change event to trigger reader reload (same as language change)
                if (update) {
                    _onTranslatorChanged.emit(Unit)
                }
                Log.d(TAG, "onRedoTranslation: complete")
            } catch (e: Exception) {
                Log.e(TAG, "onRedoTranslation: error", e)
            }
        }
    }

    /**
     * Get batch translator if available (Gemini, Composite, or GoogleFree)
     * Returns null for MLKit which doesn't support batch translation
     */
    fun getBatchTranslator(): (suspend (List<String>) -> Map<String, String>)? {
        if (!translationManager.isUsingOnlineTranslation) return null
        val currentState = translatorState ?: return null
        val source = currentState.source
        val target = currentState.target

        return { texts ->
            translationManager.translateBatch(texts, source, target) ?: emptyMap()
        }
    }

    companion object {
        private const val TAG = "ReaderLiveTranslation"
    }
}
