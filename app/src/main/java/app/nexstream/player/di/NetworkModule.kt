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
            .baseUrl("http://example.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideWatchlistApiService(okHttpClient: OkHttpClient): WatchlistApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WatchlistApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideLicenceApiService(okHttpClient: OkHttpClient): LicenceApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LicenceApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTrialApiService(okHttpClient: OkHttpClient): TrialApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrialApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSubtitleApiService(okHttpClient: OkHttpClient): SubtitleApiService {
        return Retrofit.Builder()
            .baseUrl("https://api.subdl.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SubtitleApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideThemeApiService(okHttpClient: OkHttpClient): ThemeApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
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
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .fetcherDispatcher(Dispatchers.IO.limitedParallelism(4))
            .build()
    }

    @Provides
    @Singleton
    fun provideProgressApiService(okHttpClient: OkHttpClient): ProgressApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ProgressApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRecentlyWatchedApiService(okHttpClient: OkHttpClient): RecentlyWatchedApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecentlyWatchedApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSportsApiService(okHttpClient: OkHttpClient): SportsApiService {
        return Retrofit.Builder()
            .baseUrl("https://nexstream.uk/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SportsApiService::class.java)
    }

}
