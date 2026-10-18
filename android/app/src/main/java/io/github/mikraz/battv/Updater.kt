package io.github.mikraz.battv

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Aggiornamenti: all'avvio guarda l'ultima Release su GitHub. Le Release si chiamano apk-N e
 * contengono la versione N+1 (vedi .github/workflows/apk.yml): se è più nuova di quella
 * installata chiede se installarla, la scarica e apre l'installazione di Android.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/mikraz/battv/releases/latest"
    private const val APK_NAME = "BatTV.apk"

    /** APK scaricato in attesa che l'utente permetta a BatTV di installare app */
    private var pending: File? = null
    private var asked = false

    fun check(a: Activity) {
        if (asked) return
        Thread {
            try {
                val req = Request.Builder().url(LATEST)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", Net.UA).build()
                val body = Net.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } ?: return@Thread
                val j = JSONObject(body)
                val n = Regex("^apk-(\\d+)$").find(j.optString("tag_name"))?.groupValues?.get(1)?.toLongOrNull() ?: return@Thread
                val remote = n + 1
                val local = PackageInfoCompat.getLongVersionCode(a.packageManager.getPackageInfo(a.packageName, 0))
                if (remote <= local) return@Thread
                val assets = j.optJSONArray("assets") ?: return@Thread
                var url: String? = null
                for (i in 0 until assets.length()) {
                    val o = assets.getJSONObject(i)
                    if (o.optString("name") == APK_NAME) url = o.optString("browser_download_url")
                }
                val u = url ?: return@Thread
                a.runOnUiThread { if (!a.isFinishing && !asked) ask(a, u) }
            } catch (_: Exception) {
                // Niente rete o GitHub non raggiungibile: si riprova al prossimo avvio
            }
        }.start()
    }

    private fun ask(a: Activity, url: String) {
        asked = true
        AlertDialog.Builder(a)
            .setTitle("Aggiornamento di BatTV")
            .setMessage("È disponibile una nuova versione. Vuoi installarla adesso?\n\nAccesso, preferiti e categorie restano come sono.")
            .setPositiveButton("Aggiorna") { _, _ -> download(a, url) }
            .setNegativeButton("Più tardi", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun download(a: Activity, url: String) {
        Toast.makeText(a, "Scarico l'aggiornamento…", Toast.LENGTH_LONG).show()
        Thread {
            try {
                // Prima di Android 7 l'installazione vuole un file leggibile dall'installer: uso la cache esterna
                val dir = File(if (Build.VERSION.SDK_INT < 24) a.externalCacheDir ?: a.cacheDir else a.cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, APK_NAME)
                val req = Request.Builder().url(url).header("User-Agent", Net.UA).build()
                Net.http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
                    f.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
                }
                a.runOnUiThread { install(a, f) }
            } catch (_: Exception) {
                a.runOnUiThread { Toast.makeText(a, "Download non riuscito: riprovo al prossimo avvio.", Toast.LENGTH_LONG).show() }
                asked = false
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            // La prima volta Android chiede di permettere a BatTV di installare app
            pending = f
            Toast.makeText(a, "Consenti a BatTV di installare app, poi torna indietro.", Toast.LENGTH_LONG).show()
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(a, "Apri Impostazioni › App › BatTV e attiva \"Installa app sconosciute\".", Toast.LENGTH_LONG).show()
            }
            return
        }
        pending = null
        val uri = if (Build.VERSION.SDK_INT >= 24) FileProvider.getUriForFile(a, "${a.packageName}.files", f) else Uri.fromFile(f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            a.startActivity(i)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(a, "Installazione non disponibile su questo dispositivo: usa Downloader.", Toast.LENGTH_LONG).show()
        }
    }

    /** Al ritorno dalle impostazioni, se il permesso ora c'è, riprende l'installazione */
    fun resume(a: Activity) {
        val f = pending ?: return
        if (Build.VERSION.SDK_INT < 26 || a.packageManager.canRequestPackageInstalls()) install(a, f)
    }
}
