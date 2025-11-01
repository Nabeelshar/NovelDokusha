plugins {
    alias(libs.plugins.noveldokusha.android.library)
    alias(libs.plugins.noveldokusha.android.compose)
}

android {
    namespace = "my.noveldokusha.tooling.text_translator"
}

dependencies {
    implementation(projects.core)
    implementation(projects.networking)
    implementation(projects.tooling.textTranslator.domain)
    
    // OkHttp for Gemini API calls
    implementation(libs.okhttp)
}