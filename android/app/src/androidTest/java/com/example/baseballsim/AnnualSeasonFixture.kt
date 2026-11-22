package com.example.baseballsim

import android.content.Context
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.data.*
import com.example.baseballsim.season.CheckpointCodec
import com.example.baseballsim.season.SeasonState
import java.util.UUID
import java.util.zip.GZIPInputStream

internal const val completedV3FrameHash = "d255bd19f2a9de71439b82334968d96723bccd79cfe222afc512f09c67e3fc86"

/** A schema 1 fixture assembled independently from the actual PR #53 completed frame. */
internal fun installCompletedV3Fixture(context: Context, name: String) {
    val destination = context.getDatabasePath(name)
    check(!destination.exists())
    destination.parentFile!!.mkdirs()
    val packed = InstrumentationRegistry.getInstrumentation().context.assets
        .open("season-history/completed-v3.db.gz").use { it.readBytes() }
    check(hash(packed) == "05136adb14a7fc531c590a8f41377b919d67639c42ae53e771f946cd0c66986c")
    GZIPInputStream(packed.inputStream()).use { input -> destination.outputStream().use { input.copyTo(it) } }
}

/** Only fixture positioning uses direct writes. Observed tests continue through the production store. */
internal suspend fun replaceFixture(db: PennantDatabase, previous: StoredSeason, state: SeasonState, timeline: TimelineColumns): StoreToken =
    db.withTransaction {
        val dao = db.pennantDao()
        val world = previous.token.worldId
        val generation = UUID.randomUUID().toString()
        val operation = "fixture-$generation"
        val payload = CheckpointCodec.encodeSeason(state)
        val revision = previous.token.revision + 1
        dao.insertGeneration(GenerationEntity(generation, world))
        check(dao.compareAndSetHead(world, previous.token.generationId, previous.token.revision, world, generation,
            revision, operation, hash(payload), payload) == 1)
        check(dao.setTimeline(world, generation, revision, timeline.version, timeline.rootSeed, timeline.year, timeline.previous, timeline.seal) == 1)
        dao.deleteResults(world, previous.token.generationId)
        for (result in state.results) {
            val row = CheckpointCodec.encodeSummary(result)
            val game = result.game.fixture
            dao.insertResult(ResultEntity(world, generation, game.seasonId, game.gameId, game.ordinal, hash(row), row))
        }
        dao.insertReceipt(ReceiptEntity(operation, "fixture", world, generation, revision))
        StoreToken(world, generation, revision)
    }
