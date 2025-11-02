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
import java.net.URI
import java.net.URLEncoder

/**
 * Scraper for https://www.27k.net/ (LeYueDu / 乐阅读)
 * Based on lightnovel-crawler Python implementation
 * 
 * Features:
 * - Multiple domain support (27k.net, lreads.com)
 * - UTF-8 encoding
 * - Search functionality
 * - Volume-based chapter organization (100 chapters per volume)
 */
class LeYueDu(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "27k"
    override val nameStrId = R.string.source_name_27k
    override val baseUrl = "https://www.27k.net/"
    override val catalogUrl = "https://www.27k.net/top/allvisit.html"
    override val language = LanguageCode.CHINESE

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
            it.select("div.baocuo").remove()
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
            // Convert /book/70154.html to /read/70154/ for better synopsis
            val readUrl = bookUrl.replace("/book/", "/read/").replace(".html", "/")
            
            networkClient.get(readUrl).toDocument()
                .selectFirst("div.newnav div.ellipsis_2")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            // Convert /book/70154.html to /read/70154/ for chapter list
            val readUrl = bookUrl.replace("/book/", "/read/").replace(".html", "/")
            
            networkClient.get(readUrl).toDocument()
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
            val url = "https://www.27k.net/top/allvisit/$page.html"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("div.newbox ul li, ul.seeWell li")
                .mapNotNull {
                    val titleLink = it.selectFirst("h3 a:not([imgbox])")
                    val link = it.selectFirst("a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("src") ?: ""
                    
                    val title = titleLink?.text()?.trim() ?: link.text().trim()
                    
                    BookResult(
                        title = title,
                        url = URI(baseUrl).resolve(link.attr("href")).toString(),
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

            // Search using POST request
            val searchUrl = "https://so.27k.net/search/"
            val encodedQuery = URLEncoder.encode(input, "UTF-8")
            
            val request = postRequest(searchUrl)
                .postPayload {
                    add("searchkey", encodedQuery)
                    add("searchtype", "all")
                }
            
            val doc = networkClient.call(request).toDocument()
            
            val items = doc.select("div.newbox ul li")
                .mapNotNull {
                    val titleLink = it.selectFirst("h3 a:not([imgbox])")
                    val link = it.selectFirst("h3 a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("src") ?: ""
                    
                    val title = titleLink?.text()?.trim() ?: link.text().trim()
                    
                    BookResult(
                        title = title,
                        url = URI(baseUrl).resolve(link.attr("href")).toString(),
                        coverImageUrl = if (img.startsWith("http")) img else URI(baseUrl).resolve(img).toString()
                    )
                }

            PagedList(list = items, index = index, isLastPage = true)
        }
    }
}
