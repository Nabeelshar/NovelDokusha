package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper
import org.jsoup.nodes.Document

class NovelHall(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "novelhall"
    override val nameStrId = R.string.source_name_novelhall
    override val baseUrl = "https://www.novelhall.com/"
    override val catalogUrl = "https://www.novelhall.com/all.html"
    override val language = LanguageCode.ENGLISH

    // NovelHall-specific selectors
    override val selectBookCover = ".book-img.hidden-xs img[src]"
    override val selectBookDescription = "span.js-close-wrap"
    override val selectChapterList = "#morelist a[href]"
    override val selectChapterContent = "div#htmlContent"
    override val selectCatalogItems = "li.btm"
    override val selectPaginationLastPage = "div.page-nav span:last-child"
    
    // NovelHall uses direct chapter list, not ajax
    override val useAjaxChapterLoading = false
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return if (page == 1) "$baseUrl/all.html"
        else "$baseUrl/all-$page.html"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        return "$baseUrl/index.php?s=so&module=book&keyword=$input"
    }
    
    override fun isLastPage(doc: Document) = 
        doc.selectFirst("div.page-nav")?.children()?.last()?.tagName() == "span"
}
