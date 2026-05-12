package app.nexstream.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.nexstream.player.data.local.dao.ChannelDao
import app.nexstream.player.data.local.dao.MovieDao
import app.nexstream.player.data.local.dao.PlaylistDao
import app.nexstream.player.data.local.dao.ProfileDao
import app.nexstream.player.data.local.dao.ProgramDao
import app.nexstream.player.data.local.dao.RecentlyWatchedDao
import app.nexstream.player.data.local.dao.ReminderDao
import app.nexstream.player.data.local.dao.SeriesDao
import app.nexstream.player.data.local.dao.TmdbPosterDao
import app.nexstream.player.data.local.dao.WatchlistDao
import app.nexstream.player.data.local.dao.WatchProgressDao
import app.nexstream.player.data.local.entity.ChannelEntity
import app.nexstream.player.data.local.entity.EpisodeEntity
import app.nexstream.player.data.local.entity.MovieEntity
import app.nexstream.player.data.local.entity.PlaylistEntity
import app.nexstream.player.data.local.entity.ProfileCategoryFilter
import app.nexstream.player.data.local.entity.ProfileEntity
import app.nexstream.player.data.local.entity.ProgramEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedEntity
import app.nexstream.player.data.local.entity.RecentlyWatchedType
import app.nexstream.player.data.local.entity.ReminderEntity
import app.nexstream.player.data.local.entity.SeriesEntity
import app.nexstream.player.data.local.entity.TmdbPosterEntity
import app.nexstream.player.data.local.entity.WatchlistEntity
import app.nexstream.player.data.local.entity.WatchlistType
import app.nexstream.player.data.local.entity.WatchProgressEntity

class WatchlistTypeConverters {
    @TypeConverter fun fromWatchlistType(type: WatchlistType): String = type.name
    @TypeConverter fun toWatchlistType(value: String): WatchlistType = WatchlistType.valueOf(value)
}

class RecentlyWatchedTypeConverters {
    @TypeConverter fun fromType(type: RecentlyWatchedType): String = type.name
    @TypeConverter fun toType(value: String): RecentlyWatchedType = RecentlyWatchedType.valueOf(value)
}

