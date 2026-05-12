package app.nexstream.player

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Add this to your Application class (or make your Application class implement ImageLoaderFactory):
 *
 * class NexStreamApp : Application(), ImageLoaderFactory {
 *     override fun newImageLoader(): ImageLoader = CoilConfig.build(this)
 * }
 *
 * This fixes:
 * - DiskLruCache monitor contention (too many simultaneous requests)
 * - Memory pressure from full-size poster bitmaps
 * - OkHttp Http2Connection contention
 */
object CoilConfig {
    fun build(app: Application): ImageLoader {
        return ImageLoader.Builder(app)
            // Memory cache — 15% of available RAM, not the default 25%
            // Grid posters are small so this is sufficient
            .memoryCache {
                MemoryCache.Builder(app)
                    .maxSizePercent(0.20)
                    .build()
            }
            // Disk cache — 150MB dedicated to poster images
            .diskCache {
                DiskCache.Builder()
                    .directory(app.cacheDir.resolve("poster_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            // Custom OkHttpClient with limited parallelism
            // This is the key fix for DiskLruCache contention —
            // default Coil fires unlimited concurrent requests
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .dispatcher(okhttp3.Dispatcher().apply {
                        // 6 cols × ~3 visible rows = 18 cards max visible
                        // Allow enough concurrent requests to fill screen fast
                        maxRequestsPerHost = 6
                        maxRequests = 18
                    })
                    .build()
            }
            .crossfade(false)  // No crossfade — instant display from cache
            .build()
    }
}