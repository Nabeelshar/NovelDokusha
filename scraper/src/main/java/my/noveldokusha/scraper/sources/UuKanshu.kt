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
 * Scraper for https://www.uukanshu.net/
 * Based on lightnovel-crawler Python implementation
 * 
 * Features:
 * - Supports both www.uukanshu.net (Simplified Chinese) and tw.uukanshu.net (Traditional Chinese)
 * - Volume-based chapter organization
 * - Search functionality
 * - Catalog browsing
 */
class UuKanshu(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "uukanshu"
    override val nameStrId = R.string.source_name_uukanshu
    override val baseUrl = "https://www.uukanshu.net/"
    override val catalogUrl = "https://www.uukanshu.net/"
    override val language = LanguageCode.CHINESE

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        val content = doc.selectFirst("div#contentbox")
        content?.let { 
            // Format text: remove ads and extra whitespace
            val text = TextExtractor.get(it)
            text.replace(Regex("UU看书\\s*www\\.uukanshu\\.net"), "")
                .replace(Regex("一秒记住【UU看书\\s*www\\.uukanshu\\.net】，精彩小说无弹窗免费阅读！"), "")
                .trim()
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("dl.jieshao dt.jieshao-img img")
                ?.attr("src")
                ?.let { if (it.startsWith("http")) it else URI(baseUrl).resolve(it).toString() }
        }
    }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val synopsis = doc.selectFirst("dl.jieshao dd.jieshao_content h3")
                ?.let { TextExtractor.get(it) }
                ?: doc.selectFirst("dl.jieshao dd.jieshao_content h3 p")
                    ?.let { TextExtractor.get(it) }
            synopsis
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val chapters = mutableListOf<ChapterResult>()
            
            // Chapters are in reverse order (newest first), need to reverse
            val chapterElements = doc.select("ul#chapterList li").reversed()
            
            for (element in chapterElements) {
                // Skip volume headers
                if (element.hasClass("volume")) {
                    continue
                }
                
                val anchor = element.selectFirst("a") ?: continue
                chapters.add(
                    ChapterResult(
                        title = anchor.text().trim(),
                        url = URI(baseUrl).resolve(anchor.attr("href")).toString()
                    )
                )
            }
            
            chapters
        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val page = index + 1
            val url = "https://www.uukanshu.net/toplist/allvisit/$page.html"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("ul.seeWell li")
                .mapNotNull {
                    val link = it.selectFirst("a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("src") ?: ""
                    val title = it.selectFirst("h3")?.text()?.trim() ?: link.text().trim()
                    
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

            val url = "https://www.uukanshu.net/search.aspx?k=$input"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("ul.seeWell li, div.list ul li")
                .mapNotNull {
                    val link = it.selectFirst("a") ?: return@mapNotNull null
                    val img = it.selectFirst("img")?.attr("src") ?: ""
                    val title = it.selectFirst("h3")?.text()?.trim() ?: link.text().trim()
                    
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
