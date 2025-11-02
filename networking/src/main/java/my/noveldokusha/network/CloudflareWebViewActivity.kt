package my.noveldokusha.network

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * Activity that displays a WebView for manual Cloudflare challenge solving.
 * The user can manually solve the captcha/challenge, and the activity will
 * close automatically once the cf_clearance cookie is detected.
 */
class CloudflareWebViewActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private val cookieManager = CookieManager.getInstance()

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_USER_AGENT = "user_agent"
        
        fun start(context: Context, url: String, userAgent: String) {
            val intent = Intent(context, CloudflareWebViewActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_USER_AGENT, userAgent)
                // Required for starting activity from non-activity context
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val url = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: "Mozilla/5.0"

        // Create WebView
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = userAgent
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true

            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // Check if Cloudflare challenge is solved
                    val cookies = cookieManager.getCookie(url ?: "")
                    if (cookies?.contains("cf_clearance") == true) {
                        // Challenge solved! Close the activity
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
            
            loadUrl(url)
        }

        // Create container and add WebView
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(webView)
        }
        
        setContentView(container)
        
        // Set title
        title = "Solve Cloudflare Challenge"
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
