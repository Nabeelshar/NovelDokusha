package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.R
import my.noveldokusha.data.BookMetadata
import my.noveldokusha.data.ChapterMetadata
import my.noveldokusha.data.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.PagedList
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.LanguageCode
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.utils.add
import my.noveldokusha.utils.addPath
import my.noveldokusha.utils.toDocument
import my.noveldokusha.utils.toUrlBuilderSafe
import org.jsoup.nodes.Document

class NovelCool(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "novelcool"
    override val nameStrId = R.string.source_name_novelcool
    override val baseUrl = "https://www.novelcool.com"
    override val catalogUrl = "https://www.novelcool.com/category/popular.html"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? = null
//    TODO Not getting chapter text
    override suspend fun getChapterText(doc: Document): String {
        return doc
            .selectFirst(".chapter-reading-section")!!
            .let { TextExtractor.get(it) }
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".bookinfo-pic")
                ?.selectFirst("img[src]")
                ?.attr("src")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".bookinfo-summary")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterMetadata>> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl)
                .toDocument()
                .select(".chapter-item-list a[href]")
                .map {
                    val title = it.selectFirst("span.chapter-item-headtitle")!!.text()
                    ChapterMetadata(
                        title = title,
                        url = it.attr("href"))

                }.reversed()
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookMetadata>> = withContext(Dispatchers.Default) {
        tryConnect {
//            val page = index + 1
//            val url = baseUrl.toUrlBuilderSafe().apply {
//                if (page == 1) addPath("all.html")
//                else addPath("all-$page.html")
//            }
            val doc = networkClient.get(catalogUrl).toDocument()
            doc.select(".book-item")
                .mapNotNull {
                    val link = it.selectFirst("a[href]") ?: return@mapNotNull null
                    val title = it.selectFirst(".book-name.single-line-ellipsis")!!.text()
                    val bookCover = it.selectFirst(".book-pic img[lazy_url]")?.attr("lazy_url") ?: ""

                    BookMetadata(
                        title = title,
                        url =  link.attr("href"),
                        coverImageUrl = bookCover
                    )
                }
                .let {
                    PagedList(
                        list = it,
                        index = index,
                        isLastPage = isLastPage(doc)
                    )
                }
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookMetadata>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank())
                return@tryConnect PagedList.createEmpty(index = index)

            val url = baseUrl.toUrlBuilderSafe().apply {
                addPath("index.php")
                add("s", "so")
                add("module", "book")
                add("keyword", input)
            }
            val doc = networkClient.get(url).toDocument()
            doc.selectFirst(".section3.inner.mt30 > table")
                ?.select("tr > td:nth-child(2) > a[href]")
                .let { it ?: listOf() }
                .map { link ->
                    BookMetadata(
                        title = link.text(),
                        url = baseUrl + link.attr("href").removePrefix("/"),
                    )
                }
                .let {
                    PagedList(
                        list = it,
                        index = index,
                        isLastPage = isLastPage(doc)
                    )
                }
        }
    }

    private fun isLastPage(doc: Document) = when (val nav = doc.selectFirst("div.page-nav")) {
        null -> true
        else -> nav.children().last()?.`is`("span") ?: true
    }
}
