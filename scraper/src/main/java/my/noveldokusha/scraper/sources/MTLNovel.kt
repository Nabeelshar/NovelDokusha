package my.noveldokusha.scraper.sources

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.scraper.templates.BaseNovelFullScraper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class MTLNovel(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "mtlnovel"
    override val nameStrId = R.string.source_name_mtlnovel
    override val baseUrl = "https://www.mtlnovel.com/"
    override val catalogUrl = "https://www.mtlnovel.com/alltime-rank/"
    override val language = LanguageCode.ENGLISH
    
    // MTLNovel-specific selectors
    override val selectBookCover = "amp-img.main-tmb[src]"
    override val selectBookDescription = ".desc"
    override val selectChapterList = "a.ch-link[href]"
    override val selectChapterContent = ".par.fontsize-16"
    override val selectCatalogItems = ".box.wide"
    override val selectCatalogItemTitle = "a.list-title[href]"
    override val selectCatalogItemUrl = "a.list-title[href]"
    override val selectCatalogItemCover = "amp-img[src]"
    override val selectPaginationLastPage = "div#pagination span:last-child"
    
    // MTLNovel uses direct chapter list, not ajax
    override val useAjaxChapterLoading = false
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return if (page == 1) catalogUrl
        else "$catalogUrl/page/$page/"
    }
    
    override suspend fun fetchChapterList(bookUrl: String): List<ChapterResult> = 
        withContext(Dispatchers.Default) {
            // MTLNovel needs trailing slash for chapter-list
            val url = bookUrl.trimEnd('/') + "/chapter-list/"
            networkClient.get(url)
                .toDocument()
                .select(selectChapterList)
                .map {
                    ChapterResult(
                        title = it.text(),
                        url = it.attr("href")
                    )
                }
                .reversed()
        }
    
    override fun isLastPage(doc: Document) = 
        doc.selectFirst("div#pagination")?.children()?.last()?.tagName() == "span"

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        try {
            if (input.isBlank() || index > 0)
                return@withContext Response.Success(PagedList.createEmpty(index = index))

            val url = """https://www.mtlnovel.com/wp-admin/admin-ajax.php"""
                .toUrlBuilderSafe()
                .add("action", "autosuggest")
                .add("q", input)
                .add("__amp_source_origin", "https://www.mtlnovel.com")
                .toString()

            val request = getRequest(url)
            val json = networkClient.call(request)
                .body
                .string()

            val results = JsonParser
                .parseString(json)
                .asJsonObject["items"]
                .asJsonArray[0]
                .asJsonObject["results"]
                .asJsonArray
                .map { it.asJsonObject }
                .map {
                    BookResult(
                        title = Jsoup.parse(it["title"].asString).text(),
                        url = it["permalink"].asString,
                        coverImageUrl = it["thumbnail"].asString
                    )
                }
            
            Response.Success(
                PagedList(
                    list = results,
                    index = index,
                    isLastPage = true
                )
            )
        } catch (e: Exception) {
            Response.Error("Search failed", e)
        }
    }
}
