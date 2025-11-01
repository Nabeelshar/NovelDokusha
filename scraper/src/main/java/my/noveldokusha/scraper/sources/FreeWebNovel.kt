package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

class FreeWebNovel(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "freewebnovel"
    override val nameStrId = R.string.source_name_freewebnovel
    override val baseUrl = "https://freewebnovel.com/"
    override val catalogUrl = "https://freewebnovel.com/completed-novel/"
    override val iconUrl = "https://freewebnovel.com/favicon.ico"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1, .chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".txt")?.let { element ->
                element.select("script").remove()
                element.select(".ads").remove()
                element.select("h4").remove()
                element.select("sub").remove()
                TextExtractor.get(element)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl)
                    .toDocument()
                    .selectFirst(".m-imgtxt img")
                    ?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl)
                    .toDocument()
                    .selectFirst(".m-desc .txt")
                    ?.let { TextExtractor.get(it) }
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl)
                    .toDocument()
                    .select("#idData li a")
                    .map { element ->
                        ChapterResult(
                            title = element.text(),
                            url = element.attr("href")
                        )
                    }
            }
        }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (index > 0)
                    return@tryConnect PagedList.createEmpty(index = index)

                val doc = networkClient.get(catalogUrl).toDocument()
                val books = doc.select(".ul-list1 .li-row")
                    .mapNotNull { element ->
                        val link = element.selectFirst(".tit a") ?: return@mapNotNull null
                        val coverUrl = element.selectFirst(".pic img")
                            ?.let { it.attr("data-src").ifEmpty { it.attr("src") } } ?: ""

                        BookResult(
                            title = link.text(),
                            url = link.attr("href"),
                            coverImageUrl = coverUrl
                        )
                    }

                PagedList(
                    list = books,
                    index = index,
                    isLastPage = true
                )
            }
        }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                if (input.isBlank() || index > 0)
                    return@tryConnect PagedList.createEmpty(index = index)

                val request = postRequest("${baseUrl}search/")
                    .postPayload {
                        add("searchkey", input)
                    }

                val doc = networkClient.call(request).toDocument()
                val books = doc.select(".col-content .con .txt h3 a")
                    .map { element ->
                        BookResult(
                            title = element.text(),
                            url = element.attr("href"),
                            coverImageUrl = ""
                        )
                    }

                PagedList(
                    list = books,
                    index = index,
                    isLastPage = true
                )
            }
        }
}
