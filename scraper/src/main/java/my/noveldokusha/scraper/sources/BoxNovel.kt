package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper

class BoxNovel(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "box_novel"
    override val nameStrId = R.string.source_name_box_novel
    override val baseUrl = "https://novlove.com/"
    override val catalogUrl = "https://novlove.com/novel?page=1"
    override val iconUrl = "https://novlove.com/favicon.ico"
    override val language = LanguageCode.ENGLISH

    // BoxNovel-specific selectors to avoid garbage
    override val selectCatalogItems = ".list-novel .row > div"
    override val selectCatalogItemTitle = ".novel-title a"
    override val selectCatalogItemCover = ".cover img"
    override val selectPaginationLastPage = "ul.pagination li:last-child"

    override fun isLastPage(doc: org.jsoup.nodes.Document): Boolean {
        val lastLi = doc.selectFirst(selectPaginationLastPage)
        return lastLi == null || lastLi.hasClass("disabled")
    }
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return "$baseUrl/novel?page=$page"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return "$baseUrl/search?keyword=$input&page=$page"
    }
}
