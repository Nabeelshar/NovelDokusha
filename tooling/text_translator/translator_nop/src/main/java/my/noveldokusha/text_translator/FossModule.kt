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
object FossModule {

    @Provides
    @Singleton
    fun provideTranslationManager(
        appCoroutineScope: AppCoroutineScope,
        appPreferences: AppPreferences
    ): TranslationManager {
        // Always provide Gemini manager for FOSS (shows settings even without API key)
        val apiKey = appPreferences.TRANSLATION_GEMINI_API_KEY.value
        return TranslationManagerGemini(appCoroutineScope, apiKey)
    }
}