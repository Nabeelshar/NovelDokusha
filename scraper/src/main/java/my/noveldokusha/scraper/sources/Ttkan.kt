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
 * Scraper for https://www.ttkan.co/
 *
 * Structure notes:
 * - Catalog pages: rank list at /novel/rank
 * - Book page: /novel/chapters/{novel_id} with metadata
 * - Chapter list: Loaded via AJAX at /api/nq/amp_novel_chapters?language=tw&novel_id={id}&limit={limit}
 * - Single chapter: /novel/user/page_direct?novel_id={id}&page={chapter_id}
 * - Content in `.content p` elements
 */
class Ttkan(
    private val networkClient: NetworkClient
) : SourceInterface.Catalog {
    override val id = "ttkan"
    override val nameStrId = R.string.source_name_ttkan
    override val baseUrl = "https://www.ttkan.co/"
    override val catalogUrl = "https://www.ttkan.co/novel/rank"
    override val language = LanguageCode.CHINESE

    override suspend fun getChapterTitle(doc: Document): String? =
        withContext(Dispatchers.Default) {
            doc.selectFirst(".title h1")?.text()?.trim()
                ?: doc.selectFirst("h1")?.text()?.trim()
        }

    override suspend fun getChapterText(doc: Document): String = withContext(Dispatchers.Default) {
        doc.selectFirst(".content")?.let { content ->
            // First remove the full chapter list sections (appears multiple times)
            content.select(".full_chapters").remove()
            
            // Remove bookmark anchors and their children before removing all anchors
            content.select("a.anchor_bookmark, a[href*=operation_v3], a[href*=bookmark]").remove()
            
            // Remove all non-text elements: scripts, ads, images, social sharing, feedback, SVG icons, etc.
            content.select(
                "script, style, " +
                ".ads_auto_place, .mobadsq, " +
                "amp-img, img, svg, " +
                "center, " +
                "#div_content_end, " +
                ".div_adhost, " +
                ".trc_related_container, " +
                ".div_feedback, " +
                ".social_share_frame, " +
                "amp-social-share, " +
                "a[href*=feedback], " +
                "button, " +
                ".icon, .decoration, " +
                ".next_page_links, " +
                ".more_recommend, " +
                "a"
            ).remove()
            
            // Extract only text from paragraphs
            content.select("p")
                .joinToString("\n\n") { it.text().trim() }
                .trim()
        } ?: ""
    }

    override suspend fun getBookCoverImageUrl(bookUrl: String): Response<String?> = withContext(Dispatchers.Default) {
        tryConnect {
            networkClient.get(bookUrl).toDocument("UTF-8")
                .selectFirst(".novel_info amp-img[src], .novel_info img[src]")
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
            networkClient.get(bookUrl).toDocument("UTF-8")
                .selectFirst(".description")
                ?.let { TextExtractor.get(it) }
        }
    }

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            // Extract novel ID from /novel/chapters/{novel_id}
            val novelId = bookUrl.substringAfter("/novel/chapters/").substringBefore("?").trim()
            
            // Use API endpoint to get all chapters
            // API returns JSON with chapter list
            val apiUrl = "https://www.ttkan.co/api/nq/amp_novel_chapters?language=tw&novel_id=$novelId"
            
            val response = networkClient.get(apiUrl)
            val jsonText = response.body.string()
            
            // Parse the JSON response manually to extract chapters
            // Format: {"items":[{"chapter_name":"第1章 ...","chapter_id":1},...]}
            val chapters = mutableListOf<ChapterResult>()
            
            // Simple regex-based JSON parsing for chapter data
            val itemPattern = """"chapter_name"\s*:\s*"([^"]+)"\s*,\s*"chapter_id"\s*:\s*(\d+)""".toRegex()
            var index = 1  // Start from 1 for chapter numbering
            itemPattern.findAll(jsonText).forEach { match ->
                val chapterName = match.groupValues[1]
                // Use sequential index instead of chapter_id from API
                // The chapter_id from API doesn't always match the URL pattern
                val chapterUrl = "https://www.ttkan.co/novel/pagea/${novelId}_${index}.html"
                
                chapters.add(
                    ChapterResult(
                        title = chapterName,
                        url = chapterUrl
                    )
                )
                index++
            }
            
            chapters
        }
    }

    override suspend fun getCatalogList(index: Int): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            // Catalog URL: /novel/rank
            val doc = networkClient.get(catalogUrl).toDocument("UTF-8")
            
            val items = doc.select(".rank_list > div")
                .chunked(2)  // Process in pairs: image div + info div
                .mapNotNull { pair ->
                    if (pair.size < 2) return@mapNotNull null
                    
                    val imageDiv = pair[0]
                    val infoDiv = pair[1]
                    
                    // Extract data from info div
                    val titleLink = infoDiv.selectFirst("ul li a[href*=/novel/chapters/] h2")
                        ?: return@mapNotNull null
                    val novelUrl = titleLink.parent()?.attr("href") ?: return@mapNotNull null
                    
                    // Extract cover image from image div
                    val imgElement = imageDiv.selectFirst("amp-img[src], img[src]")
                    val imgSrc = imgElement?.attr("src") ?: ""
                    
                    BookResult(
                        title = titleLink.text().trim(),
                        url = when {
                            novelUrl.startsWith("http") -> novelUrl
                            novelUrl.startsWith("/") -> URI(baseUrl).resolve(novelUrl).toString()
                            else -> "https://www.ttkan.co$novelUrl"
                        },
                        coverImageUrl = when {
                            imgSrc.startsWith("http") -> imgSrc
                            imgSrc.startsWith("/") -> URI(baseUrl).resolve(imgSrc).toString()
                            else -> ""
                        }
                    )
                }

            PagedList(list = items, index = index, isLastPage = true)
        }
    }

    override suspend fun getCatalogSearch(index: Int, input: String): Response<PagedList<BookResult>> = withContext(Dispatchers.Default) {
        tryConnect {
            if (input.isBlank())
                return@tryConnect PagedList.createEmpty(index = index)

            // Check if input is a direct URL to this source
            if (input.startsWith("http") && input.contains("ttkan.co")) {
                // Extract book info from the URL directly
                val bookUrl = when {
                    input.contains("/novel/chapters/") -> input
                    input.contains("/novel/user/page_direct?novel_id=") -> {
                        // Extract novel_id from chapter URL
                        val novelId = input.substringAfter("novel_id=").substringBefore("&")
                        "https://www.ttkan.co/novel/chapters/$novelId"
                    }
                    else -> input
                }
                
                // Fetch the book page to get title and cover
                val doc = networkClient.get(bookUrl).toDocument("UTF-8")
                
                // Try multiple selectors to find the title
                val title = doc.selectFirst(".novel_info h1")?.text()?.trim()
                    ?: doc.selectFirst("h1")?.text()?.trim()
                    ?: doc.selectFirst(".novel_info ul li h1")?.text()?.trim()
                    ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: "Unknown Novel"
                
                val coverImg = doc.selectFirst(".novel_info amp-img[src], .novel_info img[src]")?.attr("src")
                    ?: ""
                
                return@tryConnect PagedList(
                    list = listOf(
                        BookResult(
                            title = title,
                            url = bookUrl,
                            coverImageUrl = when {
                                coverImg.startsWith("http") -> coverImg
                                coverImg.startsWith("/") -> URI(baseUrl).resolve(coverImg).toString()
                                else -> ""
                            }
                        )
                    ),
                    index = index,
                    isLastPage = true
                )
            }

            // Search URL pattern: /novel/search?q={query}
            val encodedQuery = java.net.URLEncoder.encode(input, "UTF-8")
            val url = "https://www.ttkan.co/novel/search?q=$encodedQuery"

            val doc = networkClient.get(url).toDocument("UTF-8")
            
            // Search results use .novel_cell structure
            // Each .novel_cell contains an <a> with image and <ul> with title and metadata
            val items = doc.select(".novel_cell")
                .mapNotNull { cell ->
                    // Get the link and image (both use same href)
                    val link = cell.selectFirst("a[href*=/novel/chapters/]") ?: return@mapNotNull null
                    val novelUrl = link.attr("href")
                    
                    // Get title from h3 inside ul li a
                    val title = cell.selectFirst("ul li a h3")?.text()?.trim() 
                        ?: return@mapNotNull null
                    
                    // Get cover image
                    val imgElement = cell.selectFirst("amp-img[src], img[src]")
                    val imgSrc = imgElement?.attr("src") ?: ""
                    
                    BookResult(
                        title = title,
                        url = when {
                            novelUrl.startsWith("http") -> novelUrl
                            novelUrl.startsWith("/") -> URI(baseUrl).resolve(novelUrl).toString()
                            else -> "https://www.ttkan.co$novelUrl"
                        },
                        coverImageUrl = when {
                            imgSrc.startsWith("http") -> imgSrc
                            imgSrc.startsWith("/") -> URI(baseUrl).resolve(imgSrc).toString()
                            else -> ""
                        }
                    )
                }

            PagedList(list = items, index = index, isLastPage = true)
        }
    }
}
