package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.ifCase
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import org.jsoup.nodes.Document

class Jaomix(private val networkClient: NetworkClient) : SourceInterface.Catalog {
    override val id = "Jaomix"
    override val nameStrId = R.string.source_name_jaomix
    override val baseUrl = "https://jaomix.ru/"
    override val catalogUrl = "https://jaomix.ru/"
    override val iconUrl = "https://jaomix.ru/wp-content/uploads/2019/08/cropped-logo-2-150x150.png"
    override val language = LanguageCode.RUSSIAN

    private suspend fun getPagesList(index: Int, url: String) =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(url).toDocument().run {
                    val isLastPage = select("ul.pagiinput").isEmpty()
                    val bookResults =
                        select("div.block-home > div.one").mapNotNull {
                            val link = it.selectFirst("div.img-home > a") ?: return@mapNotNull null
                            val bookCover =
                                it.selectFirst("div.img-home > a > img")?.attr("src") ?: ""
                            BookResult(
                                title = link.attr("title"),
                                url = link.attr("href"),
                                coverImageUrl = bookCover
                            )
                        }
                    PagedList(list = bookResults, index = index, isLastPage = !isLastPage)
                }
            }
        }

    override suspend fun getChapterTitle(doc: Document): String =
        withContext(Dispatchers.Default) { doc.selectFirst(".entry-title")?.text() ?: "" }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst(".entry-content")!!.let {
            it.select(".adblock-service").remove()
            TextExtractor.get(it)
        }
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient
                    .get(bookUrl)
                    .toDocument()
                    .selectFirst("div.img-book > img")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl).toDocument().selectFirst("#desc-tab")?.text()
            }
        }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl)
                .toDocument()
                .select("div.title")
                .map {
                    ChapterResult(it.selectFirst("a")?.attr("title") ?: "", it.selectFirst("a")?.attr("href") ?: "")
                }
                .reversed()

        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            val page = index + 1
            val url =
                catalogUrl
                    .toUrlBuilderSafe()
                    .ifCase(page > 1) { add("gpage", page.toString()) }
                    .toString()
            getPagesList(index, url)
        }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String,
    ): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            val page = index + 1
            val url =
                baseUrl
                    .toUrlBuilderSafe()
                    .add("searchrn" to input)
                    .ifCase(page > 1) { add("gpage", page.toString()) }
                    .toString()
            getPagesList(index, url)
        }
}
