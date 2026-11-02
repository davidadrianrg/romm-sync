package es.davidrg.rommsync.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import es.davidrg.rommsync.data.local.entity.RomPresentationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RomPresentationDao {

    @Query("SELECT * FROM rom_presentation")
    fun observeAll(): Flow<List<RomPresentationEntity>>

    @Query("SELECT * FROM rom_presentation WHERE romId = :romId")
    suspend fun get(romId: Int): RomPresentationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RomPresentationEntity)

    @Query("DELETE FROM rom_presentation WHERE romId = :romId")
    suspend fun delete(romId: Int)
}
