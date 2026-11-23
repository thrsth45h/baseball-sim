package com.example.baseballsim

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.baseballsim.core.GameEvent
import com.example.baseballsim.core.GameSession
import com.example.baseballsim.core.GameSnapshot
import com.example.baseballsim.core.Status
import com.example.baseballsim.data.*
import com.example.baseballsim.season.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PennantCommand { CREATE, SELECT, PITCH, PLATE_APPEARANCE, GAME, OPEN_SELECTED, DAY, SEASON, SAVE, LOAD, NEXT_YEAR }

/** The UI receives only values returned after durable commit, never a mutable game engine. */
interface PennantActions {
    suspend fun read(): StoredSeason?
    suspend fun hasSlot(): Boolean
    suspend fun applyLineup(expected: StoreToken, clubId: String, players: List<String>): StoredSeason =
        throw UnsupportedOperationException("This action source does not edit lineups")
    suspend fun nextYear(expected: StoreToken): StoredSeason =
        throw UnsupportedOperationException("This action source does not advance years")
    suspend fun openOwnGame(expected: StoreToken, gameId: String, stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason =
        throw UnsupportedOperationException("This action source does not open a specific fixture")
    suspend fun advanceVisibleDay(expected: StoreToken, dayIndex: Int, stop: () -> Boolean,
        progress: suspend (StoredSeason) -> Unit): StoredSeason = throw UnsupportedOperationException("This action source does not advance a specific date")
    suspend fun historyYears(expected: StoreToken): List<Long> = emptyList()
    suspend fun historyYear(expected: StoreToken, year: Long): SeasonYear =
        throw UnsupportedOperationException("This action source does not read annual history")
    suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
        stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason
}

class DurablePennantActions(private val coordinator: SeasonCoordinator) : PennantActions {
    override suspend fun read() = coordinator.read()
    override suspend fun hasSlot() = coordinator.hasSlot()
    override suspend fun applyLineup(expected: StoreToken, clubId: String, players: List<String>) = coordinator.applyLineup(expected, clubId, players)
    override suspend fun nextYear(expected: StoreToken) = coordinator.nextYear(expected)
    override suspend fun openOwnGame(expected: StoreToken, gameId: String, stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit) =
        coordinator.openOwnGame(expected, gameId, stop, progress)
    override suspend fun advanceVisibleDay(expected: StoreToken, dayIndex: Int, stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit) =
        coordinator.advanceVisibleDay(expected, dayIndex, stop, progress)
    override suspend fun historyYears(expected: StoreToken) = coordinator.historyYears(expected)
    override suspend fun historyYear(expected: StoreToken, year: Long) = coordinator.historyYear(expected, year)
    override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
        stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason = when (command) {
        PennantCommand.CREATE -> coordinator.createBatterProfile(seed, club, replacement)
        PennantCommand.SELECT -> coordinator.selectClub(club)
        PennantCommand.PITCH -> coordinator.advance(SeasonAdvance.PITCH)
        PennantCommand.PLATE_APPEARANCE -> coordinator.advance(SeasonAdvance.PLATE_APPEARANCE)
        PennantCommand.GAME -> coordinator.advance(SeasonAdvance.GAME)
        PennantCommand.OPEN_SELECTED -> coordinator.openNextSelected(stop, progress)
        PennantCommand.DAY -> coordinator.advanceDay(stop, progress)
        PennantCommand.SEASON -> coordinator.finishSeason(stop, progress)
        PennantCommand.SAVE -> coordinator.saveSlot()
        PennantCommand.LOAD -> coordinator.loadSlot()
        PennantCommand.NEXT_YEAR -> error("Annual progression requires a confirmed save token")
    }
}

/** Process owner: rotation does not close or replace the database. */
internal object PennantRuntime {
    @Volatile private var actions: PennantActions? = null
    fun actions(context: Context): PennantActions = actions ?: synchronized(this) {
        actions ?: DurablePennantActions(SeasonCoordinator(RoomSeasonStore(PennantDatabase.open(context.applicationContext))))
            .also { actions = it }
    }
}

data class PennantDisplay(val stored: StoredSeason, val fixture: Fixture?, val snapshot: GameSnapshot?, val recent: List<String>)

internal fun pennantDisplay(stored: StoredSeason): PennantDisplay {
    val state = stored.state
    val active = state.active
    if (active != null) {
        if (active.snapshot.status == Status.ABORTED) {
            return PennantDisplay(stored, active.game.fixture, active.snapshot.toCore(), listOf("未完了の試合を保持しています"))
        }
        val replay = active.recover(state.masterSeed)
        return PennantDisplay(stored, active.game.fixture, replay.snapshot, replay.events.takeLast(12).mapNotNull(::pennantEvent))
    }
    val last = state.results.lastOrNull() ?: return PennantDisplay(stored, null, null, emptyList())
    // The prototype intentionally stores summaries, not every old pitch log. Reconstruct only
    // the one displayed game and check it before showing its recent plays.
    val replay = last.replay()
    return PennantDisplay(stored, last.game.fixture, replay.snapshot, replay.events.takeLast(12).mapNotNull(::pennantEvent))
}

private fun pennantEvent(event: GameEvent): String? = when {
    event.pitchEvent != null -> when (event.pitchEvent) {
        "called_ball" -> "ボール"; "called_strike_caught" -> "見逃しストライク"
        "swinging_strike_caught" -> "空振り"; "ordinary_foul_uncaught" -> "ファウル"
        "fair_ball_handoff" -> "フェア"; "bunt_foul_uncaught" -> "バントファウル"
        "foul_fly_caught" -> "ファウル捕球"; "foul_tip_caught" -> "ファウルチップ"; else -> null
    }?.let { "${event.pitchesAccepted}球目 · $it" }
    event.kind == "plate_appearance_completed" -> when (event.category) {
        "walk" -> "四球"; "strikeout" -> "三振"; "home_run" -> "本塁打"
        "safe_hit" -> "セーフ安打"; "out_hold" -> "打者アウト"; else -> "打席終了"
    } + if (event.runsAdded > 0) " · ${event.runsAdded}得点" else ""
    event.kind == "game_end" -> "試合終了"
    else -> null
}

data class PennantUiState(
    val seedInput: String = "42", val selectedClubInput: String = "L1-T01",
    val display: PennantDisplay? = null, val busy: Boolean = false, val bulk: Boolean = false,
    val stopRequested: Boolean = false, val hasSlot: Boolean = false, val loaded: Boolean = false,
    val seedError: String? = null, val error: String? = null, val notice: String? = null,
    val resultDay: Int? = null, val openedGameId: String? = null,
) {
    val season get() = display?.stored?.state
    val visibleDay get() = resultDay ?: season?.dayIndex
    val canUse get() = loaded && !busy && error == null
    val canProgress get() = canUse && season?.status == SeasonStatus.IN_PROGRESS
    val canAdvanceMatch get() = canProgress && season?.active?.snapshot?.status == Status.IN_PROGRESS
    val canSave get() = canUse && season != null && season?.active == null
    val canLoad get() = canSave && hasSlot
    val canEditLineup get() = canUse && season?.lineupEnabled == true && season?.status == SeasonStatus.IN_PROGRESS && season?.active == null
    val timeline get() = display?.stored?.timeline
    val canAdvanceYear get() = canUse && timeline != null && season?.status == SeasonStatus.COMPLETE
}

class PennantViewModel(private val actions: PennantActions,
    private val computation: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val lock = Any()
    private val mutable = MutableStateFlow(PennantUiState())
    val state: StateFlow<PennantUiState> = mutable.asStateFlow()
    private val stopping = AtomicBoolean(false)
    private var generation = 0L
    private var cleared = false
    init { reload() }

    fun changeSeed(value: String) = synchronized(lock) {
        if (!cleared && !mutable.value.busy) mutable.value = mutable.value.copy(seedInput = value, seedError = null)
    }
    fun selectClub(id: String) {
        if (mutable.value.season == null) synchronized(lock) {
            if (!cleared && mutable.value.canUse) { PennantSchedule.club(id); mutable.value = mutable.value.copy(selectedClubInput = id) }
        } else execute(PennantCommand.SELECT, club = id)
    }
    fun requestStop() = synchronized(lock) {
        if (!cleared && mutable.value.busy && mutable.value.bulk) {
            stopping.set(true); mutable.value = mutable.value.copy(stopRequested = true)
        }
    }
    private fun claim(bulk: Boolean, allowError: Boolean = false): Long? = synchronized(lock) {
        if (cleared || mutable.value.busy || (!allowError && (!mutable.value.loaded || mutable.value.error != null))) return null
        generation = Math.addExact(generation, 1)
        stopping.set(false)
        mutable.value = mutable.value.copy(busy = true, bulk = bulk, stopRequested = false, error = null, notice = null, openedGameId = null)
        generation
    }
    private fun current(token: Long) = !cleared && generation == token
    private fun publish(token: Long, value: PennantDisplay?, slot: Boolean? = null, finished: Boolean = false,
        inputs: Boolean = false, notice: String? = null, finishingDay: Int? = null, openedGameId: String? = null) = synchronized(lock) {
        if (!current(token)) return@synchronized
        val old = mutable.value
        val previous = old.display?.stored?.token
        val saved = value?.stored
        val sameSave = saved != null && previous != null && saved.token.worldId == previous.worldId &&
            saved.token.generationId == previous.generationId
        // The engine jumps to the next calendar day when its last result commits. Keep the
        // finished day's cards even on intermediate publications while the command is still busy.
        val retainedDay = if (saved != null && sameSave) (finishingDay ?: old.resultDay)?.takeIf { day ->
            saved.state.dayIndex == day + 1 && saved.state.results.lastOrNull()?.game?.fixture?.dayIndex == day
        } else null
        mutable.value = old.copy(display = value, hasSlot = slot ?: old.hasSlot,
            seedInput = if (inputs && value != null) value.stored.timeline?.rootSeed ?: value.stored.state.masterSeed else old.seedInput,
            selectedClubInput = value?.stored?.state?.selectedClubId ?: old.selectedClubInput,
            seedError = if (inputs && value != null) null else old.seedError,
            loaded = true, busy = !finished, bulk = if (finished) false else old.bulk,
            stopRequested = if (finished) false else old.stopRequested, error = null, notice = notice, resultDay = retainedDay,
            openedGameId = openedGameId?.takeIf { sameSave && saved?.state?.active?.game?.fixture?.gameId == it })
    }
    fun reload() {
        val token = claim(false, allowError = true) ?: return
        viewModelScope.launch {
            try {
                val (display, slot) = withContext(computation) { actions.read()?.let(::pennantDisplay) to actions.hasSlot() }
                publish(token, display, slot, finished = true, inputs = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed(token) }
        }
    }
    fun advanceVisibleDay(expected: StoreToken, day: Int): Boolean = synchronized(lock) {
        val before = mutable.value
        if (!before.canUse || before.display?.stored?.token != expected || before.visibleDay != day) return@synchronized false
        if (before.resultDay != null) {
            mutable.value = before.copy(resultDay = null, openedGameId = null)
            return@synchronized true
        }
        if (!before.canProgress) return@synchronized false
        dispatch(PennantCommand.DAY, before, false, before.selectedClubInput, expected, dayIndex = day)
    }
    fun openOwnGame(expected: StoreToken, gameId: String): Boolean = synchronized(lock) {
        val before = mutable.value
        if (!before.canUse || before.display?.stored?.token != expected) return@synchronized false
        val season = before.season ?: return@synchronized false
        val fixture = season.games.firstOrNull { it.fixture.gameId == gameId }?.fixture ?: return@synchronized false
        if (fixture.dayIndex != before.visibleDay || season.selectedClubId !in listOf(fixture.awayClubId, fixture.homeClubId)) {
            return@synchronized false
        }
        if (fixture.ordinal <= season.results.size || season.active?.game?.fixture?.gameId == gameId) {
            mutable.value = before.copy(openedGameId = gameId)
            return@synchronized true
        }
        if (!before.canProgress || before.resultDay != null) return@synchronized false
        dispatch(PennantCommand.OPEN_SELECTED, before, false, before.selectedClubInput, expected, gameId)
    }
    fun consumeOpenedGame(expected: StoreToken, gameId: String): Boolean = synchronized(lock) {
        val before = mutable.value
        if (!before.canUse || before.display?.stored?.token != expected || before.openedGameId != gameId) return@synchronized false
        mutable.value = before.copy(openedGameId = null)
        true
    }
    fun execute(command: PennantCommand, replacementConfirmed: Boolean = false, club: String = mutable.value.selectedClubInput,
        confirmationToken: StoreToken? = null): Unit = synchronized(lock) {
        val before = mutable.value
        // Compare with the latest published state while claiming the operation under the same lock.
        // This intentionally makes no promise about DB changes not yet published to this ViewModel.
        if (confirmationToken != null && confirmationToken != before.display?.stored?.token) return@synchronized
        if (command == PennantCommand.CREATE) {
            if (before.busy || !before.canUse) return@synchronized
            if (MatchViewModel.validatedSeed(before.seedInput) == null) {
                if (!cleared && !mutable.value.busy) mutable.value = mutable.value.copy(seedError = MatchViewModel.SEED_ERROR)
                return@synchronized
            }
            if (before.season != null && !replacementConfirmed) return@synchronized
        } else if (command == PennantCommand.SELECT) { if (!before.canUse || before.season == null) return@synchronized }
        else if (command == PennantCommand.SAVE) { if (!before.canSave) return@synchronized }
        else if (command == PennantCommand.LOAD) { if (!before.canLoad) return@synchronized }
        else if (command == PennantCommand.NEXT_YEAR) { if (!before.canAdvanceYear || confirmationToken == null) return@synchronized }
        else if (command in listOf(PennantCommand.PITCH, PennantCommand.PLATE_APPEARANCE, PennantCommand.GAME)) { if (!before.canAdvanceMatch) return@synchronized }
        else if (!before.canProgress) return@synchronized
        dispatch(command, before, replacementConfirmed, club, confirmationToken)
    }
    private fun dispatch(command: PennantCommand, before: PennantUiState, replacementConfirmed: Boolean, club: String,
        confirmationToken: StoreToken?, ownGameId: String? = null, dayIndex: Int? = null): Boolean {
        val bulk = command in listOf(PennantCommand.OPEN_SELECTED, PennantCommand.DAY, PennantCommand.SEASON)
        val token = claim(bulk) ?: return false
        if (command == PennantCommand.SEASON) mutable.value = mutable.value.copy(resultDay = null)
        val finishingDay = when (command) {
            PennantCommand.DAY, PennantCommand.GAME, PennantCommand.PITCH, PennantCommand.PLATE_APPEARANCE -> before.season?.dayIndex
            else -> null
        }
        viewModelScope.launch {
            try {
                val (display, slot) = withContext(computation) {
                    val progress: suspend (StoredSeason) -> Unit = { value ->
                        publish(token, pennantDisplay(value), finishingDay = finishingDay)
                    }
                    val committed = when {
                        ownGameId != null -> actions.openOwnGame(checkNotNull(confirmationToken), ownGameId, stopping::get, progress)
                        dayIndex != null -> actions.advanceVisibleDay(checkNotNull(confirmationToken), dayIndex, stopping::get, progress)
                        command == PennantCommand.NEXT_YEAR -> actions.nextYear(checkNotNull(confirmationToken))
                        else -> actions.execute(command, before.seedInput, club, replacementConfirmed, stopping::get, progress)
                    }
                    pennantDisplay(committed) to actions.hasSlot()
                }
                val notice = when (command) { PennantCommand.SAVE -> "手動セーブしました"; PennantCommand.LOAD -> "手動セーブから再開しました"; else -> null }
                publish(token, display, slot, finished = true, inputs = command in listOf(PennantCommand.CREATE, PennantCommand.LOAD), notice = notice,
                    finishingDay = finishingDay, openedGameId = ownGameId)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Re-read durable data; never keep an advanced candidate after a failed write.
                try {
                    val (display, slot) = withContext(computation) { actions.read()?.let(::pennantDisplay) to actions.hasSlot() }
                    publish(token, display, slot, finished = true, finishingDay = finishingDay)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The previous published value remains visible. */ }
                failed(token)
            }
        }
        return true
    }
    suspend fun historyYears(expected: StoreToken) = withContext(computation) { actions.historyYears(expected) }
    suspend fun historyYear(expected: StoreToken, year: Long) = withContext(computation) { actions.historyYear(expected, year) }
    fun applyLineup(expected: StoreToken, clubId: String, players: List<String>): Unit = synchronized(lock) {
        val before = mutable.value
        if (!before.canEditLineup || before.display?.stored?.token != expected || before.season?.selectedClubId != clubId) return@synchronized
        val order = players.toList()
        // A malformed UI draft is refused before dispatch; storage independently validates the candidate.
        if (runCatching { before.season!!.applyLineup(clubId, order) }.isFailure) return@synchronized
        val token = claim(false) ?: return@synchronized
        viewModelScope.launch {
            try {
                val (display, slot) = withContext(computation) {
                    pennantDisplay(actions.applyLineup(expected, clubId, order)) to actions.hasSlot()
                }
                publish(token, display, slot, finished = true, notice = "打順を保存しました")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // A normal read can finalize a durable completed checkpoint. Failed APPLY must
                // remain isolated, so retain the prior published value until explicit reload.
                failed(token)
            }
        }
    }
    private fun failed(token: Long) = synchronized(lock) {
        if (current(token)) mutable.value = mutable.value.copy(busy = false, bulk = false, stopRequested = false,
            loaded = true, error = "保存データを確認できませんでした。データを残したまま停止しています。")
    }
    override fun onCleared() {
        synchronized(lock) { cleared = true; generation = Math.addExact(generation, 1); stopping.set(true) }
        super.onCleared()
    }
    class Factory(private val actions: PennantActions) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PennantViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return PennantViewModel(actions) as T
        }
    }
}
