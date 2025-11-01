package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.templates.BaseMadaraScraper
import org.jsoup.nodes.Document

class SonicMTL(
    networkClient: NetworkClient
) : BaseMadaraScraper(networkClient) {
    override val id = "sonicmtl"
    override val nameStrId = R.string.source_name_sonicmtl
    override val baseUrl = "https://sonicmtl.com/"
    override val catalogUrl = "https://sonicmtl.com/novel/?m_orderby=latest"
    override val iconUrl = "https://sonicmtl.com/favicon.ico"
    override val language = LanguageCode.ENGLISH
    
    override val catalogOrderBy = "latest"
    
    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst(".reading-content .text-left")?.let { element ->
            element.select(".ad").remove()
            element.select(".c-ads").remove()
            element.select(".custom-code").remove()
            element.select(".body-top-ads").remove()
            element.select(".before-content-ad").remove()
            element.select(".autors-widget").remove()
            TextExtractor.get(element)
        } ?: ""
    }
}
