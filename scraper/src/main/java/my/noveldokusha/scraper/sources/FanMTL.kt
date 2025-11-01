package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.toUrlBuilderSafe
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

/**
 * Novel main page (chapter list) example:
 * https://www.fanmtl.com/novel/information
 * Chapter url example:
 * https://www.fanmtl.com/novel/information/chapter-1
 */
class FanMTL(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "fanmtl"
    override val nameStrId = R.string.source_name_fanmtl
    override val baseUrl = "https://www.fanmtl.com/"
    override val catalogUrl = "https://www.fanmtl.com/novels"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title, h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst("#chapter-article .chapter-content")?.let { element ->
            element.select("script").remove()
            element.select("div[align=\"center\"]").remove()
            element.select(".ads").remove()
            TextExtractor.get(element)
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".novel-header figure.cover img[src]")
                ?.attr("src")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".novel-info .description, .novel-synopsis")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val chapters = mutableListOf<ChapterResult>()
            
            // Get first page to find pagination
            val firstDoc = networkClient.get(bookUrl).toDocument()
            val lastPageLink = firstDoc.select(".pagination a[data-ajax-update]").lastOrNull()
            
            if (lastPageLink != null) {
                val lastPageUrl = lastPageLink.attr("href")
                val pageMatch = Regex("page=(\\d+)").find(lastPageUrl)
                val wjmMatch = Regex("wjm=([^&]+)").find(lastPageUrl)
                
                if (pageMatch != null && wjmMatch != null) {
                    val pageCount = pageMatch.groupValues[1].toInt() + 1
                    val wjm = wjmMatch.groupValues[1]
                    
                    // Fetch all pages
                    for (page in 0 until pageCount) {
                        val pageUrl = "$bookUrl?page=$page&wjm=$wjm"
                        val doc = networkClient.get(pageUrl).toDocument()
                        
                        doc.select("ul.chapter-list li a[href]").forEach {
                            val title = it.selectFirst(".chapter-title")?.text() ?: it.text()
                            chapters.add(
                                ChapterResult(
                                    title = title.trim(),
                                    url = it.attr("href")
                                )
                            )
                        }
                    }
                }
            } else {
                // Single page chapter list
                firstDoc.select("ul.chapter-list li a[href]").forEach {
                    val title = it.selectFirst(".chapter-title")?.text() ?: it.text()
                    chapters.add(
                        ChapterResult(
                            title = title.trim(),
                            url = it.attr("href")
                        )
                    )
                }
            }
            
            chapters
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect("index=$index") {
            val page = index + 1
            val url = "$baseUrl/novels?page=$page"

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".novel-list .novel-item").mapNotNull {
                val link = it.selectFirst("a[href]") ?: return@mapNotNull null
                val title = it.selectFirst(".novel-title")?.text() ?: link.text()
                val bookCover = it.selectFirst("img[src]")?.attr("src") ?: ""
                
                BookResult(
                    title = title,
                    url = link.attr("href"),
                    coverImageUrl = bookCover
                )
            }

            PagedList(
                list = books,
                index = index,
                isLastPage = books.isEmpty() || doc.selectFirst(".pagination .next") == null
            )
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank() || index > 0)
                return@tryConnect PagedList.createEmpty(index = index)

            val url = baseUrl.toUrlBuilderSafe()
                .add("s", input)

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".novel-list .novel-item").mapNotNull {
                val link = it.selectFirst("a[href]") ?: return@mapNotNull null
                val title = it.selectFirst(".novel-title")?.text() ?: link.text()
                val bookCover = it.selectFirst("img[src]")?.attr("src") ?: ""
                
                BookResult(
                    title = title,
                    url = link.attr("href"),
                    coverImageUrl = bookCover
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
