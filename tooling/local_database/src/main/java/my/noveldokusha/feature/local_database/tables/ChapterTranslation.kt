package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores translated text for chapter paragraphs to avoid re-translating
 * on every reader session.
 */
@Entity(
    indices = [
        Index(value = ["chapterUrl", "sourceLang", "targetLang"])
    ]
)
data class ChapterTranslation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterUrl: String,
    val sourceLang: String,
    val targetLang: String,
    val originalText: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
