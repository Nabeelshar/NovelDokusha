package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.add
import my.noveldokusha.network.postPayload
import my.noveldokusha.network.postRequest
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
import java.net.URLEncoder

/**
 * Scraper for https://www.69shuba.com/
 * Based on lightnovel-crawler Python implementation
 * 
 * Features:
 * - GBK encoding support for Chinese content
 * - Search functionality
 * - Catalog browsing (monthly visit ranking)
 * - Chapter list extraction
 */
class Shuba69(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "shuba69"
    override val nameStrId = R.string.source_name_69shuba
    override val baseUrl = "https://www.69shuba.com/"
    override val catalogUrl = "https://www.69shuba.com/novels/monthvisit_0_0_1.htm"
    override val language = LanguageCode.CHINESE
    override val charset = "GBK"

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("div.txtnav h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        val content = doc.selectFirst("div.txtnav")
        content?.let { 
            // Remove unwanted elements
            it.select("h1").remove()
            it.select("div.txtinfo").remove()
            it.select("div#txtright").remove()
            TextExtractor.get(it)
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("div.bookimg2 img")
                ?.attr("src")
                ?.let { if (it.startsWith("http")) it else URI(baseUrl).resolve(it).toString() }
        }
    }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument(charset)
                .selectFirst("div.navtxt")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            // Convert /txt/A43616.htm to /A43616/ for chapter list
            val chapterListUrl = bookUrl.replace("/txt/", "/").replace(".htm", "/")
            
            // Site lists chapters newest-first; reverse so we return oldest-first
            networkClient.get(chapterListUrl).toDocument(charset)
                .select("div#catalog ul li a")
                .map { element ->
                    ChapterResult(
                        title = element.text().trim(),
                        url = URI(baseUrl).resolve(element.attr("href")).toString()
                    )
                }
                .reversed()
        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val page = index + 1
            val url = "https://www.69shuba.com/novels/monthvisit_0_0_$page.htm"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("div.newbox ul li")
                .mapNotNull {
                    val titleLink = it.selectFirst("h3 a:not([imgbox])")
                    val bookLink = it.selectFirst("a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("data-src") ?: ""
                    
                    val title = titleLink?.text()?.trim() ?: bookLink.text().trim()
                    
                    BookResult(
                        title = title,
                        url = URI(baseUrl).resolve(bookLink.attr("href")).toString(),
                        coverImageUrl = if (img.startsWith("http")) img else URI(baseUrl).resolve(img).toString()
                    )
                }

            PagedList(list = items, index = index, isLastPage = items.isEmpty())
        }
    }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank() || index > 0)
                return@tryConnect PagedList.createEmpty(index = index)

            // Check if input is a direct URL to this source
            if (input.startsWith("http") && (input.contains("69shuba.com") || input.contains("www.69shu.com"))) {
                // Extract book info from the URL directly
                // Convert /book/ URL to /txt/ format for proper book page
                val bookUrl = if (input.contains("/book/")) {
                    input.replace("/book/", "/txt/")
                } else {
                    input
                }
                
                // Fetch the book page to get title and cover using GBK charset
                val doc = networkClient.get(bookUrl).toDocument(charset)
                
                // Try multiple selectors to find the title
                val title = doc.selectFirst("div.bookinfo h1")?.text()?.trim()
                    ?: doc.selectFirst("div.bookimg2 img")?.attr("alt")?.trim()
                    ?: doc.selectFirst("h1")?.text()?.trim()
                    ?: doc.selectFirst("div.bookinfo h2")?.text()?.trim()
                    ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: "Unknown Novel"
                
                val coverImg = doc.selectFirst("div.bookimg2 img")?.attr("src") 
                    ?: doc.selectFirst("img[alt]")?.attr("src")
                    ?: ""
                
                return@tryConnect PagedList(
                    list = listOf(
                        BookResult(
                            title = title,
                            url = bookUrl,
                            coverImageUrl = if (coverImg.startsWith("http")) coverImg else URI(baseUrl).resolve(coverImg).toString()
                        )
                    ),
                    index = index,
                    isLastPage = true
                )
            }

            // Search using POST request with GBK encoding
            val searchUrl = "https://www.69shuba.com/modules/article/search.php"
            val encodedQuery = URLEncoder.encode(input, "GBK")
            
            val request = postRequest(searchUrl)
                .postPayload {
                    add("searchkey", encodedQuery)
                    add("submit", "Search")
                }
            
            // Some sites may challenge POST search requests via Cloudflare. Try once,
            // and if the Cloudflare bypass interceptor throws, attempt to prime cookies
            // by visiting the base site and then retry the POST once.
            val doc = try {
                networkClient.call(request).toDocument()
            } catch (e: Exception) {
                // Attempt to trigger Cloudflare bypass (interceptor will launch WebView if needed)
                try {
                    networkClient.get(baseUrl).toDocument()
                } catch (_: Exception) {
                    // ignore - best-effort
                }
                // Retry the original request once
                networkClient.call(request).toDocument()
            }
            
            val items = doc.select("div.newbox ul li")
                .mapNotNull {
                    val titleLink = it.selectFirst("h3 a:not([imgbox])")
                    val bookLink = it.selectFirst("a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("data-src") ?: ""
                    
                    val title = titleLink?.text()?.trim() ?: bookLink.text().trim()
                    
                    BookResult(
                        title = title,
                        url = URI(baseUrl).resolve(bookLink.attr("href")).toString(),
                        coverImageUrl = if (img.startsWith("http")) img else URI(baseUrl).resolve(img).toString()
                    )
                }

            PagedList(list = items, index = index, isLastPage = true)
        }
    }
}
