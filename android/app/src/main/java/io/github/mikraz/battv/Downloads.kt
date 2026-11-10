package io.github.mikraz.battv

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Film ed episodi scaricati per vederli senza connessione.
 * Il download lo fa il DownloadManager di Android (continua anche con l'app chiusa, mostra la notifica,
 * riprende se cade la rete); i file stanno nella cartella privata dell'app, quindi non servono permessi
 * e spariscono se l'app viene disinstallata. L'elenco (titolo, locandina, dati per ricordare l'episodio)
 * è salvato qui a fianco, perché senza rete la pagina non può chiedere nulla al provider.
 */
object Downloads {
    private fun prefs(c: Context) = c.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private fun dm(c: Context) = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun load(c: Context): JSONObject =
        try { JSONObject(prefs(c).getString("all", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }

    private fun save(c: Context, o: JSONObject) = prefs(c).edit().putString("all", o.toString()).apply()

    /** "ok", "exists" (già scaricato o in corso) oppure "err:motivo" */
    @Synchronized
    fun start(c: Context, key: String, title: String, logo: String, url: String, extra: String): String {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return "err:indirizzo non valido"
        var all = load(c)
        all.optJSONObject(key)?.let {
            if (status(c, it).first != "failed") return "exists"
            removeLocked(c, key)
            all = load(c)
        }
        val dir = c.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return "err:memoria non disponibile"
        if (dir.usableSpace < 300L * 1024 * 1024) return "err:spazio insufficiente"
        val ext = Regex("\\.([A-Za-z0-9]{2,4})$").find(Uri.parse(url).lastPathSegment ?: "")?.groupValues?.get(1) ?: "mp4"
        val file = File(dir, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + "." + ext)
        if (file.exists()) file.delete()
        val req = DownloadManager.Request(Uri.parse(url))
            .addRequestHeader("User-Agent", Net.UA)
            .setTitle(title)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        val id = try { dm(c).enqueue(req) } catch (e: Exception) { return "err:" + (e.message ?: "errore") }
        all.put(key, JSONObject()
            .put("id", id).put("title", title).put("logo", logo)
            .put("file", file.absolutePath).put("extra", extra).put("t", System.currentTimeMillis()))
        save(c, all)
        return "ok"
    }

    /** Stato, byte scaricati e byte totali di un download */
    private fun status(c: Context, o: JSONObject): Triple<String, Long, Long> {
        val file = File(o.optString("file"))
        if (o.optBoolean("ok") && file.exists()) return Triple("done", file.length(), file.length())
        var st = "failed"; var done = 0L; var total = -1L
        try {
            dm(c).query(DownloadManager.Query().setFilterById(o.optLong("id")))?.use { q ->
                if (q.moveToFirst()) {
                    done = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    total = q.getLong(q.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    st = when (q.getInt(q.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> if (file.exists() && file.length() > 0) "done" else "failed"
                        DownloadManager.STATUS_FAILED -> "failed"
                        DownloadManager.STATUS_RUNNING -> "running"
                        else -> "waiting" // in coda o in pausa (senza rete, in attesa del Wi-Fi)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Triple(st, done, total)
    }

    /** Elenco in JSON, dal più recente: key, title, logo, extra, state (running|waiting|done|failed), done, total, uri */
    @Synchronized
    fun list(c: Context): String {
        val all = load(c)
        var changed = false
        val rows = ArrayList<JSONObject>()
        for (key in all.keys()) {
            val o = all.getJSONObject(key)
            val (st, done, total) = status(c, o)
            if (st == "done" && !o.optBoolean("ok")) { o.put("ok", true); changed = true } // da qui in poi non serve più chiedere al sistema
            rows.add(JSONObject()
                .put("key", key).put("title", o.optString("title")).put("logo", o.optString("logo"))
                .put("extra", o.optString("extra")).put("state", st).put("done", done).put("total", total)
                .put("uri", Uri.fromFile(File(o.optString("file"))).toString()).put("t", o.optLong("t")))
        }
        if (changed) save(c, all)
        rows.sortByDescending { it.optLong("t") }
        return JSONArray(rows).toString()
    }

    @Synchronized
    fun remove(c: Context, key: String) = removeLocked(c, key)

    private fun removeLocked(c: Context, key: String) {
        val all = load(c)
        val o = all.optJSONObject(key) ?: return
        try { dm(c).remove(o.optLong("id")) } catch (_: Exception) {}
        File(o.optString("file")).delete()
        all.remove(key)
        save(c, all)
    }
}
