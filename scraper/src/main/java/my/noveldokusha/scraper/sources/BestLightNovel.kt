package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelFullScraper

class BestLightNovel(
    networkClient: NetworkClient
) : BaseNovelFullScraper(networkClient) {
    override val id = "best_light_novel"
    override val nameStrId = R.string.source_name_bestlightnovel
    override val baseUrl = "https://bestlightnovel.com/"
    override val catalogUrl = "https://bestlightnovel.com/novel_list"
    override val language = LanguageCode.ENGLISH

    // BestLightNovel-specific selectors
    override val selectBookCover = ".info_image > img[src]"
    override val selectBookDescription = "#noidungm"
    override val selectChapterList = "div.chapter-list a[href]"
    override val selectChapterContent = "#vung_doc"
    override val selectCatalogItems = ".update_item.list_category"
    override val selectCatalogItemTitle = "a[href]"
    override val selectCatalogItemUrl = "a[href]"
    override val selectCatalogItemCover = "img[src]"
    override val selectPaginationLastPage = "div.phan-trang .pageselect:nth-last-child(2)"

    // BestLightNovel uses direct chapter list, not ajax
    override val useAjaxChapterLoading = false
    
    override fun buildCatalogUrl(index: Int): String {
        val page = index + 1
        return if (page == 1) {
            catalogUrl
        } else {
            "$catalogUrl?type=newest&category=all&state=all&page=$page"
        }
    }

    override fun buildSearchUrl(index: Int, input: String): String {
        val page = index + 1
        val searchPath = input.replace(" ", "_")
        return if (page == 1) {
            "$baseUrl/search_novels/$searchPath"
        } else {
            "$baseUrl/search_novels/$searchPath?page=$page"
        }
    }
}