@TypeConverters(WatchlistTypeConverters::class, RecentlyWatchedTypeConverters::class)
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        ProgramEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        WatchlistEntity::class,
        RecentlyWatchedEntity::class,
        ReminderEntity::class,
        ProfileEntity::class,
        ProfileCategoryFilter::class,
        WatchProgressEntity::class,
        TmdbPosterEntity::class
    ],
    version = 21,
    exportSchema = false
)
abstract class NexStreamDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun programDao(): ProgramDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun recentlyWatchedDao(): RecentlyWatchedDao
    abstract fun reminderDao(): ReminderDao
    abstract fun profileDao(): ProfileDao
    abstract fun watchProgressDao(): WatchProgressDao  // ← ADDED

    abstract fun tmdbPosterDao(): TmdbPosterDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE channels ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS watchlist (
                    id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL,
                    posterUrl TEXT, streamUrl TEXT, addedAt INTEGER NOT NULL DEFAULT 0)""")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE channels ADD COLUMN tvArchive INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE channels ADD COLUMN tvArchiveDuration INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE channels ADD COLUMN streamId TEXT")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS recently_watched (
                    id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, name TEXT NOT NULL,
                    subtitle TEXT, streamUrl TEXT NOT NULL, logoUrl TEXT, seriesId TEXT,
                    episodeId TEXT, movieId TEXT, watchedAt INTEGER NOT NULL DEFAULT 0)""")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_programs_channelId_startTime ON programs(channelId, startTime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_programs_channelId ON programs(channelId)")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS reminders (
                    id TEXT NOT NULL PRIMARY KEY, channelId TEXT NOT NULL,
                    channelName TEXT NOT NULL, streamUrl TEXT NOT NULL,
                    programTitle TEXT NOT NULL, startTime INTEGER NOT NULL)""")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS profiles_old (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL,
                    pinHash TEXT, isDefault INTEGER NOT NULL DEFAULT 0,
                    sortOrder INTEGER NOT NULL DEFAULT 0, updatedAt INTEGER NOT NULL DEFAULT 0)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS profile_category_filters_old (
                    profileId TEXT NOT NULL, categoryType TEXT NOT NULL, categoryName TEXT NOT NULL,
                    isAllowed INTEGER NOT NULL DEFAULT 1, updatedAt INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (profileId, categoryType, categoryName))""")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS profiles_old")
                db.execSQL("DROP TABLE IF EXISTS profile_category_filters_old")
                db.execSQL("DROP TABLE IF EXISTS profiles")
                db.execSQL("DROP TABLE IF EXISTS profile_category_filters")
                db.execSQL("""CREATE TABLE IF NOT EXISTS profiles (
                    id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, emoji TEXT NOT NULL,
                    pin_hash TEXT, is_default INTEGER NOT NULL DEFAULT 0,
                    sort_order INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS profile_category_filters (
                    profileId TEXT NOT NULL, categoryType TEXT NOT NULL, categoryName TEXT NOT NULL,
                    isAllowed INTEGER NOT NULL DEFAULT 1, updated_at INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (profileId, categoryType, categoryName))""")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watchlist ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("""CREATE TABLE IF NOT EXISTS watchlist_new (
                    id TEXT NOT NULL, profileId TEXT NOT NULL, type TEXT NOT NULL,
                    name TEXT NOT NULL, posterUrl TEXT, streamUrl TEXT,
                    addedAt INTEGER NOT NULL, PRIMARY KEY (id, profileId))""")
                db.execSQL("INSERT INTO watchlist_new SELECT id, profileId, type, name, posterUrl, streamUrl, addedAt FROM watchlist")
                db.execSQL("DROP TABLE watchlist")
                db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
                db.execSQL("ALTER TABLE recently_watched ADD COLUMN profileId TEXT NOT NULL DEFAULT 'default'")
                db.execSQL("""CREATE TABLE IF NOT EXISTS recently_watched_new (
                    id TEXT NOT NULL, profileId TEXT NOT NULL, type TEXT NOT NULL,
                    name TEXT NOT NULL, subtitle TEXT, streamUrl TEXT NOT NULL,
                    logoUrl TEXT, seriesId TEXT, episodeId TEXT, movieId TEXT,
                    watchedAt INTEGER NOT NULL, PRIMARY KEY (id, profileId))""")
                db.execSQL("INSERT INTO recently_watched_new SELECT id, profileId, type, name, subtitle, streamUrl, logoUrl, seriesId, episodeId, movieId, watchedAt FROM recently_watched")
                db.execSQL("DROP TABLE recently_watched")
                db.execSQL("ALTER TABLE recently_watched_new RENAME TO recently_watched")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE watchlist SET profileId = (SELECT id FROM profiles WHERE is_default = 1 LIMIT 1) WHERE profileId = 'default'")
                db.execSQL("UPDATE recently_watched SET profileId = (SELECT id FROM profiles WHERE is_default = 1 LIMIT 1) WHERE profileId = 'default'")
            }
        }
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profiles ADD COLUMN is_restricted INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_playlistId ON movies(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_categoryName ON movies(categoryName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movies_playlistId_categoryName ON movies(playlistId, categoryName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_playlistId ON series(playlistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_categoryName ON series(categoryName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_playlistId_categoryName ON series(playlistId, categoryName)")
            }
        }


        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS watch_progress (
                        profileId  TEXT NOT NULL,
                        itemId     TEXT NOT NULL,
                        itemType   TEXT NOT NULL,
                        seriesId   TEXT,
                        positionMs INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL DEFAULT 0,
                        updatedAt  INTEGER NOT NULL,
                        PRIMARY KEY(profileId, itemId)
                    )
                """.trimIndent())
                // Index names must match Room's auto-generated names from the entity
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_profileId ON watch_progress(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_itemId ON watch_progress(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_profileId_seriesId ON watch_progress(profileId, seriesId)")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_progress ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tmdb_poster_cache (title TEXT NOT NULL PRIMARY KEY, posterUrl TEXT NOT NULL, fetchedAt INTEGER NOT NULL DEFAULT 0)")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old two-column index
                database.execSQL("DROP INDEX IF EXISTS index_programs_channelId_startTime")
                // Add the new three-column index
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_programs_channelId_startTime_endTime " +
                            "ON programs(channelId, startTime, endTime)"
                )
            }
        }

        fun create(context: Context): NexStreamDatabase {
            return Room.databaseBuilder(
                context,
                NexStreamDatabase::class.java,
                "nexstream_database"
            )
                .addMigrations(
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21    // ← ADDED
                )
                .build()
        }
    }
}