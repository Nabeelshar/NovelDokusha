package my.noveldokusha.scraper.templates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.addPath
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilder
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.domain.ChapterResult

/**
 * Template for NovelPub/LightNovelWorld-style sites.
 * 
 * Common pattern:
 * - Modern novel reader with paginated chapter lists
 * - Book page: /novel/novel-name
 * - Chapters: /novel/novel-name/chapters/page-1, page-2, etc.
 * - Search: Often disabled or requires different approach
 * - Catalog: /genre/all/popular/all/
 * 
 * Used by:
 * - LightNovelWorld.com
 * - NovelPub.com
 * - FreeWebNovel.com
 * - And 5+ other sites from lightnovel-crawler
 */
abstract class BaseNovelPubScraper(
    networkClient: NetworkClient
) : BaseScraperTemplate(networkClient) {

    // Default selectors for NovelPub-style sites
    override val selectBookCover: String = ".cover img[data-src]"
    override val selectBookDescription: String = ".summary .content"
    override val selectChapterList: String = ".chapter-list li a"
    override val selectChapterContent: String = "#chapter-container"
    override val selectCatalogItems: String = ".novel-item"
    override val selectCatalogItemTitle: String = "a[title]"
    override val selectCatalogItemUrl: String = "a[title]"
    override val selectCatalogItemCover: String = ".novel-cover img[data-src]"
    override val selectPaginationLastPage: String = "ul.pagination li:last-child.active"

    // NovelPub-specific settings
    protected open val genrePath: String = "genre"
    protected open val catalogGenre: String = "all"
    protected open val catalogSort: String = "popular"
    protected open val catalogStatus: String = "all"
    protected open val usesPaginatedChapterList: Boolean = true
    protected open val chaptersPerPage: Int = 100

    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        val builder = baseUrl.toUrlBuilderSafe()
            .addPath(genrePath)
            .addPath(catalogGenre)
            .addPath(catalogSort)
            .addPath(catalogStatus)
        
        if (page > 1) {
            builder.addPath(page.toString())
        }
        
        return builder.toString()
    }

    override fun buildSearchUrl(index: Int, input: String): String {
        // Many NovelPub-style sites have broken search
        // Subclasses should override if search is supported
        return catalogUrl
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<my.noveldokusha.core.PagedList<my.noveldokusha.scraper.domain.BookResult>> {
        // Default: search not supported for NovelPub-style sites
        // Subclasses can override if their site supports search
        return Response.Success(my.noveldokusha.core.PagedList.createEmpty(index = index))
    }

    override suspend fun fetchChapterList(bookUrl: String): List<ChapterResult> {
        return if (usesPaginatedChapterList) {
            fetchPaginatedChapterList(bookUrl)
        } else {
            super.fetchChapterList(bookUrl)
        }
    }

    protected open suspend fun fetchPaginatedChapterList(bookUrl: String): List<ChapterResult> = 
        withContext(Dispatchers.Default) {
            try {
                val chapters = mutableListOf<ChapterResult>()
                val baseChaptersUrl = bookUrl.toUrlBuilderSafe().addPath("chapters")
                
                for (page in 1..Int.MAX_VALUE) {
                    val pageUrl = baseChaptersUrl.addPath("page-$page").toString()
                    
                    val doc = networkClient.get(pageUrl).toDocument()
                    val pageChapters = doc.select(selectChapterList)
                        .map { element ->
                            ChapterResult(
                                title = element.attr("title").ifBlank { element.text() },
                                url = chapterUrlTransform(element.attr("href"))
                            )
                        }
                    
                    if (pageChapters.isEmpty()) {
                        break
                    }
                    
                    chapters.addAll(pageChapters)
                    
                    // Safety limit to prevent infinite loops
                    if (page >= 1000) {
                        break
                    }
                }
                
                chapters
            } catch (e: Exception) {
                super.fetchChapterList(bookUrl)
            }
        }

    override fun isLastPage(doc: org.jsoup.nodes.Document): Boolean {
        return when (val pagination = doc.selectFirst("ul.pagination")) {
            null -> true
            else -> {
                // Check if last child has "active" class
                val lastItem = pagination.children().lastOrNull()
                lastItem?.hasClass("active") ?: true
            }
        }
    }
}
