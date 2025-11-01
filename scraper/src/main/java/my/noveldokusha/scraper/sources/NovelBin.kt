package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.getRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.scraper.templates.BaseNovelFullScraper
import okhttp3.Headers
import org.jsoup.nodes.Document

class NovelBin(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "Novelbin"
    override val nameStrId = R.string.source_name_novelbin
    override val baseUrl = "https://novelbin.me/"
    override val catalogUrl = "https://novelbin.me/sort/novelbin-daily-update"
    override val iconUrl = "https://novelbin.me/img/logo.png"
    override val language = LanguageCode.ENGLISH
    
    // NovelBin-specific selectors
    override val selectBookCover = "meta[itemprop=image]"
    override val selectBookDescription = "div.desc-text"
    override val selectChapterList = "ul.list-chapter li a"
    override val selectChapterContent = ".container .adsads"
    override val selectCatalogItems = "#list-page div.list-novel .row"
    override val selectCatalogItemTitle = "div.col-xs-7 a"
    override val selectCatalogItemUrl = "div.col-xs-7 a"
    override val selectCatalogItemCover = "div.col-xs-3 > div > img[data-src]"
    override val selectPaginationLastPage = "ul.pagination li.next:not(.disabled)"
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return if (page == 1) catalogUrl
        else "$catalogUrl?page=$page"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return if (page == 1) "$baseUrl/search?keyword=$input"
        else "$baseUrl/search?keyword=$input&page=$page"
    }
    
    override suspend fun getChapterTitle(doc: Document): String = 
        doc.selectFirst("h2 > .title-chapter")?.text() ?: ""
    
    override suspend fun fetchChapterList(bookUrl: String): List<ChapterResult> = 
        withContext(Dispatchers.Default) {
            val keyId = networkClient.get(bookUrl)
                .toDocument()
                .selectFirst("meta[property=og:url]")
                ?.attr("content")
                ?.toUrlBuilderSafe()
                ?.build()
                ?.lastPathSegment ?: throw Exception("Novel ID not found")
            
            val request = getRequest(
                url = baseUrl.toUrlBuilderSafe()
                    .addPath("ajax", "chapter-archive")
                    .add("novelId" to keyId)
                    .toString(),
                headers = Headers.Builder()
                    .add("Accept", "*/*")
                    .add("X-Requested-With", "XMLHttpRequest")
                    .add("User-Agent", "Mozilla/5.0 (Android 13; Mobile; rv:125.0) Gecko/125.0 Firefox/125.0")
                    .add("Referer", "$bookUrl#tab-chapters-title")
                    .build()
            )
            
            networkClient.call(request)
                .toDocument()
                .select(selectChapterList)
                .map { ChapterResult(it.attr("title") ?: "", it.attr("href") ?: "") }
        }
    
    override fun isLastPage(doc: Document) = 
        doc.select("ul.pagination li.next.disabled").isNotEmpty()
}
