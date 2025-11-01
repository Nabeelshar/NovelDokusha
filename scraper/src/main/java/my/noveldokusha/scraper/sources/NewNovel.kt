package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper

class NewNovel(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "newnovel"
    override val nameStrId = R.string.source_name_newnovel
    override val baseUrl = "https://newnovel.org/"
    override val catalogUrl = "https://newnovel.org/novel?page=1"
    override val iconUrl = "https://newnovel.org/favicon.ico"
    override val language = LanguageCode.ENGLISH
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return "$baseUrl/novel?page=$page"
    }
    
    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        return "$baseUrl/search?keyword=$input&page=$page"
    }
}
