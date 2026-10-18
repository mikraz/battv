package io.github.mikraz.battv

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/** Player a tutto schermo con ExoPlayer: legge HLS, TS, MP4 e MKV. Indietro chiude e torna alla lista. */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val RESULT_ERROR = 2
    }

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply {
            keepScreenOn = true
            controllerShowTimeoutMs = 3000
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
        setContentView(view)
        intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }?.let {
            Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()

        val http = OkHttpDataSource.Factory(Net.http).setUserAgent(Net.UA)
        val sources = DefaultMediaSourceFactory(this).setDataSourceFactory(DefaultDataSource.Factory(this, http))
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        val p = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(sources).build()

        val item = MediaItem.Builder().setUri(url).apply {
            if (Regex("\\.m3u8?(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Diretta rimasta indietro: riparte dal punto live invece di chiudere
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    p.seekToDefaultPosition(); p.prepare(); return
                }
                // Altrimenti torna alla pagina, che prova il server aggiornato dal DNS
                setResult(RESULT_ERROR)
                finish()
            }
        })
        p.setMediaItem(item)
        p.playWhenReady = true
        p.prepare()
        view.player = p
        view.requestFocus()
        player = p
    }

    override fun onStop() {
        super.onStop()
        view.player = null
        player?.release()
        player = null
    }
}
