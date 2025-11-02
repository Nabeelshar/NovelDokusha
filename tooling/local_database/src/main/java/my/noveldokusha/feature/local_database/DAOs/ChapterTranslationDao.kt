package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import my.noveldokusha.feature.local_database.tables.ChapterTranslation

@Dao
interface ChapterTranslationDao {
    
    /**
     * Get all translations for a specific chapter and language pair
     */
    @Query("""
        SELECT * FROM ChapterTranslation 
        WHERE chapterUrl = :chapterUrl 
        AND sourceLang = :sourceLang 
        AND targetLang = :targetLang
    """)
    suspend fun getTranslations(
        chapterUrl: String,
        sourceLang: String,
        targetLang: String
    ): List<ChapterTranslation>
    
    /**
     * Insert or replace a batch of translations
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(translations: List<ChapterTranslation>)
    
    /**
     * Delete translations for chapters that no longer exist
     */
    @Query("""
        DELETE FROM ChapterTranslation 
        WHERE chapterUrl NOT IN (SELECT url FROM Chapter)
    """)
    suspend fun removeOrphanedTranslations()
    
    /**
     * Delete all translations for a specific chapter
     */
    @Query("DELETE FROM ChapterTranslation WHERE chapterUrl = :chapterUrl")
    suspend fun deleteChapterTranslations(chapterUrl: String)
    
    /**
     * Get count of translations for a chapter
     */
    @Query("""
        SELECT COUNT(*) FROM ChapterTranslation 
        WHERE chapterUrl = :chapterUrl 
        AND sourceLang = :sourceLang 
        AND targetLang = :targetLang
    """)
    suspend fun getTranslationCount(
        chapterUrl: String,
        sourceLang: String,
        targetLang: String
    ): Int
}
