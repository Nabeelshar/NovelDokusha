package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseMadaraScraper

class ReadMTL(
    networkClient: NetworkClient
) : BaseMadaraScraper(networkClient) {
    override val id = "readmtl"
    override val nameStrId = R.string.source_name_readmtl
    override val baseUrl = "https://www.readmtl.com/"
    override val catalogUrl = "https://www.readmtl.com/novel/?m_orderby=latest"
    override val iconUrl = "https://www.readmtl.com/favicon.ico"
    override val language = LanguageCode.ENGLISH
    
    override val catalogOrderBy = "latest"
}
