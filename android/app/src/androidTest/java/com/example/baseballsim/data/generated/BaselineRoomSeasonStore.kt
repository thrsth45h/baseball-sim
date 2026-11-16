package com.example.baseballsim.data

import androidx.room.withTransaction
import com.example.baseballsim.season.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** SQLite transaction is short: all simulation, replay and encoding are done before it. */
class BaselineRoomSeasonStore(
    val database: PennantDatabase,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val faults: StoreFaults = StoreFaults {},
) : SeasonStore {
    private val dao get() = database.pennantDao()
    private data class Raw(val head: WorkingEntity, val results: List<ResultEntity>, val receipt: ReceiptEntity?)
    private fun token(head: WorkingEntity) = StoreToken(head.worldId, head.generationId, head.revision)
    private suspend fun raw(): Raw? = withContext(io) { database.withTransaction {
        val head = dao.head()
        if (head == null) {
            if (dao.hasSavedData()) throw StoreCorruption("Saved records exist without a current head; data has been retained")
            null
        } else Raw(head, dao.results(head.worldId, head.generationId), dao.receipt(head.operationId))
    } }
    override suspend fun read(): StoredSeason? {
        val raw = raw() ?: return null
        return withContext(computation) {
            val receipt = raw.receipt
            if (receipt == null || receipt.worldId != raw.head.worldId || receipt.generationId != raw.head.generationId || receipt.revision != raw.head.revision || !validIdentifier(raw.head.operationId))
                throw StoreCorruption("Saved head and operation receipt disagree")
            val savedToken = try { token(raw.head) } catch (error: Exception) { throw StoreCorruption("Saved identity or revision is invalid", error) }
            val state = checkedState(raw.head.payload, raw.head.payloadHash)
            if (raw.results.size != state.results.size) throw StoreCorruption("Result ledger count differs from the saved frame")
            raw.results.zip(state.results).forEach { (row, result) ->
                val bytes = CheckpointCodec.encodeSummary(result)
                if (row.worldId != raw.head.worldId || row.generationId != raw.head.generationId || row.seasonId != result.game.fixture.seasonId ||
                    row.gameId != result.game.fixture.gameId || row.ordinal != result.game.fixture.ordinal || row.payloadHash != hash(bytes) || !row.payload.contentEquals(bytes)) {
                    throw StoreCorruption("Result ledger and saved frame disagree")
                }
            }
            StoredSeason(savedToken, state, raw.head.operationId)
        }
    }
    private fun operation(value: String) { require(validIdentifier(value)) }
    private fun fingerprint(action: String, expected: StoreToken?, bodyHash: String = ""): String =
        hash(listOf(action, expected?.worldId.orEmpty(), expected?.generationId.orEmpty(), expected?.revision?.toString().orEmpty(), bodyHash)
            .joinToString("\u0000").toByteArray(Charsets.UTF_8))
    private suspend fun duplicate(op: String, fingerprint: String): ReceiptEntity? {
        val prior = dao.receipt(op) ?: return null
        if (prior.fingerprint != fingerprint) throw StoreConflict("Operation ID was reused for another update")
        val head = dao.head() ?: throw StoreCorruption("Receipt exists without current state")
        if (token(head) != StoreToken(prior.worldId, prior.generationId, prior.revision) || head.operationId != prior.operationId)
            throw StoreConflict("Operation was superseded by another saved update")
        return prior
    }
    private fun requireToken(head: WorkingEntity?, expected: StoreToken) {
        if (head == null || token(head) != expected) throw StoreConflict("Saved generation or revision changed")
    }
    private fun resultRows(results: List<GameSummary>, world: String, generation: String): List<ResultEntity> = results.map { result ->
        val bytes = CheckpointCodec.encodeSummary(result)
        ResultEntity(world, generation, result.game.fixture.seasonId, result.game.fixture.gameId, result.game.fixture.ordinal, hash(bytes), bytes)
    }
    private suspend fun finishWrite(op: String, signature: String, head: WorkingEntity): ReceiptEntity {
        val receipt = ReceiptEntity(op, signature, head.worldId, head.generationId, head.revision)
        dao.insertReceipt(receipt)
        return receipt
    }
    private suspend fun boundRead(expected: StoreToken, operation: String): StoredSeason {
        val current = read() ?: throw StoreCorruption("Committed save disappeared")
        if (current.token != expected || current.operationId != operation)
            throw StoreConflict("Another update superseded this operation before acknowledgement")
        return current
    }
    private suspend fun afterCommit(receipt: ReceiptEntity): StoredSeason {
        faults.hit(StoreFaultPoint.AFTER_COMMIT)
        return boundRead(StoreToken(receipt.worldId, receipt.generationId, receipt.revision), receipt.operationId)
    }
    override suspend fun create(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken?, replacementConfirmed: Boolean): StoredSeason {
        operation(operationId)
        val initial = withContext(computation) { SeasonState.initial(masterSeed, selectedClubId) }
        val bytes = withContext(computation) { CheckpointCodec.encodeSeason(initial) }
        val bodyHash = hash(bytes); val signature = fingerprint("create", expected, bodyHash)
        val world = newId(); val generation = newId()
        StoreToken(world, generation, 0)
        val previous = read()
        faults.hit(StoreFaultPoint.BEFORE_TRANSACTION)
        val committed = withContext(io) { database.withTransaction {
            duplicate(operationId, signature) ?: run {
                val old = dao.head()
                if (old == null) {
                    if (expected != null) throw StoreConflict("Expected save no longer exists")
                } else {
                    if (!replacementConfirmed || expected == null) throw StoreConflict("Replacing a season requires explicit confirmation")
                    requireToken(old, expected)
                    if (previous?.token != expected) throw StoreConflict("Saved state changed before replacement")
                    if (requireNotNull(previous).state.status == SeasonStatus.BLOCKED) dao.insertDiagnostic(DiagnosticEntity(old.worldId, old.generationId, old.payloadHash, old.payload))
                }
                dao.insertGeneration(GenerationEntity(generation, world))
                val head = WorkingEntity(worldId = world, generationId = generation, revision = 0, operationId = operationId, payloadHash = bodyHash, payload = bytes)
                if (old == null) dao.insertHead(head) else {
                    check(dao.compareAndSetHead(old.worldId, old.generationId, old.revision, world, generation, 0, operationId, bodyHash, bytes) == 1)
                    dao.deleteResults(old.worldId, old.generationId)
                }
                faults.hit(StoreFaultPoint.AFTER_HEAD)
                finishWrite(operationId, signature, head)
            }
        } }
        return afterCommit(committed)
    }
    override suspend fun commit(expected: StoreToken, operationId: String, candidate: SeasonState): StoredSeason {
        operation(operationId)
        val bytes = /* STORE_BENCH_OPEN_0 */StoreBenchmarkTimers.measure("candidate_encode") { withContext(computation) { CheckpointCodec.encodeSeason(candidate) } }/* STORE_BENCH_CLOSE_0 */
        val bodyHash = hash(bytes); val signature = fingerprint("commit", expected, bodyHash)
        /* STORE_BENCH_OPEN_1 */StoreBenchmarkTimers.measure("candidate_check") { withContext(computation) { checkedState(bytes, bodyHash) } }/* STORE_BENCH_CLOSE_1 */
        val before = /* STORE_BENCH_OPEN_2 */StoreBenchmarkTimers.measure("before_read") { read() }/* STORE_BENCH_CLOSE_2 */ ?: throw StoreConflict("No active save")
        if (before.token != expected) {
            // A duplicate completed operation may be retried after its successful response was lost.
            val prior = withContext(io) { database.withTransaction { duplicate(operationId, signature) } }
            if (prior != null) return afterCommit(prior)
            throw StoreConflict("Saved generation or revision changed")
        }
        require(candidate.masterSeed == before.state.masterSeed && candidate.games == before.state.games)
        require(candidate.dayIndex >= before.state.dayIndex) { "Ordinary updates cannot rewind the calendar" }
        require(candidate.results.size >= before.state.results.size && candidate.results.take(before.state.results.size) == before.state.results)
        require(candidate.results.size <= before.state.results.size + 1) { "Commit at most one completed game at a time" }
        if (candidate.results.size == before.state.results.size + 1) {
            require(before.state.active != null) { "Persist the start marker before any pitch" }
            val accepted = before.state.accept(candidate.results.last())
            require(candidate.active == null && candidate.dayIndex == accepted.dayIndex) { "Result and day transition must be one canonical update" }
        }
        val priorActive = before.state.active
        val nextActive = candidate.active
        if (priorActive == null) {
            require(candidate.results.size == before.state.results.size) { "Persist the start marker before any pitch" }
            nextActive?.let { require(it.advanceCalls == 0L && it.snapshot == it.initial) { "A new start marker must be unadvanced" } }
        } else if (nextActive != null) {
            require(nextActive.game == priorActive.game && nextActive.advanceCalls >= priorActive.advanceCalls)
            if (nextActive.advanceCalls == priorActive.advanceCalls) require(nextActive == priorActive)
        } else {
            require(candidate.results.size == before.state.results.size + 1) { "An active match cannot silently disappear" }
        }
        if (before.state.status == SeasonStatus.BLOCKED) require(candidate.active == before.state.active && candidate.results == before.state.results)
        if (CheckpointCodec.encodeSeason(before.state).contentEquals(bytes)) {
            val prior = withContext(io) { database.withTransaction {
                duplicate(operationId, signature).also { if (it == null) requireToken(dao.head(), expected) }
            } }
            if (prior != null) return afterCommit(prior)
            faults.hit(StoreFaultPoint.AFTER_COMMIT)
            return boundRead(before.token, before.operationId)
        }
        val rows = withContext(computation) { resultRows(candidate.results.drop(before.state.results.size), expected.worldId, expected.generationId) }
        val revision = Math.addExact(expected.revision, 1)
        faults.hit(StoreFaultPoint.BEFORE_TRANSACTION)
        val committed = /* STORE_BENCH_OPEN_3 */StoreBenchmarkTimers.measure("transaction") { withContext(io) { database.withTransaction {
            duplicate(operationId, signature) ?: run {
                val old = dao.head(); requireToken(old, expected)
                rows.forEach { dao.insertResult(it) }
                faults.hit(StoreFaultPoint.AFTER_RESULTS)
                check(dao.compareAndSetHead(expected.worldId, expected.generationId, expected.revision,
                    expected.worldId, expected.generationId, revision, operationId, bodyHash, bytes) == 1)
                faults.hit(StoreFaultPoint.AFTER_HEAD)
                finishWrite(operationId, signature, WorkingEntity(worldId = expected.worldId, generationId = expected.generationId,
                    revision = revision, operationId = operationId, payloadHash = bodyHash, payload = bytes))
            }
        } } }/* STORE_BENCH_CLOSE_3 */
        return /* STORE_BENCH_OPEN_4 */StoreBenchmarkTimers.measure("after_commit") { afterCommit(committed) }/* STORE_BENCH_CLOSE_4 */
    }
    override suspend fun saveSlot(expected: StoreToken, operationId: String): StoredSeason {
        operation(operationId); val signature = fingerprint("save-slot", expected)
        val before = read() ?: throw StoreConflict("No active save")
        require(outsideMatch(before.state)) { "Manual save is disabled during a match" }
        faults.hit(StoreFaultPoint.BEFORE_TRANSACTION)
        val committed = withContext(io) { database.withTransaction {
            duplicate(operationId, signature) ?: run {
                val old = dao.head(); requireToken(old, expected); old!!
                dao.putSlot(SlotEntity(worldId = old.worldId, payloadHash = old.payloadHash, payload = old.payload))
                val revision = Math.addExact(expected.revision, 1)
                check(dao.compareAndSetHead(expected.worldId, expected.generationId, expected.revision, expected.worldId,
                    expected.generationId, revision, operationId, old.payloadHash, old.payload) == 1)
                faults.hit(StoreFaultPoint.AFTER_HEAD)
                finishWrite(operationId, signature, old.copy(revision = revision, operationId = operationId))
            }
        } }
        return afterCommit(committed)
    }
    override suspend fun loadSlot(expected: StoreToken, operationId: String): StoredSeason {
        operation(operationId); val signature = fingerprint("load-slot", expected)
        val before = read() ?: throw StoreConflict("No active save")
        require(outsideMatch(before.state)) { "Manual load is disabled during a match" }
        val slot = withContext(io) { dao.slot() } ?: throw StoreConflict("Manual slot is empty")
        val restored = withContext(computation) { checkedState(slot.payload, slot.payloadHash) }
        require(outsideMatch(restored))
        val generation = newId(); val revision = Math.addExact(expected.revision, 1)
        StoreToken(slot.worldId, generation, revision)
        val rows = withContext(computation) { resultRows(restored.results, slot.worldId, generation) }
        faults.hit(StoreFaultPoint.BEFORE_TRANSACTION)
        val committed = withContext(io) { database.withTransaction {
            duplicate(operationId, signature) ?: run {
                val old = dao.head(); requireToken(old, expected)
                val currentSlot = dao.slot() ?: throw StoreConflict("Manual slot disappeared")
                if (currentSlot.worldId != slot.worldId || currentSlot.payloadHash != slot.payloadHash || !currentSlot.payload.contentEquals(slot.payload))
                    throw StoreConflict("Manual slot changed")
                dao.insertGeneration(GenerationEntity(generation, slot.worldId))
                rows.forEach { dao.insertResult(it) }
                faults.hit(StoreFaultPoint.AFTER_RESULTS)
                check(dao.compareAndSetHead(expected.worldId, expected.generationId, expected.revision, slot.worldId,
                    generation, revision, operationId, slot.payloadHash, slot.payload) == 1)
                dao.deleteResults(expected.worldId, expected.generationId)
                faults.hit(StoreFaultPoint.AFTER_HEAD)
                finishWrite(operationId, signature, WorkingEntity(worldId = slot.worldId, generationId = generation, revision = revision,
                    operationId = operationId, payloadHash = slot.payloadHash, payload = slot.payload))
            }
        } }
        return afterCommit(committed)
    }
    override suspend fun hasSlot(): Boolean = withContext(io) { dao.slot() != null }
}
