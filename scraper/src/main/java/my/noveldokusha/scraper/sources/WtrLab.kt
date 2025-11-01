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
import my.noveldokusha.network.toJson
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.SourceInterface
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import org.jsoup.nodes.Document

/**
 * Novel main page (chapter list) example:
 * https://wtr-lab.com/en/serie-123/novel-name
 * Chapter url example:
 * https://wtr-lab.com/en/serie-123/novel-name/chapter-1
 */
class WtrLab(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "wtrlab"
    override val nameStrId = R.string.source_name_wtrlab
    override val baseUrl = "https://wtr-lab.com/"
    override val catalogUrl = "https://wtr-lab.com/en/library"
    override val language = LanguageCode.ENGLISH

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            // Extract from Next.js data
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html()
            if (nextData != null) {
                val titleMatch = Regex("\"title\":\"([^\"]+)\"").find(nextData)
                titleMatch?.groupValues?.get(1)
            } else {
                doc.selectFirst("h1, .chapter-title")?.text()
            }
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        // WtrLab uses API for chapter content
        val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html() ?: return@withContext ""
        
        // Extract raw_id and chapter number from URL or data
        val rawIdMatch = Regex("\"raw_id\":(\\d+)").find(nextData)
        val chapterMatch = Regex("/chapter-(\\d+)").find(doc.location())
        
        if (rawIdMatch != null && chapterMatch != null) {
            val rawId = rawIdMatch.groupValues[1]
            val chapterNo = chapterMatch.groupValues[1]
            
            try {
                val apiUrl = "$baseUrl/api/reader/get"
                val request = postRequest(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .postPayload {
                        add("language", "en")
                        add("raw_id", rawId)
                        add("chapter_no", chapterNo)
                    }
                
                val jsonResponse = networkClient.call(request).toJson()
                val bodyArray = jsonResponse.asJsonObject
                    .getAsJsonObject("data")
                    ?.getAsJsonObject("data")
                    ?.getAsJsonArray("body")
                
                if (bodyArray != null) {
                    return@withContext bodyArray.joinToString("") { 
                        "<p>${it.asString}</p>" 
                    }
                }
            } catch (e: Exception) {
                // Fallback to basic extraction
            }
        }
        
        // Fallback extraction from page
        doc.selectFirst(".chapter-content, .reading-content")?.let { element ->
            element.select("script").remove()
            element.html()
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html()
            
            if (nextData != null) {
                val imageMatch = Regex("\"image\":\"([^\"]+)\"").find(nextData)
                imageMatch?.groupValues?.get(1)
            } else {
                doc.selectFirst("img[src*=\"cover\"], .novel-cover img")?.attr("src")
            }
        }
    }

    override suspend fun getBookDescription(
        bookUrl: String
    ): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html()
            
            if (nextData != null) {
                val descMatch = Regex("\"description\":\"([^\"]+)\"").find(nextData)
                descMatch?.groupValues?.get(1)
            } else {
                doc.selectFirst(".description, .novel-synopsis")?.text()
            }
        }
    }

    override suspend fun getChapterList(
        bookUrl: String
    ): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            val doc = networkClient.get(bookUrl).toDocument()
            val nextData = doc.selectFirst("script#__NEXT_DATA__")?.html() ?: return@tryConnect emptyList()
            
            // Extract raw_id and chapter_count
            val rawIdMatch = Regex("\"raw_id\":(\\d+)").find(nextData)
            val chapterCountMatch = Regex("\"chapter_count\":(\\d+)").find(nextData)
            val slugMatch = Regex("\"slug\":\"([^\"]+)\"").find(nextData)
            
            if (rawIdMatch != null && chapterCountMatch != null && slugMatch != null) {
                val rawId = rawIdMatch.groupValues[1]
                val chapterCount = chapterCountMatch.groupValues[1].toInt()
                val slug = slugMatch.groupValues[1]
                
                (1..chapterCount).map { chapterNo ->
                    ChapterResult(
                        title = "Chapter $chapterNo",
                        url = "$baseUrl/en/serie-$rawId/$slug/chapter-$chapterNo"
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect("index=$index") {
            val page = index + 1
            val url = "$baseUrl/en/library?page=$page"

            val doc = networkClient.get(url).toDocument()
            val books = doc.select(".novel-item, .book-item").mapNotNull {
                val link = it.selectFirst("a[href*=\"/serie-\"]") ?: return@mapNotNull null
                val title = it.selectFirst(".title, .book-title")?.text() ?: link.text()
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

            val request = postRequest("$baseUrl/api/search")
                .addHeader("Content-Type", "application/json")
                .postPayload {
                    add("text", input)
                }

            val jsonResponse = networkClient.call(request).toJson()
            val dataArray = jsonResponse.asJsonObject.getAsJsonArray("data")
            
            val books = mutableListOf<BookResult>()
            dataArray?.forEach { item ->
                val novel = item.asJsonObject
                val data = novel.getAsJsonObject("data")
                val rawId = novel.get("raw_id").asInt
                val slug = novel.get("slug").asString
                val title = data.get("title").asString
                val author = data.get("author").asString
                val status = if (novel.get("status").asBoolean) "Ongoing" else "Completed"
                
                books.add(
                    BookResult(
                        title = title,
                        url = "$baseUrl/en/serie-$rawId/f$slug",
                        coverImageUrl = data.get("image")?.asString ?: "",
                        description = "Author: $author | Status: $status"
                    )
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
