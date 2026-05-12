package app.nexstream.player.data.local.dao

import androidx.room.*
import app.nexstream.player.data.local.entity.ProfileCategoryFilter
import app.nexstream.player.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY sort_order ASC, is_default DESC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY sort_order ASC, is_default DESC")
    suspend fun getAllProfilesOnce(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE is_default = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id AND is_default = 0")
    suspend fun deleteNonDefaultProfile(id: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun forceDeleteProfile(id: String)

    // Category filters
    @Query("SELECT * FROM profile_category_filters WHERE profileId = :profileId")
    fun getFiltersForProfile(profileId: String): Flow<List<ProfileCategoryFilter>>

    @Query("SELECT * FROM profile_category_filters WHERE profileId = :profileId")
    suspend fun getFiltersForProfileOnce(profileId: String): List<ProfileCategoryFilter>

    @Query("SELECT * FROM profile_category_filters WHERE profileId = :profileId AND categoryType = :type")
    suspend fun getFiltersForProfileAndType(profileId: String, type: String): List<ProfileCategoryFilter>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilter(filter: ProfileCategoryFilter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFilters(filters: List<ProfileCategoryFilter>)

    @Query("DELETE FROM profile_category_filters WHERE profileId = :profileId")
    suspend fun deleteFiltersForProfile(profileId: String)

    @Query("DELETE FROM profile_category_filters WHERE profileId = :profileId AND categoryType = :type")
    suspend fun deleteFiltersForProfileAndType(profileId: String, type: String)

    @Query("DELETE FROM profile_category_filters WHERE profileId = :profileId AND categoryType = :type AND categoryName = :name")
    suspend fun deleteFilter(profileId: String, type: String, name: String)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    // Get allowed category names for a profile and content type
    // Returns null list when no filters exist (all allowed)
    @Query("SELECT categoryName FROM profile_category_filters WHERE profileId = :profileId AND categoryType = :type AND isAllowed = 0")
    suspend fun getBlockedCategories(profileId: String, type: String): List<String>

    @Query("SELECT categoryName FROM profile_category_filters WHERE profileId = :profileId AND categoryType = :type AND isAllowed = 0")
    fun getBlockedCategoriesFlow(profileId: String, type: String): Flow<List<String>>
}