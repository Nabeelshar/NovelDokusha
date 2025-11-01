package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.templates.BaseNovelFullScraper
import org.jsoup.nodes.Document

class NovelFull(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "novelfull"
    override val nameStrId = R.string.source_name_novelfull
    override val baseUrl = "https://novelfull.net/"
    override val catalogUrl = "https://novelfull.net/novel?page=1"
    override val iconUrl = "https://novelfull.net/favicon.ico"
    override val language = LanguageCode.ENGLISH
    
    // NovelFull selectors
    override val selectBookCover = ".book img[src]"
    override val selectBookDescription = "#tab-description .desc-text"
    override val selectChapterList = "#list-chapter .row a"
    override val selectChapterContent = "#chr-content"
    override val selectCatalogItems = ".col-novel-main .list-novel .row"
    override val selectCatalogItemTitle = ".col-xs-7 a"
    override val selectCatalogItemUrl = ".col-xs-7 a"
    override val selectCatalogItemCover = ".col-xs-3 img[src]"
    
    override val novelIdSelector = "#rating[data-novel-id]"
    override val ajaxChapterPath = "ajax-chapter-option"
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return "$baseUrl/novel?page=$page"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return "$baseUrl/search?keyword=$input&page=$page"
    }
    
    override suspend fun getChapterTitle(doc: Document): String =
        doc.selectFirst(".chapter-text, .chapter-title")?.text() ?: ""
    
    override suspend fun getChapterText(doc: Document): String = 
        withContext(Dispatchers.Default) {
            doc.selectFirst("#chr-content")?.let { element ->
                element.select("script").remove()
                element.select(".ads").remove()
                TextExtractor.get(element)
            } ?: ""
        }
}
