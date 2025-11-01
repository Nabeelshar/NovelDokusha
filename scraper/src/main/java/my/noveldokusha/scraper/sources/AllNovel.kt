package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper

class AllNovel(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "allnovel"
    override val nameStrId = R.string.source_name_allnovel
    override val baseUrl = "https://allnovel.org/"
    override val catalogUrl = "https://allnovel.org/index.php/novel?page=1"
    override val iconUrl = "https://allnovel.org/favicon.ico"
    override val language = LanguageCode.ENGLISH
    
    override val novelIdSelector = "#rating[data-novel-id]"
    override val ajaxChapterPath = "ajax-chapter-option"
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return "$baseUrl/index.php/novel?page=$page"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return "$baseUrl/index.php/search?keyword=$input&page=$page"
    }
}
