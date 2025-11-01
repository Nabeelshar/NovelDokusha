package my.noveldokusha.text_translator

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.text_translator.domain.TranslationManager
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object FullModule {

    @Provides
    @Singleton
    fun provideTranslationManager(
        coroutineScope: AppCoroutineScope,
        appPreferences: AppPreferences
    ): TranslationManager {
        val mlkitManager = TranslationManagerMLKit(coroutineScope)
        
        val geminiApiKey = appPreferences.TRANSLATION_GEMINI_API_KEY.value
        val geminiManager = if (geminiApiKey.isNotBlank()) {
            TranslationManagerGemini(coroutineScope, geminiApiKey)
        } else {
            null
        }
        
        return if (geminiManager != null) {
            TranslationManagerComposite(
                coroutineScope,
                mlkitManager,
                geminiManager,
                appPreferences
            )
        } else {
            mlkitManager
        }
    }
}