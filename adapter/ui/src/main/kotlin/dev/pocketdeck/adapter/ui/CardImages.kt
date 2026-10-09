package dev.pocketdeck.adapter.ui

import android.content.Context
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.TimeUnit

private const val DISK_CACHE_BYTES = 250L * 1024 * 1024
private const val KEEP_FOR_DAYS = 30L
private const val TCGDEX_HOST = "assets.tcgdex.net"
private const val FULL_SIZE = "/high.webp"
private const val SMALL_SIZE = "/low.webp"

/**
 * Card pictures are fetched once and then kept on the phone. Their hosts send short cache lifetimes (GitHub's raw files say
 * five minutes) that would make the app ask again all the time, and card art never changes, so the lifetime is set here.
 * Call once at start-up.
 */
fun installCardImageCache(context: Context) {
    val app = context.applicationContext
    SingletonImageLoader.setSafe {
        coil3.ImageLoader
            .Builder(app)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { cardImageClient })) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(File(app.cacheDir, "card_images").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }.crossfade(true)
            .build()
    }
}

private val cardImageClient: OkHttpClient by lazy {
    OkHttpClient
        .Builder()
        .addNetworkInterceptor { chain ->
            chain
                .proceed(chain.request())
                .newBuilder()
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .header("Cache-Control", "max-age=${TimeUnit.DAYS.toSeconds(KEEP_FOR_DAYS)}")
                .build()
        }.build()
}

/**
 * The small version of a card picture for grids and lists. TCGdex serves the same picture as `low.webp` at about a third of the
 * bytes of `high.webp`; other hosts have one size only.
 */
internal fun thumbnailOf(url: String?): String? =
    if (url != null && TCGDEX_HOST in url && url.endsWith(FULL_SIZE)) url.removeSuffix(FULL_SIZE) + SMALL_SIZE else url
