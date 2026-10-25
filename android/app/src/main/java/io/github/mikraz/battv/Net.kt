package io.github.mikraz.battv

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Un solo client HTTP per ponte interno e player: riusa le connessioni ed è più veloce nello zapping. */
object Net {
    const val UA = "Mozilla/5.0 (Linux; Android TV) BatTV"

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
