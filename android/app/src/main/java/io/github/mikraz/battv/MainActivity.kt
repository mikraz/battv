package io.github.mikraz.battv

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.WebViewAssetLoader
import okhttp3.Request
import java.io.ByteArrayInputStream

/**
 * BatTV per Android TV: mostra lo stesso player web della Tesla (index.html dentro l'app)
 * e aggiunge due cose che a una pagina web mancano:
 * - un ponte interno (https://appassets.androidplatform.net/proxy?url=...) che legge qualsiasi
 *   indirizzo, anche http e senza CORS, quindi niente ponte Cloudflare;
 * - il player nativo ExoPlayer al posto di quello del browser.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var lastBack = 0L

    private val assets by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    private val playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        // Il player restituisce il punto raggiunto e la durata (in ms), così la pagina ricorda l'episodio
        val pos = r.data?.getLongExtra(PlayerActivity.EXTRA_POS, 0L) ?: 0L
        val dur = r.data?.getLongExtra(PlayerActivity.EXTRA_DUR, -1L) ?: -1L
        val fn = if (r.resultCode == PlayerActivity.RESULT_ERROR) "battvNativeError" else "battvNativeClosed"
        js("window.$fn&&$fn($pos,$dur)")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "$userAgentString BatTV-AndroidTV"
        }
        web.setBackgroundColor(0xFF0F1216.toInt())
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.addJavascriptInterface(Bridge(), "BatTVNative")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                val u = req.url
                if (u.host == "appassets.androidplatform.net" && u.path == "/proxy") {
                    return proxy(u.getQueryParameter("url"), req.requestHeaders["Range"])
                }
                return assets.shouldInterceptRequest(u)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prima chiede alla pagina di chiudere quello che è aperto; se non c'è niente, esce con una doppia pressione
                web.evaluateJavascript("window.battvBack?battvBack():false") { res ->
                    if (res == "true") return@evaluateJavascript
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastBack < 2000) finish()
                    else {
                        lastBack = now
                        Toast.makeText(this@MainActivity, "Premi di nuovo Indietro per uscire", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        if (savedInstanceState == null) web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        else web.restoreState(savedInstanceState)
        web.requestFocus()

        // Controlla se su GitHub c'è una versione più nuova dell'app
        Updater.check(this)
    }

    override fun onResume() {
        super.onResume()
        Updater.resume(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    /** Il ponte interno: gira la richiesta al server e restituisce la risposta alla pagina. Gira già in un thread separato. */
    private fun proxy(target: String?, range: String?): WebResourceResponse {
        val cors = mutableMapOf("Access-Control-Allow-Origin" to "*")
        if (target == null || !(target.startsWith("http://") || target.startsWith("https://"))) {
            return WebResourceResponse("text/plain", "utf-8", 400, "Bad Request", cors + ("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
        }
        // La risposta viene letta tutta prima di passarla alla pagina: se la connessione si interrompe a metà
        // (succede con l'elenco dei film, oltre 10 MB) si riprova invece di consegnare un pezzo illeggibile.
        repeat(2) {
            try {
                val b = Request.Builder().url(target).header("User-Agent", Net.UA)
                if (range != null) b.header("Range", range)
                Net.http.newCall(b.build()).execute().use { r ->
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    val ct = r.header("Content-Type") ?: "application/octet-stream"
                    val mime = ct.substringBefore(';').trim()
                    val charset = Regex("charset=([^;]+)", RegexOption.IGNORE_CASE).find(ct)?.groupValues?.get(1)?.trim()
                    val h = cors.toMutableMap()
                    // Locandine e loghi restano in memoria per una settimana: tornando alla lista non si riscaricano.
                    // Gli elenchi invece si chiedono sempre freschi.
                    h["Cache-Control"] = if (mime.startsWith("image/") && r.isSuccessful) "max-age=604800" else "no-store"
                    r.header("Content-Range")?.let { h["Content-Range"] = it }
                    r.header("Accept-Ranges")?.let { h["Accept-Ranges"] = it }
                    // Android non accetta codici 3xx in questa risposta: i reindirizzamenti li segue già OkHttp
                    val code = if (r.code in 300..399) 502 else r.code
                    val reason = r.message.ifBlank { if (code < 400) "OK" else "Error" }
                    return WebResourceResponse(mime, charset, code, reason, h, ByteArrayInputStream(bytes))
                }
            } catch (_: Exception) {
                // connessione interrotta o server che non risponde: secondo tentativo, poi errore
            }
        }
        return WebResourceResponse("text/plain", "utf-8", 502, "Bad Gateway", cors + ("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }

    /** Funzioni che la pagina può chiamare come window.BatTVNative.* */
    inner class Bridge {
        @JavascriptInterface
        fun play(url: String, title: String, startMs: Double) = runOnUiThread {
            playerLauncher.launch(
                Intent(this@MainActivity, PlayerActivity::class.java)
                    .putExtra(PlayerActivity.EXTRA_URL, url)
                    .putExtra(PlayerActivity.EXTRA_TITLE, title)
                    .putExtra(PlayerActivity.EXTRA_START, startMs.toLong().coerceAtLeast(0L))
            )
        }

        /** true sul televisore (vista TV e telecomando), false su telefono e tablet (vista touch) */
        @JavascriptInterface
        fun isTv(): Boolean {
            val ui = getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager
            return ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
                packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        }

        @JavascriptInterface
        fun toast(msg: String) = runOnUiThread {
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
