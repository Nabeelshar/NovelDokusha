package my.noveldokusha.scraper

import my.noveldokusha.data.BookMetadata
import my.noveldokusha.data.ChapterMetadata
import my.noveldokusha.data.Response
import my.noveldokusha.network.PagedList
import org.jsoup.nodes.Document

sealed interface SourceInterface {
    val id: String
    val name: String
    val baseUrl: String

    // Transform current url to preferred url
    suspend fun transformChapterUrl(url: String): String = url

    suspend fun getChapterTitle(doc: Document): String? = null
    suspend fun getChapterText(doc: Document): String? = null

    interface Base : SourceInterface
    interface RemoteCatalog : SourceInterface {
        val catalogUrl: String
        val language: String
        val iconUrl: String get() = "$baseUrl/favicon.ico"

        suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = Response.Success(null)
        suspend fun getBookDescription(bookUrl: String): Response<String?> = Response.Success(null)

        /**
         * Chapters list ordered from first one (oldest) to newest one.
         */
        suspend fun getChapterList(bookUrl: String): Response<List<ChapterMetadata>>
        suspend fun getCatalogList(index: Int): Response<PagedList<BookMetadata>>
        suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookMetadata>>
    }
}