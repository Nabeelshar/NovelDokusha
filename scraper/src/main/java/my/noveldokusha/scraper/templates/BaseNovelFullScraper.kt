package my.noveldokusha.scraper.templates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.domain.ChapterResult

/**
 * Template for ReadNovelFull-style sites.
 * 
 * Common pattern:
 * - Book page: /novel-name.html
 * - Chapters loaded via AJAX from separate endpoint
 * - Search: /novel-list/search?keyword=query
 * - Catalog: /novel-list/most-popular-novel or similar
 * 
 * Used by:
 * - ReadNovelFull.com
 * - BestLightNovel.com
 * - NovelHall.com
 * - And 11+ other sites from lightnovel-crawler
 */
abstract class BaseNovelFullScraper(
    networkClient: NetworkClient
) : BaseScraperTemplate(networkClient) {

    // Default selectors for NovelFull-style sites
    override val selectBookCover: String = ".book img[src]"
    override val selectBookDescription: String = "#tab-description"
    override val selectChapterList: String = "a[href]"
    override val selectChapterContent: String = "#chr-content"
    override val selectCatalogItems: String = ".row"
    override val selectCatalogItemTitle: String = "a[href]"
    override val selectCatalogItemUrl: String = "a[href]"
    override val selectCatalogItemCover: String = "img[src]"
    override val selectPaginationLastPage: String = "ul.pagination li:last-child.disabled"

    // Search selectors (usually same as catalog)
    override val selectSearchItems: String = ".row"
    override val selectSearchItemTitle: String = "a[href]"
    override val selectSearchItemUrl: String = "a[href]"
    override val selectSearchItemCover: String = "img[src]"

    // Novel list path (override if different)
    protected open val novelListPath: String = "novel-list"
    protected open val catalogOrderBy: String = "most-popular-novel"
    
    // Ajax chapter loading support
    protected open val useAjaxChapterLoading: Boolean = true
    protected open val ajaxChapterPath: String = "ajax/chapter-archive"
    protected open val novelIdAttribute: String = "data-novel-id"
    protected open val novelIdSelector: String = "#rating[$novelIdAttribute]"

    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return catalogUrl.toUrlBuilderSafe().apply {
            if (page > 1) add("page", page)
        }.toString()
    }

    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        val builder = baseUrl.toUrlBuilderSafe().addPath(novelListPath, "search")
        builder.add("keyword", input)
        if (page > 1) builder.add("page", page)
        return builder.toString()
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
            try {
                val doc = networkClient.get(bookUrl).toDocument()
                val novelId = doc.selectFirst(novelIdSelector)?.attr(novelIdAttribute)
                    ?: throw Exception("Novel ID not found")
                
                val ajaxUrl = baseUrl + ajaxChapterPath + "?novelId=" + novelId
                
                networkClient.get(ajaxUrl)
                    .toDocument()
                    .select(selectChapterList)
                    .map { element ->
                        ChapterResult(
                            title = element.text(),
                            url = chapterUrlTransform(element.attr("href"))
                        )
                    }
            } catch (e: Exception) {
                // Fallback to regular chapter list if ajax fails
                super.fetchChapterList(bookUrl)
            }
        }
}
