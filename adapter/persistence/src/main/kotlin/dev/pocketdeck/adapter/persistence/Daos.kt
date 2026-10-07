package dev.pocketdeck.adapter.persistence

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class CardDao {
    @Query("SELECT * FROM card ORDER BY id")
    abstract fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM card ORDER BY id")
    abstract suspend fun getAll(): List<CardEntity>

    @Query("DELETE FROM card")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insertAll(cards: List<CardEntity>)

    @Transaction
    open suspend fun replaceAll(cards: List<CardEntity>) {
        deleteAll()
        insertAll(cards)
    }
}

@Dao
internal abstract class OwnedCardDao {
    @Query("SELECT * FROM owned_card WHERE profileId = :profileId")
    abstract fun observeAll(profileId: Long): Flow<List<OwnedCardEntity>>

    @Query("SELECT * FROM owned_card WHERE profileId = :profileId")
    abstract suspend fun getAll(profileId: Long): List<OwnedCardEntity>

    @Query("DELETE FROM owned_card WHERE profileId = :profileId")
    protected abstract suspend fun deleteAll(profileId: Long)

    @Insert
    protected abstract suspend fun insertAll(owned: List<OwnedCardEntity>)

    @Transaction
    open suspend fun replaceAll(
        profileId: Long,
        owned: List<OwnedCardEntity>,
    ) {
        deleteAll(profileId)
        insertAll(owned)
    }
}

@Dao
internal abstract class BackupOwnedCardDao {
    @Query("SELECT * FROM owned_card_backup WHERE profileId = :profileId")
    abstract suspend fun getAll(profileId: Long): List<BackupOwnedCardEntity>

    @Query("SELECT COUNT(*) FROM owned_card_backup WHERE profileId = :profileId")
    abstract fun observeCount(profileId: Long): Flow<Int>

    @Query("DELETE FROM owned_card_backup WHERE profileId = :profileId")
    protected abstract suspend fun deleteAll(profileId: Long)

    @Insert
    protected abstract suspend fun insertAll(rows: List<BackupOwnedCardEntity>)

    @Transaction
    open suspend fun replaceAll(
        profileId: Long,
        rows: List<BackupOwnedCardEntity>,
    ) {
        deleteAll(profileId)
        insertAll(rows)
    }
}

@Dao
internal abstract class ProfileDao {
    @Query("SELECT * FROM profile ORDER BY id")
    abstract fun observeAll(): Flow<List<ProfileEntity>>

    @Insert
    abstract suspend fun insert(profile: ProfileEntity): Long

    @Query("UPDATE profile SET name = :name WHERE id = :id")
    abstract suspend fun rename(
        id: Long,
        name: String,
    )

    @Query("DELETE FROM profile WHERE id = :id")
    protected abstract suspend fun deleteProfile(id: Long)

    @Query("DELETE FROM owned_card WHERE profileId = :id")
    protected abstract suspend fun deleteOwnedCards(id: Long)

    @Query("DELETE FROM owned_card_backup WHERE profileId = :id")
    protected abstract suspend fun deleteBackup(id: Long)

    /** One transaction, so a profile never outlives or loses part of its data. */
    @Transaction
    open suspend fun deleteWithCollection(id: Long) {
        deleteOwnedCards(id)
        deleteBackup(id)
        deleteProfile(id)
    }
}
