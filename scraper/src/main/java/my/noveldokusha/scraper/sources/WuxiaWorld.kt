package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseMadaraScraper

class WuxiaWorld(
    networkClient: NetworkClient
) : BaseMadaraScraper(networkClient) {
    override val id = "wuxia_world"
    override val nameStrId = R.string.source_name_wuxia_world
    override val baseUrl = "https://wuxiaworld.site/"
    override val catalogUrl = "https://wuxiaworld.site/novel/?m_orderby=trending"
    override val iconUrl = "https://wuxiaworld.site/wp-content/uploads/2019/04/favicon-1.ico"
    override val language = LanguageCode.ENGLISH

    override val catalogOrderBy = "alphabet"
    override val selectCatalogItemTitle: String = ".post-title h3 a"
}
