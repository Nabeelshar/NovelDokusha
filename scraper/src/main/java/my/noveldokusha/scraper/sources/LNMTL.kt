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

/**
 * Novel main page (chapter list) example:
 * https://lnmtl.com/novel/example-novel
 * Chapter url example:
 * https://lnmtl.com/chapter/example-novel-chapter-1
 */
class LNMTL(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "lnmtl"
    override val nameStrId = R.string.source_name_lnmtl
    override val baseUrl = "https://lnmtl.com/"
    override val catalogUrl = "https://lnmtl.com/novel-list"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".chapter-title, h1")?.text()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.select(".chapter-body .translated").joinToString("\n") { element ->
            TextExtractor.get(element)
                .replace("\u00ad", "")
                .replace(Regex("\u201e[, ]*"), "&ldquo;")
                .replace(Regex("\u201d[, ]*"), "&rdquo;")
                .replace(Regex("[ ]*,[ ]+"), ", ")
                .trim()
        }.let { text ->
            text.split("\n").joinToString("") { "<p>$it</p>" }
        }
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst("img[title], .novel-cover img[src]")
                ?.attr("src")
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument()
                .selectFirst(".novel .description, .novel-synopsis")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val chapters = mutableListOf<ChapterResult>()
            val doc = networkClient.get(bookUrl).toDocument()
            
            // LNMTL uses JavaScript to load chapters via API
            // We need to extract volume IDs from the page script
            val scriptText = doc.select("script").firstOrNull { 
                it.html().contains("lnmtl.volumes") 
            }?.html() ?: ""
            
            // Parse volume IDs from script (simplified approach)
            val volumeIds = Regex("\"id\":(\\d+)").findAll(scriptText)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            
            // For each volume, fetch chapters from API
            volumeIds.forEach { volumeId ->
                var page = 1
                var hasMore = true
                
                while (hasMore) {
                    try {
                        val apiUrl = "$baseUrl/chapter?page=$page&volumeId=$volumeId"
                        val jsonDoc = networkClient.get(apiUrl).toDocument()
                        val jsonText = jsonDoc.body().text()
                        
                        // Parse chapter data from JSON response
                        val chapterMatches = Regex("\"site_url\":\"([^\"]+)\"").findAll(jsonText)
                        val titleMatches = Regex("\"title\":\"([^\"]*?)\"").findAll(jsonText)
                        val numberMatches = Regex("\"number\":(\\d+)").findAll(jsonText)
                        
                        val urls = chapterMatches.map { it.groupValues[1] }.toList()
                        val titles = titleMatches.map { it.groupValues[1] }.toList()
                        val numbers = numberMatches.map { it.groupValues[1] }.toList()
                        
                        if (urls.isEmpty()) {
                            hasMore = false
                            break
                        }
                        
                        urls.indices.forEach { i ->
                            val number = numbers.getOrNull(i) ?: ""
                            val title = titles.getOrNull(i) ?: ""
                            val fullTitle = if (number.isNotEmpty()) "#$number $title" else title
                            
                            chapters.add(
                                ChapterResult(
                                    title = fullTitle.trim(),
                                    url = urls[i]
                                )
                            )
                        }
                        
                        page++
                        if (urls.size < 100) hasMore = false
                    } catch (e: Exception) {
                        hasMore = false
                    }
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
            val url = "$baseUrl/novel-list?page=$page"

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".novel-list .novel-item, .media").mapNotNull {
                val link = it.selectFirst("a[href*=\"/novel/\"]") ?: return@mapNotNull null
                val title = it.selectFirst(".novel-title, .media-heading")?.text() ?: link.text()
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
                isLastPage = books.isEmpty() || doc.selectFirst(".pagination .next, a[rel=\"next\"]") == null
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

            val url = "$baseUrl/search?q=$input"

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".novel-list .novel-item, .media").mapNotNull {
                val link = it.selectFirst("a[href*=\"/novel/\"]") ?: return@mapNotNull null
                val title = it.selectFirst(".novel-title, .media-heading")?.text() ?: link.text()
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
