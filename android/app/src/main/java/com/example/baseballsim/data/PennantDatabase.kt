package com.example.baseballsim.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "working_state")
data class WorkingEntity(
    @PrimaryKey val id: Int = 1,
    val worldId: String, val generationId: String, val revision: Long,
    val operationId: String, val payloadHash: String, val payload: ByteArray,
    @Embedded(prefix = "timeline_") val timeline: TimelineColumns = TimelineColumns(),
)

@Entity(tableName = "completed_results", primaryKeys = ["worldId", "generationId", "seasonId", "gameId"])
data class ResultEntity(
    val worldId: String, val generationId: String, val seasonId: String, val gameId: String,
    val ordinal: Int, val payloadHash: String, val payload: ByteArray,
)

@Entity(tableName = "manual_slot")
data class SlotEntity(
    @PrimaryKey val id: Int = 1, val worldId: String, val payloadHash: String, val payload: ByteArray,
    @Embedded(prefix = "timeline_") val timeline: TimelineColumns = TimelineColumns(),
)

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
    @Query("SELECT EXISTS(SELECT 1 FROM generation_registry) OR EXISTS(SELECT 1 FROM manual_slot) OR EXISTS(SELECT 1 FROM completed_results) OR EXISTS(SELECT 1 FROM operation_receipts) OR EXISTS(SELECT 1 FROM blocked_diagnostics) OR EXISTS(SELECT 1 FROM annual_archives)")
    suspend fun hasSavedData(): Boolean
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHead(value: WorkingEntity)
    @Query("UPDATE working_state SET worldId=:newWorld, generationId=:newGeneration, revision=:newRevision, operationId=:operation, payloadHash=:hash, payload=:body WHERE id=1 AND worldId=:expectedWorld AND generationId=:expectedGeneration AND revision=:expectedRevision")
    suspend fun compareAndSetHead(expectedWorld: String, expectedGeneration: String, expectedRevision: Long,
        newWorld: String, newGeneration: String, newRevision: Long, operation: String, hash: String, body: ByteArray): Int
    @Query("UPDATE working_state SET timeline_version=:version, timeline_rootSeed=:rootSeed, timeline_year=:year, timeline_previous=:previous, timeline_seal=:seal WHERE id=1 AND worldId=:world AND generationId=:generation AND revision=:revision")
    suspend fun setTimeline(world: String, generation: String, revision: Long,
        version: Int, rootSeed: String?, year: Long?, previous: String?, seal: String?): Int
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
    @Query("SELECT * FROM annual_archives WHERE id=:id") suspend fun archive(id: String): AnnualArchiveEntity?
    @Query("SELECT * FROM annual_clubs WHERE archiveId=:id ORDER BY clubId") suspend fun annualClubs(id: String): List<AnnualClubEntity>
    @Query("SELECT * FROM annual_players WHERE archiveId=:id ORDER BY playerId") suspend fun annualPlayers(id: String): List<AnnualPlayerEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertArchive(value: AnnualArchiveEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAnnualClubs(rows: List<AnnualClubEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAnnualPlayers(rows: List<AnnualPlayerEntity>)
}

@Database(
    entities = [WorkingEntity::class, ResultEntity::class, SlotEntity::class, ReceiptEntity::class, DiagnosticEntity::class,
        GenerationEntity::class, AnnualArchiveEntity::class, AnnualClubEntity::class, AnnualPlayerEntity::class],
    version = 2, exportSchema = true,
)
abstract class PennantDatabase : RoomDatabase() {
    abstract fun pennantDao(): PennantDao
    companion object {
        const val FILE_NAME = "pennant-prototype.db"
        private val addAnnualHistory = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("working_state", "manual_slot")) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN timeline_version INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE $table ADD COLUMN timeline_rootSeed TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN timeline_year INTEGER")
                    db.execSQL("ALTER TABLE $table ADD COLUMN timeline_previous TEXT")
                    db.execSQL("ALTER TABLE $table ADD COLUMN timeline_seal TEXT")
                }
                db.execSQL("CREATE TABLE annual_archives (id TEXT NOT NULL PRIMARY KEY, worldId TEXT NOT NULL, rootSeed TEXT NOT NULL, year INTEGER NOT NULL, parent TEXT, summaryHash TEXT NOT NULL)")
                db.execSQL("CREATE TABLE annual_clubs (archiveId TEXT NOT NULL, clubId TEXT NOT NULL, played INTEGER NOT NULL, wins INTEGER NOT NULL, losses INTEGER NOT NULL, draws INTEGER NOT NULL, runsFor INTEGER NOT NULL, runsAgainst INTEGER NOT NULL, rank INTEGER, PRIMARY KEY(archiveId, clubId), FOREIGN KEY(archiveId) REFERENCES annual_archives(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
                db.execSQL("CREATE TABLE annual_players (archiveId TEXT NOT NULL, playerId TEXT NOT NULL, clubId TEXT NOT NULL, plateAppearances INTEGER NOT NULL, hits INTEGER NOT NULL, walks INTEGER NOT NULL, strikeouts INTEGER NOT NULL, homeRuns INTEGER NOT NULL, PRIMARY KEY(archiveId, playerId), FOREIGN KEY(archiveId) REFERENCES annual_archives(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
            }
        }
        fun open(context: Context, name: String = FILE_NAME): PennantDatabase =
            Room.databaseBuilder(context.applicationContext, PennantDatabase::class.java, name)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(addAnnualHistory)
                .build() // Unknown schema fails; deliberately no destructive migration fallback.
    }
}
