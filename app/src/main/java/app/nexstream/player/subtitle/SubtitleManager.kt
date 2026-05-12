package app.nexstream.player.subtitle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

data class SubtitleLanguage(
    val code: String,
    val name: String,
    val sdId: Int,
    val url: String,
    val releaseName: String?
)

sealed class SubtitleResult {
    data class Success(val file: File) : SubtitleResult()
    data class Error(val message: String) : SubtitleResult()
}

@Singleton
class SubtitleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubtitleApiService,
    private val okHttpClient: OkHttpClient,
    private val subtitlePreferences: SubtitlePreferences
) {
    private val apiKey = "WiYmR2UjccqhIotiHO8-Hdvk5vOZCzLr"
    private val cacheDir = File(context.cacheDir, "subtitles").also { it.mkdirs() }

    suspend fun searchMovieSubtitles(
        title: String,
        year: String? = null,
        languages: String = "en"
    ): List<SubtitleLanguage> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = api.searchMovie(
                apiKey = apiKey,
                filmName = title,
                year = year,
                languages = languages,  // ← was hardcoded "en"
                subsPerPage = 30
            )
            response.subtitles?.map {
                SubtitleLanguage(
                    code = it.lang,
                    name = it.language,
                    sdId = it.sd_id,
                    url = it.url,
                    releaseName = it.release_name ?: it.full_name
                )
            }?.sortedBy { it.name } ?: emptyList()   // ← remove distinctBy so all results show
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "Search failed", e)
            emptyList()
        }
    }

    suspend fun searchEpisodeSubtitles(
        seriesName: String,
        seasonNumber: Int,
        episodeNumber: Int,
        languages: String = "en"        // ← add this parameter
    ): List<SubtitleLanguage> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = api.searchEpisode(
                apiKey = apiKey,
                filmName = seriesName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                languages = languages,  // ← was hardcoded "en"
                subsPerPage = 30
            )
            response.subtitles?.map {
                SubtitleLanguage(
                    code = it.lang,
                    name = it.language,
                    sdId = it.sd_id,
                    url = it.url,
                    releaseName = it.release_name ?: it.full_name
                )
            }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "Search failed", e)
            emptyList()
        }
    }

    suspend fun downloadSubtitle(
        subtitle: SubtitleLanguage,
        cacheKey: String
    ): SubtitleResult = withContext(Dispatchers.IO) {
        // Check cache first
        val cachedFile = File(cacheDir, "$cacheKey-${subtitle.code}.srt")
        if (cachedFile.exists()) {
            android.util.Log.d("SubtitleManager", "Using cached subtitle: ${cachedFile.name}")
            return@withContext SubtitleResult.Success(cachedFile)
        }

        return@withContext try {
            val downloadUrl = "https://dl.subdl.com${subtitle.url}"
            android.util.Log.d("SubtitleManager", "Downloading: $downloadUrl")

            val request = okhttp3.Request.Builder().url(downloadUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext SubtitleResult.Error("Download failed: ${response.code}")
            }

            val bytes = response.body?.bytes()
                ?: return@withContext SubtitleResult.Error("Empty response")

            // SubDL returns a zip file — extract the first .srt inside
            val srtFile = extractSrtFromZip(bytes, cachedFile)
                ?: return@withContext SubtitleResult.Error("No SRT found in zip")

            SubtitleResult.Success(srtFile)
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "Download failed", e)
            SubtitleResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun extractSrtFromZip(zipBytes: ByteArray, outputFile: File): File? {
        return try {
            ZipInputStream(zipBytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(".srt", ignoreCase = true)) {
                        outputFile.writeBytes(zip.readBytes())
                        android.util.Log.d("SubtitleManager", "Extracted: ${entry.name}")
                        return outputFile
                    }
                    entry = zip.nextEntry
                }
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("SubtitleManager", "Zip extraction failed", e)
            null
        }
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}