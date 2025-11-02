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
)

internal class ReaderLiveTranslation(
    private val translationManager: TranslationManager,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("LiveTranslator")
    )
) {
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
    
    /**
     * Get batch translator if available (Gemini only)
     * Returns null for MLKit which doesn't support batch translation
     */
    fun getBatchTranslator(): (suspend (List<String>) -> Map<String, String>)? {
        if (!translationManager.isUsingOnlineTranslation) return null
        if (translatorState == null) return null
        
        val source = translatorState?.source ?: return null
        val target = translatorState?.target ?: return null
        
        // Try to get Gemini manager and call translateBatch directly
        return try {
            val geminiClass = Class.forName("my.noveldokusha.text_translator.TranslationManagerGemini")
            if (!geminiClass.isInstance(translationManager)) {
                Log.d(TAG, "getBatchTranslator: translator is not Gemini")
                return null
            }
            
            Log.d(TAG, "getBatchTranslator: found Gemini translator, creating batch wrapper")
            
            // Create wrapper that calls the method directly
            // The manager is already the correct instance, we just call it as "any" and cast result
            val batchTranslator: suspend (List<String>) -> Map<String, String> = { texts ->
                @Suppress("UNCHECKED_CAST")
                withContext(Dispatchers.IO) {
                    // Use reflection to invoke translateBatch
                    val method = geminiClass.getDeclaredMethod(
                        "translateBatch",
                        List::class.java,
                        String::class.java,
                        String::class.java,
                        kotlin.coroutines.Continuation::class.java
                    )
                    method.isAccessible = true
                    
                    // Call with suspendCancellableCoroutine to get proper continuation
                    kotlinx.coroutines.suspendCancellableCoroutine<Map<String, String>> { continuation ->
                        try {
                            method.invoke(translationManager, texts, source, target, continuation)
                        } catch (e: Exception) {
                            continuation.cancel(e)
                        }
                    }
                }
            }
            batchTranslator
        } catch (e: Exception) {
            Log.e(TAG, "getBatchTranslator: error (${translationManager.javaClass.simpleName})", e)
            null
        }
    }

    companion object {
        private const val TAG = "ReaderLiveTranslation"
    }
}
