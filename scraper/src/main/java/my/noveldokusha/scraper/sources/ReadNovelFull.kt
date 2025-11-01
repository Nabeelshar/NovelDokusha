package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper
import org.jsoup.nodes.Document

/**
 * Novel main page (chapter list) example:
 * https://readnovelfull.com/reincarnation-of-the-strongest-sword-god-v2.html
 * Chapter url example:
 * https://readnovelfull.com/reincarnation-of-the-strongest-sword-god/chapter-1-starting-over-v1.html
 */
class ReadNovelFull(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "read_novel_full"
    override val nameStrId = R.string.source_name_read_novel_full
    override val baseUrl = "https://readnovelfull.com/"
    override val catalogUrl = "https://readnovelfull.com/novel-list/most-popular-novel"
    override val language = LanguageCode.ENGLISH

    // ReadNovelFull-specific catalog structure
    override val selectCatalogItems = ".col-novel-main.archive .row"
    
    // Specific ajax settings
    override val novelIdSelector = "#rating[data-novel-id]"
    override val ajaxChapterPath = "ajax/chapter-archive"
}
