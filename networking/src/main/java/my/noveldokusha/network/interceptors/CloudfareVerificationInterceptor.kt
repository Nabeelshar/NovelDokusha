package my.noveldokusha.network.interceptors

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import my.noveldokusha.core.domain.CloudfareVerificationBypassFailedException
import my.noveldokusha.core.domain.WebViewCookieManagerInitializationFailedException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

private val ERROR_CODES = listOf(
    HttpsURLConnection.HTTP_FORBIDDEN /*403*/,
    HttpsURLConnection.HTTP_UNAVAILABLE /*503*/
)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")

/**
 * If a CloudFare security verification redirection is detected, execute a
 * webView and retrieve the necessary headers.
 */
internal class CloudFareVerificationInterceptor(
    @ApplicationContext private val appContext: Context
) : Interceptor {

    private val lock = ReentrantLock()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (isNotCloudFare(response)) {
            return response
        }

        return lock.withLock {
            try {
                val cookieManager = CookieManager.getInstance()
                    ?: throw WebViewCookieManagerInitializationFailedException()

                response.close()
                // Remove old cf_clearance from the cookie
                val cookie = cookieManager
                    .getCookie(request.url.toString())
                    ?.splitToSequence(";")
                    ?.map { it.split("=").map(String::trim) }
                    ?.filter { it[0] != "cf_clearance" }
                    ?.joinToString(";") { it.joinToString("=") }

                cookieManager.setCookie(request.url.toString(), cookie)

                runBlocking(Dispatchers.IO) {
                    resolveWithWebView(request, cookieManager)
                }

                val responseCloudfare = chain.proceed(request)

                if (!isNotCloudFare(responseCloudfare)) {
                    throw CloudfareVerificationBypassFailedException()
                }

                responseCloudfare
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException(e.message, e.cause)
            }
        }
    }

    private fun isNotCloudFare(response: Response): Boolean {
        return response.code !in ERROR_CODES ||
                response.header("Server") !in SERVER_CHECK
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveWithWebView(
        request: Request,
        cookieManager: CookieManager
    ) = withContext(Dispatchers.Default) {
        val headers = request
            .headers
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }

        WebSettings.getDefaultUserAgent(appContext)

        withContext(Dispatchers.Main) {
            var challengeSolved = false
            val webView = WebView(appContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = request.header("user-agent")
                    ?: UserAgentInterceptor.DEFAULT_USER_AGENT
                // Increase memory to prevent crashes
                settings.setRenderPriority(WebSettings.RenderPriority.HIGH)

                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Check if challenge is solved by looking for cf_clearance cookie
                        val cookies = cookieManager.getCookie(url ?: "")
                        if (cookies?.contains("cf_clearance") == true) {
                            challengeSolved = true
                        }
                    }
                }
            }
            webView.loadUrl(request.url.toString(), headers)
            
            // Wait for challenge to be solved, with timeout
            val maxAttempts = 60 // 30 seconds total
            var attempts = 0
            while (!challengeSolved && attempts < maxAttempts) {
                delay(500) // Check every 500ms
                attempts++
            }
            
            // Give extra time for cookies to sync
            delay(1.seconds)
            
            webView.stopLoading()
            webView.destroy()
        }
    }
}