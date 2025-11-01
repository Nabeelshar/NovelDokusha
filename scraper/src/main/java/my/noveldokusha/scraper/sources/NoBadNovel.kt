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

/**
 * Novel main page (chapter list) example:
 * https://www.nobadnovel.com/series/too-wicked-princess-embracing-her-leads-to-unexpected-delight
 * Chapter url example:
 * https://www.nobadnovel.com/series/too-wicked-princess-embracing-her-leads-to-unexpected-delight/chapter-1-yang-an
 */
class NoBadNovel(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "nobadnovel"
    override val nameStrId = R.string.source_name_nobadnovel
    override val baseUrl = "https://www.nobadnovel.com/"
    override val catalogUrl = "https://www.nobadnovel.com/series"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst("div.text-base.sm\\:text-lg, div[class*=text-base]")!!.let {
            // Remove ads and scripts
            it.select("script").remove()
            it.select("ins").remove()
            it.select("a").remove()
            it.select(".hidden").remove()
            TextExtractor.get(it)
        }
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("img[src*=cdn.nobadnovel]")
                ?.attr("src")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("#intro .content")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl)
                .toDocument()
                .select(".chapter-list a[href]")
                .map {
                    ChapterResult(
                        title = it.text().trim(),
                        url = it.attr("href")
                    )
                }
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect("index=$index") {
            val page = index + 1
            val url = baseUrl.toUrlBuilderSafe()
                .addPath("series")
                .apply {
                    if (page > 1) {
                        addPath("page", page.toString())
                    }
                }

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".grid > div").mapNotNull {
                val link = it.selectFirst("a[href*=/series/]") ?: return@mapNotNull null
                val title = it.selectFirst("h4 a")?.text() ?: link.attr("href").split("/").last()
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
                isLastPage = books.isEmpty() || doc.selectFirst(".pagination a[href*=page/${page + 1}]") == null
            )
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank())
                return@tryConnect PagedList.createEmpty(index = index)

            val page = index + 1
            val url = baseUrl.toUrlBuilderSafe()
                .addPath("series")
                .apply {
                    add("keyword", input)
                    if (page > 1) {
                        addPath("page", page.toString())
                    }
                }

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".grid > div").mapNotNull {
                val link = it.selectFirst("a[href*=/series/]") ?: return@mapNotNull null
                val title = it.selectFirst("h4 a")?.text() ?: link.attr("href").split("/").last()
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
                isLastPage = books.isEmpty() || doc.selectFirst(".pagination a[href*=page/${page + 1}]") == null
            )
        }
    }
}
