package my.noveldokusha.scraper.templates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.domain.ChapterResult
import okhttp3.FormBody

/**
 * Template for WordPress Madara theme-based sites.
 * 
 * Common pattern:
 * - WordPress with Madara manga/novel theme
 * - Book page: /novel-name/
 * - Chapters loaded via AJAX POST to /ajax/chapters/
 * - Search: /?s=query&post_type=wp-manga
 * - Uses data attributes for lazy loading images (data-src)
 * 
 * Used by:
 * - BoxNovel.com
 * - NovelMultiverse.com
 * - WuxiaWorld.site
 * - And 8+ other sites from lightnovel-crawler
 */
abstract class BaseMadaraScraper(
    networkClient: NetworkClient
) : BaseScraperTemplate(networkClient) {

    // Default selectors for Madara-style sites
    override val selectBookCover: String = ".summary_image img[data-src]"
    override val selectBookDescription: String = ".summary__content.show-more"
    override val selectChapterList: String = ".wp-manga-chapter a[href]"
    override val selectChapterContent: String = ".reading-content"
    override val selectCatalogItems: String = ".page-item-detail"
    override val selectCatalogItemTitle: String = "a[href]"
    override val selectCatalogItemUrl: String = "a[href]"
    override val selectCatalogItemCover: String = "img[data-src]"
    override val selectPaginationLastPage: String = "div.nav-previous.float-left"

    // Search selectors (Madara uses different structure for search results)
    override val selectSearchItems: String = ".c-tabs-item__content"
    override val selectSearchItemTitle: String = ".post-title h3 a"
    override val selectSearchItemUrl: String = ".post-title h3 a"
    override val selectSearchItemCover: String = "img[data-src]"

    // Madara-specific settings
    protected open val catalogPath: String = "novel"
    protected open val catalogOrderBy: String = "alphabet"
    protected open val useAjaxChapterLoading: Boolean = true
    protected open val reverseChapterOrder: Boolean = true

    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return baseUrl.toUrlBuilderSafe()
            .addPath(catalogPath)
            .apply {
                if (page > 1) addPath("page", page.toString())
                add("m_orderby", catalogOrderBy)
            }.toString()
    }

    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return baseUrl.toUrlBuilderSafe()
            .apply {
                if (page > 1) addPath("page", page.toString())
                add("s", input)
                add("post_type", "wp-manga")
                add("op", "")
                add("author", "")
                add("artist", "")
                add("release", "")
                add("adult", "")
            }.toString()
    }

    override suspend fun fetchChapterList(bookUrl: String): List<ChapterResult> {
        return if (useAjaxChapterLoading) {
            fetchChapterListViaAjax(bookUrl)
        } else {
            super.fetchChapterList(bookUrl)
        }
    }

    protected open suspend fun fetchChapterListViaAjax(bookUrl: String): List<ChapterResult> = 
        withContext(Dispatchers.Default) {
            // Try primary ajax method
            val cleanUrl = bookUrl.split("?")[0].trimEnd('/')
            val ajaxUrl = "$cleanUrl/ajax/chapters/"
            
            try {
                val chapters = networkClient.call(postRequest(ajaxUrl))
                    .toDocument()
                    .select(selectChapterList)
                    .map { element ->
                        ChapterResult(
                            title = element.text(),
                            url = chapterUrlTransform(element.attr("href"))
                        )
                    }
                
                if (reverseChapterOrder) chapters.reversed() else chapters
            } catch (e: Exception) {
                // Try alternate method with manga-chapters-holder
                try {
                    fetchChapterListAlternate(bookUrl)
                } catch (e2: Exception) {
                    super.fetchChapterList(bookUrl)
                }
            }
        }

    protected open suspend fun fetchChapterListAlternate(bookUrl: String): List<ChapterResult> = 
        withContext(Dispatchers.Default) {
            val doc = networkClient.get(bookUrl).toDocument()
            val mangaId = doc.selectFirst("#manga-chapters-holder[data-id]")?.attr("data-id")
                ?: throw Exception("Manga ID not found for alternate method")
            
            val ajaxUrl = baseUrl.toUrlBuilderSafe()
                .addPath("wp-admin", "admin-ajax.php")
                .toString()
            
            val formBody = FormBody.Builder()
                .add("action", "manga_get_chapters")
                .add("manga", mangaId)
                .build()
            
            val chapters = networkClient.call(postRequest(ajaxUrl, body = formBody)).toDocument()
                .select(selectChapterList)
                .map { element ->
                    ChapterResult(
                        title = element.text(),
                        url = chapterUrlTransform(element.attr("href"))
                    )
                }
            
            if (reverseChapterOrder) chapters.reversed() else chapters
        }

    override fun isLastPage(doc: org.jsoup.nodes.Document): Boolean {
        // Madara uses nav-previous to indicate more pages exist
        // If nav-previous is missing, we're on the last page
        return when (val nav = doc.selectFirst(selectPaginationLastPage)) {
            null -> true
            else -> false
        }
    }
}
