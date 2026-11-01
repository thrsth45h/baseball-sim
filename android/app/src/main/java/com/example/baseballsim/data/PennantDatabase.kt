package com.example.baseballsim.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "working_state")
data class WorkingEntity(
    @PrimaryKey val id: Int = 1,
    val worldId: String, val generationId: String, val revision: Long,
    val operationId: String, val payloadHash: String, val payload: ByteArray,
)

@Entity(tableName = "completed_results", primaryKeys = ["worldId", "generationId", "seasonId", "gameId"])
data class ResultEntity(
    val worldId: String, val generationId: String, val seasonId: String, val gameId: String,
    val ordinal: Int, val payloadHash: String, val payload: ByteArray,
)

@Entity(tableName = "manual_slot")
data class SlotEntity(@PrimaryKey val id: Int = 1, val worldId: String, val payloadHash: String, val payload: ByteArray)

@Entity(tableName = "operation_receipts")
data class ReceiptEntity(
    @PrimaryKey val operationId: String, val fingerprint: String,
    val worldId: String, val generationId: String, val revision: Long,
)

@Entity(tableName = "blocked_diagnostics", primaryKeys = ["worldId", "generationId"])
data class DiagnosticEntity(
    val worldId: String, val generationId: String, val payloadHash: String, val payload: ByteArray,
)

@Entity(tableName = "generation_registry")
data class GenerationEntity(@PrimaryKey val generationId: String, val worldId: String)

@Dao
interface PennantDao {
    @Query("SELECT * FROM working_state WHERE id = 1") suspend fun head(): WorkingEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHead(value: WorkingEntity)
    @Query("UPDATE working_state SET worldId=:newWorld, generationId=:newGeneration, revision=:newRevision, operationId=:operation, payloadHash=:hash, payload=:body WHERE id=1 AND worldId=:expectedWorld AND generationId=:expectedGeneration AND revision=:expectedRevision")
    suspend fun compareAndSetHead(expectedWorld: String, expectedGeneration: String, expectedRevision: Long,
        newWorld: String, newGeneration: String, newRevision: Long, operation: String, hash: String, body: ByteArray): Int
    @Query("SELECT * FROM completed_results WHERE worldId=:world AND generationId=:generation ORDER BY ordinal")
    suspend fun results(world: String, generation: String): List<ResultEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertResult(value: ResultEntity)
    @Query("DELETE FROM completed_results WHERE worldId=:world AND generationId=:generation")
    suspend fun deleteResults(world: String, generation: String)
    @Query("SELECT * FROM manual_slot WHERE id=1") suspend fun slot(): SlotEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putSlot(value: SlotEntity)
    @Query("SELECT * FROM operation_receipts WHERE operationId=:operation") suspend fun receipt(operation: String): ReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReceipt(value: ReceiptEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDiagnostic(value: DiagnosticEntity)
    @Query("SELECT COUNT(*) FROM blocked_diagnostics") suspend fun diagnosticCount(): Int
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGeneration(value: GenerationEntity)
}

@Database(entities = [WorkingEntity::class, ResultEntity::class, SlotEntity::class, ReceiptEntity::class, DiagnosticEntity::class, GenerationEntity::class], version = 1, exportSchema = true)
abstract class PennantDatabase : RoomDatabase() {
    abstract fun pennantDao(): PennantDao
    companion object {
        const val FILE_NAME = "pennant-prototype.db"
        fun open(context: Context, name: String = FILE_NAME): PennantDatabase =
            Room.databaseBuilder(context.applicationContext, PennantDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build() // Unknown schema fails; deliberately no destructive migration fallback.
    }
}
