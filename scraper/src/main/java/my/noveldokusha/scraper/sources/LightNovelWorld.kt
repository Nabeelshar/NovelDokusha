package my.noveldokusha.scraper.sources

import my.noveldokusha.core.LanguageCode
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.templates.BaseNovelPubScraper

/**
 * Novel main page (chapter list) example:
 * https://www.lightnovelworld.com/novel/the-devil-does-not-need-to-be-defeated
 * Chapter url example:
 * https://www.lightnovelworld.com/novel/the-devil-does-not-need-to-be-defeated/1348-chapter-0
 */
class LightNovelWorld(
    networkClient: NetworkClient
) : BaseNovelPubScraper(networkClient) {
    override val id = "light_novel_world"
    override val nameStrId = R.string.source_name_light_novel_world
    override val baseUrl = "https://www.lightnovelworld.com/"
    override val catalogUrl = "https://www.lightnovelworld.com/genre/all/popular/all/"
    override val iconUrl = "https://static.lightnovelworld.com/content/img/lightnovelworld/favicon.png"
    override val language = LanguageCode.ENGLISH
}
