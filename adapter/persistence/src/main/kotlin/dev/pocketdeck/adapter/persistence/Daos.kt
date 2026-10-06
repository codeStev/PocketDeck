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
    @Query("SELECT * FROM owned_card")
    abstract fun observeAll(): Flow<List<OwnedCardEntity>>

    @Query("SELECT * FROM owned_card")
    abstract suspend fun getAll(): List<OwnedCardEntity>

    @Query("DELETE FROM owned_card")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insertAll(owned: List<OwnedCardEntity>)

    @Transaction
    open suspend fun replaceAll(owned: List<OwnedCardEntity>) {
        deleteAll()
        insertAll(owned)
    }
}

@Dao
internal abstract class BackupOwnedCardDao {
    @Query("SELECT * FROM owned_card_backup")
    abstract suspend fun getAll(): List<BackupOwnedCardEntity>

    @Query("SELECT COUNT(*) FROM owned_card_backup")
    abstract fun observeCount(): Flow<Int>

    @Query("DELETE FROM owned_card_backup")
    protected abstract suspend fun deleteAll()

    @Insert
    protected abstract suspend fun insertAll(rows: List<BackupOwnedCardEntity>)

    @Transaction
    open suspend fun replaceAll(rows: List<BackupOwnedCardEntity>) {
        deleteAll()
        insertAll(rows)
    }
}
