package app.nexstream.player.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.nexstream.player.data.local.NexStreamDatabase
import app.nexstream.player.data.local.dao.TmdbPosterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS programs (
                    id TEXT PRIMARY KEY NOT NULL,
                    channelId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER NOT NULL,
                    category TEXT,
                    icon TEXT
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_programs_channelId ON programs(channelId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_programs_startTime ON programs(startTime)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS movies (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    streamUrl TEXT NOT NULL,
                    posterUrl TEXT,
                    backdropUrl TEXT,
                    plot TEXT,
                    cast TEXT,
                    director TEXT,
                    genre TEXT,
                    releaseDate TEXT,
                    rating TEXT,
                    duration TEXT,
                    categoryId TEXT,
                    categoryName TEXT,
                    playlistId TEXT NOT NULL,
                    isFavourite INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_playlistId ON movies(playlistId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_movies_categoryName ON movies(categoryName)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE playlists ADD COLUMN xtreamExpiry TEXT")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN lastPlayedPosition INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE movies ADD COLUMN lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0")
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS series (
                    id TEXT NOT NULL PRIMARY KEY,
                    seriesId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    posterUrl TEXT,
                    backdropUrl TEXT,
                    plot TEXT,
                    cast TEXT,
                    director TEXT,
                    genre TEXT,
                    releaseDate TEXT,
                    rating TEXT,
                    categoryId TEXT,
                    categoryName TEXT,
                    seasonCount INTEGER NOT NULL DEFAULT 0,
                    playlistId TEXT NOT NULL
                )
            """.trimIndent())
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS episodes (
                    id TEXT NOT NULL PRIMARY KEY,
                    episodeId TEXT NOT NULL,
                    seriesId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    seasonNum INTEGER NOT NULL,
                    episodeNum INTEGER NOT NULL,
                    streamUrl TEXT NOT NULL,
                    posterUrl TEXT,
                    plot TEXT,
                    duration TEXT,
                    containerExtension TEXT NOT NULL,
                    playlistId TEXT NOT NULL,
                    lastPlayedPosition INTEGER NOT NULL DEFAULT 0,
                    lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE channels ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
            try {
                database.execSQL("ALTER TABLE episodes ADD COLUMN posterUrl TEXT")
            } catch (e: Exception) { /* already exists on fresh installs */ }
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS watch_progress (
                    profileId  TEXT NOT NULL,
                    itemId     TEXT NOT NULL,
                    itemType   TEXT NOT NULL,
                    seriesId   TEXT,
                    positionMs INTEGER NOT NULL,
                    updatedAt  INTEGER NOT NULL,
                    PRIMARY KEY(profileId, itemId)
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_wp_profile ON watch_progress(profileId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_wp_item ON watch_progress(itemId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS idx_wp_series ON watch_progress(profileId, seriesId)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NexStreamDatabase {
        return Room.databaseBuilder(
            context,
            NexStreamDatabase::class.java,
            "nexstream_database"
        )
            // All migrations are defined in NexStreamDatabase companion object
            .addMigrations(
                NexStreamDatabase.MIGRATION_5_6,
                NexStreamDatabase.MIGRATION_6_7,
                NexStreamDatabase.MIGRATION_7_8,
                NexStreamDatabase.MIGRATION_8_9,
                NexStreamDatabase.MIGRATION_9_10,
                NexStreamDatabase.MIGRATION_10_11,
                NexStreamDatabase.MIGRATION_11_12,
                NexStreamDatabase.MIGRATION_12_13,
                NexStreamDatabase.MIGRATION_13_14,
                NexStreamDatabase.MIGRATION_14_15,
                NexStreamDatabase.MIGRATION_15_16,
                NexStreamDatabase.MIGRATION_16_17,
                NexStreamDatabase.MIGRATION_17_18,
                NexStreamDatabase.MIGRATION_18_19,
                NexStreamDatabase.MIGRATION_19_20,
                NexStreamDatabase.MIGRATION_20_21,
                NexStreamDatabase.MIGRATION_21_22,
                NexStreamDatabase.MIGRATION_22_23,
                NexStreamDatabase.MIGRATION_23_24,
                NexStreamDatabase.MIGRATION_24_25,
                NexStreamDatabase.MIGRATION_25_26,
                NexStreamDatabase.MIGRATION_26_27,
                NexStreamDatabase.MIGRATION_27_28,
                NexStreamDatabase.MIGRATION_28_29,
                NexStreamDatabase.MIGRATION_29_30,
                NexStreamDatabase.MIGRATION_30_31,
                NexStreamDatabase.MIGRATION_31_32,
                NexStreamDatabase.MIGRATION_32_33,
                NexStreamDatabase.MIGRATION_33_34,
                NexStreamDatabase.MIGRATION_34_35,
                NexStreamDatabase.MIGRATION_35_36,
                NexStreamDatabase.MIGRATION_36_37,
                NexStreamDatabase.MIGRATION_37_38
            )
            .build()
    }

    @Provides fun providePlaylistDao(db: NexStreamDatabase)  = db.playlistDao()
    @Provides fun provideChannelDao(db: NexStreamDatabase)   = db.channelDao()
    @Provides fun provideProgramDao(db: NexStreamDatabase)   = db.programDao()
    @Provides fun provideMovieDao(db: NexStreamDatabase)     = db.movieDao()
    @Provides fun provideSeriesDao(db: NexStreamDatabase)    = db.seriesDao()
    @Provides fun provideWatchlistDao(db: NexStreamDatabase) = db.watchlistDao()
    @Provides fun provideReminderDao(db: NexStreamDatabase)  = db.reminderDao()
    @Provides @Singleton fun provideProfileDao(db: NexStreamDatabase)      = db.profileDao()
    @Provides @Singleton fun provideWatchProgressDao(db: NexStreamDatabase) = db.watchProgressDao()
    @Provides @Singleton fun provideTmdbPosterDao(db: NexStreamDatabase) = db.tmdbPosterDao()
    @Provides fun provideRecentlyWatchedDao(db: NexStreamDatabase) = db.recentlyWatchedDao()
    @Provides fun provideMusicDao(db: NexStreamDatabase) = db.musicDao()
    @Provides @Singleton fun provideProfileAppearanceDao(db: NexStreamDatabase) = db.profileAppearanceDao()
    @Provides @Singleton fun provideChannelGroupDao(db: NexStreamDatabase) = db.channelGroupDao()
    @Provides @Singleton fun provideDeviceFolderDao(db: NexStreamDatabase) = db.deviceFolderDao()
}