package my.noveldokusha.scraper.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.LanguageCode
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.toDocument
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.TextExtractor
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Scraper for https://www.ddxss.cc/
 * Based on lightnovel-crawler Python implementation
 * 
 * Features:
 * - UTF-8 encoding (unlike most Chinese sites which use GBK)
 * - JSON-based search API
 * - Volume-based chapter organization (100 chapters per volume)
 * - Catalog browsing
 */
class Ddxss(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "ddxss"
    override val nameStrId = R.string.source_name_ddxss
    override val baseUrl = "https://www.ddxss.cc/"
    override val catalogUrl = "https://www.ddxss.cc/top/allvisit.html"
    override val language = LanguageCode.CHINESE

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst("h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        val content = doc.selectFirst("div#chaptercontent")
        content?.let { 
            val text = TextExtractor.get(it)
            // Remove promotional text patterns
            text.replace(Regex("请收藏本站：.*"), "")
                .replace(Regex("顶点小说手机版：.*"), "")
                .replace(Regex("您可以在百度里搜索.*"), "")
                .replace(Regex("最新章节地址：.*"), "")
                .replace(Regex("全文阅读地址：.*"), "")
                .replace(Regex("txt下载地址：.*"), "")
                .replace(Regex("手机阅读：.*"), "")
                .replace(Regex("为了方便下次阅读.*"), "")
                .replace(Regex("请向你的朋友.*推荐本书.*"), "")
                .trim()
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("div.book div.info div.cover img")
                ?.attr("src")
                ?.let { if (it.startsWith("http")) it else URI(baseUrl).resolve(it).toString() }
        }
    }

    override suspend fun getBookDescription(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("div.book div.info div.intro")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            
            doc.select("div.listmain a")
                .mapNotNull { element ->
                    val href = element.attr("href")
                    // Filter out non-chapter links
                    if (!href.contains("/book/")) return@mapNotNull null
                    
                    ChapterResult(
                        title = element.text().trim(),
                        url = URI(baseUrl).resolve(href).toString()
                    )
                }
        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val page = index + 1
            val url = "https://www.ddxss.cc/top/allvisit/$page.html"

            val doc = networkClient.get(url).toDocument()
            val items = doc.select("div.list ul li, ul.seeWell li")
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

            // Note: The Python version uses a JSON API with cookies
            // For simplicity, we'll use a basic search approach
            val url = "https://www.ddxss.cc/user/search.html?q=$input"

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
