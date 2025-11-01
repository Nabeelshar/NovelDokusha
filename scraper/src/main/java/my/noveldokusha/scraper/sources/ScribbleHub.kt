package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.addPath
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

class ScribbleHub(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "scribblehub"
    override val nameStrId = R.string.source_name_scribblehub
    override val baseUrl = "https://www.scribblehub.com/"
    override val catalogUrl = "https://www.scribblehub.com/series-ranking/?sort=1&order=2"
    override val iconUrl = "https://www.scribblehub.com/wp-content/uploads/2020/02/cropped-SchribbleHub-Favicon-32x32.png"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title")?.text()
        }

    override suspend fun getChapterText(doc: Document): String =
        withContext(Dispatchers.Default) {
            doc.selectFirst("#chp_raw")?.let { element ->
                element.select("script").remove()
                element.select("div.modern_chapter_ad").remove()
                TextExtractor.get(element)
            } ?: ""
        }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl)
                    .toDocument()
                    .selectFirst(".fic_image img[src]")
                    ?.attr("src")
            }
        }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> =
        withContext(Dispatchers.Default) {
            tryConnect {
                networkClient.get(bookUrl)
                    .toDocument()
                    .selectFirst(".wi_fic_desc")
                    ?.let { TextExtractor.get(it) }
            }
        }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> =
        withContext(Dispatchers.Default) {
            tryConnect {
                // Extract series ID from URL
                val seriesId = bookUrl.split("/").lastOrNull { it.isNotEmpty() }
                    ?: throw Exception("Invalid book URL")

                val url = baseUrl.toUrlBuilderSafe()
                    .addPath("wp-admin", "admin-ajax.php")
                    .add("action", "wi_getreleases_pagination")
                    .add("pagenum", "-1")
                    .add("mypostid", seriesId)
                    .toString()

                networkClient.get(url)
                    .toDocument()
                    .select(".toc_w a[href]")
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
                val page = index + 1
                val url = baseUrl.toUrlBuilderSafe()
                    .addPath("series-ranking")
                    .add("sort", "1")
                    .add("order", "2")
                    .add("pg", page.toString())
                    .toString()

                val doc = networkClient.get(url).toDocument()
                val books = doc.select(".search_main_box")
                    .mapNotNull { element ->
                        val link = element.selectFirst(".search_title a[href]")
                            ?: return@mapNotNull null
                        val coverUrl = element.selectFirst(".search_img img[src]")
                            ?.attr("src") ?: ""

                        BookResult(
                            title = link.text(),
                            url = link.attr("href"),
                            coverImageUrl = coverUrl
                        )
                    }

                PagedList(
                    list = books,
                    index = index,
                    isLastPage = books.isEmpty()
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

                val url = baseUrl.toUrlBuilderSafe()
                    .add("s", input)
                    .add("post_type", "fictionposts")
                    .toString()

                val doc = networkClient.get(url).toDocument()
                val books = doc.select(".search_main_box")
                    .mapNotNull { element ->
                        val link = element.selectFirst(".search_title a[href]")
                            ?: return@mapNotNull null
                        val coverUrl = element.selectFirst(".search_img img[src]")
                            ?.attr("src") ?: ""

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
}
