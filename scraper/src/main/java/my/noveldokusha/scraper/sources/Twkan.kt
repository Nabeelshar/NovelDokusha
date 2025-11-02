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
import java.net.URI

/**
 * Scraper for https://twkan.com/
 *
 * Structure notes:
 * - Catalog pages: novels under `#article_list_content li` with `.newnav h3 a[href]` for title
 * - Book page: `/book/{id}.html` with metadata and recent chapters under `.qustime ul li a[href]`
 * - Full chapter list: `/book/{id}/index.html` with all chapters under `.catalog ul li a[href]`
 * - Chapter page: `/txt/{bookId}/{chapterId}` with content in `#txtcontent0`
 */
class Twkan(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "twkan"
    override val nameStrId = R.string.source_name_twkan
    override val baseUrl = "https://twkan.com/"
    override val catalogUrl = "https://twkan.com/novels/newhot_2_0_1.html"
    override val language = LanguageCode.CHINESE

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".txtnav h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst("#txtcontent0")?.let { content ->
            // Remove ads and scripts
            content.select("script, .txtad").remove()
            TextExtractor.get(content)
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".bookimg2 img[src]")
                ?.attr("src")
                ?.let { src ->
                    when {
                        src.startsWith("http") -> src
                        src.startsWith("/") -> URI(baseUrl).resolve(src).toString()
                        else -> null
                    }
                }
        }
    }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("#tab_info .navtxt p")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            // Convert /book/{id}.html to /{id}/ for chapter list (same as Shuba69 pattern)
            val chapterListUrl = bookUrl.replace("/book/", "/").replace(".html", "/")
            
            networkClient.get(chapterListUrl).toDocument()
                .select("div#catalog ul li a")
                .map { element ->
                    ChapterResult(
                        title = element.text().trim(),
                        url = URI(baseUrl).resolve(element.attr("href")).toString()
                    )
                }
        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val page = index + 1
            // Catalog URL pattern: /novels/newhot_2_0_{page}.html
            val url = if (page == 1) {
                catalogUrl
            } else {
                "https://twkan.com/novels/newhot_2_0_${page}.html"
            }

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("#article_list_content li")
                .mapNotNull { li ->
                    val link = li.selectFirst(".newnav h3 a[href]") ?: return@mapNotNull null
                    val imgElement = li.selectFirst(".imgbox img[src], .imgbox img[data-src]")
                    val imgSrc = imgElement?.attr("src") ?: imgElement?.attr("data-src") ?: ""
                    
                    BookResult(
                        title = link.text().trim(),
                        url = URI(baseUrl).resolve(link.attr("href")).toString(),
                        coverImageUrl = when {
                            imgSrc.startsWith("http") -> imgSrc
                            imgSrc.startsWith("/") && !imgSrc.contains("nocover") -> 
                                URI(baseUrl).resolve(imgSrc).toString()
                            else -> ""
                        }
                    )
                }

            PagedList(list = items, index = index, isLastPage = items.isEmpty())
        }
    }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank())
                return@tryConnect PagedList.createEmpty(index = index)

            // Search URL pattern: /search/{encoded_query}/{page}.html
            val encodedQuery = java.net.URLEncoder.encode(input, "UTF-8")
            val page = index + 1
            val url = "https://twkan.com/search/$encodedQuery/$page.html"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("#article_list_content li")
                .mapNotNull { li ->
                    val link = li.selectFirst("a[href*=/book/]") ?: return@mapNotNull null
                    val title = li.selectFirst("h3 a")?.text()?.trim() ?: return@mapNotNull null
                    val imgElement = li.selectFirst("img[src], img[data-src]")
                    val imgSrc = imgElement?.attr("src") ?: imgElement?.attr("data-src") ?: ""
                    
                    BookResult(
                        title = title,
                        url = link.attr("abs:href"),
                        coverImageUrl = when {
                            imgSrc.startsWith("http") -> imgSrc
                            imgSrc.startsWith("/") && !imgSrc.contains("nocover") -> 
                                URI(baseUrl).resolve(imgSrc).toString()
                            else -> ""
                        }
                    )
                }

            PagedList(list = items, index = index, isLastPage = true)
        }
    }
}
