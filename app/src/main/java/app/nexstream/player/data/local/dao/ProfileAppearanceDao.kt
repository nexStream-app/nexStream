package app.nexstream.player.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.nexstream.player.data.local.entity.ProfileAppearanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileAppearanceDao {
    @Query("SELECT * FROM profile_appearance WHERE profileId = :profileId")
    suspend fun getAppearance(profileId: String): ProfileAppearanceEntity?

    @Query("SELECT * FROM profile_appearance WHERE profileId = :profileId")
    fun getAppearanceFlow(profileId: String): Flow<ProfileAppearanceEntity?>

    @Upsert
    suspend fun upsertAppearance(appearance: ProfileAppearanceEntity)

    @Query("DELETE FROM profile_appearance WHERE profileId = :profileId")
    suspend fun deleteAppearance(profileId: String)
}
