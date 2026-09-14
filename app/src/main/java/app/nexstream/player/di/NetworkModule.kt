package app.nexstream.player.di

import android.content.Context
import app.nexstream.player.data.remote.RecentlyWatchedApiService
import app.nexstream.player.data.remote.SportsApiService
import app.nexstream.player.data.remote.ThemeApiService
import app.nexstream.player.data.remote.WatchlistApiService
import app.nexstream.player.license.LicenceApiService
import app.nexstream.player.license.TrialApiService
import app.nexstream.player.subtitle.SubtitleApiService
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import app.nexstream.player.data.remote.ProgressApiService

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://example.com/") // Dummy base, actual URL set per request
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideWatchlistApiService(): WatchlistApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WatchlistApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLicenceApiService(): LicenceApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LicenceApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTrialApiService(): TrialApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrialApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSubtitleApiService(): SubtitleApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.subdl.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubtitleApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideThemeApiService(): ThemeApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ThemeApiService::class.java)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)   // use max 20% of app memory for images
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)  // 50MB disk cache
                    .build()
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))  // max 4 concurrent fetches
            .build()
    }

    @Provides
    @Singleton
    fun provideProgressApiService(): ProgressApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProgressApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRecentlyWatchedApiService(): RecentlyWatchedApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecentlyWatchedApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSportsApiService(): SportsApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SportsApiService::class.java)
    }

}