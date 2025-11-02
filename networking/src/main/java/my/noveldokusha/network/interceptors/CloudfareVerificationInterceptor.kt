package my.noveldokusha.network.interceptors

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import my.noveldokusha.core.domain.CloudfareVerificationBypassFailedException
import my.noveldokusha.core.domain.WebViewCookieManagerInitializationFailedException
import android.content.Intent
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

private const val TAG = "CloudflareInterceptor"

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
    ): Unit = withContext(Dispatchers.Default) {
        val url = request.url.toString()
        val domain = request.url.host

        Log.d(TAG, "Starting Cloudflare challenge resolution for: $url")
        Log.d(TAG, "Domain: $domain")

        // Launch existing WebViewActivity for user to manually solve the challenge
        withContext(Dispatchers.Main) {
            val intent = Intent().apply {
                setClassName(appContext, "my.noveldokusha.webview.WebViewActivity")
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }

        // Wait for the user to solve the challenge
        // Check for cf_clearance cookie in ANY domain
        val maxAttempts = 120 // 2 minutes timeout
        var attempts = 0
        var challengeSolved = false
        
        while (!challengeSolved && attempts < maxAttempts) {
            delay(1.seconds)
            
            // Flush cookies to ensure they're written
            cookieManager.flush()
            
            // Get ALL cookies from the page
            val allCookies = cookieManager.getCookie(url) ?: ""
            
            // Log cookies every 10 seconds for debugging
            if (attempts % 10 == 0) {
                Log.d(TAG, "Attempt $attempts/$maxAttempts - Cookies: ${allCookies.take(200)}")
            }
            
            if (allCookies.contains("cf_clearance")) {
                Log.d(TAG, "cf_clearance cookie found! Challenge solved.")
                challengeSolved = true
            }
            attempts++
        }
        
        if (!challengeSolved) {
            Log.w(TAG, "Challenge NOT solved after $attempts attempts")
        }
        
        // Give extra time for cookies to fully sync
        if (challengeSolved) {
            cookieManager.flush()
            delay(2.seconds)
        }
    }
}