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

enum class PennantCommand { CREATE, SELECT, PITCH, PLATE_APPEARANCE, GAME, OPEN_SELECTED, DAY, SEASON, SAVE, LOAD }

/** The UI receives only values returned after durable commit, never a mutable game engine. */
interface PennantActions {
    suspend fun read(): StoredSeason?
    suspend fun hasSlot(): Boolean
    suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
        stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason
}

class DurablePennantActions(private val coordinator: SeasonCoordinator) : PennantActions {
    override suspend fun read() = coordinator.read()
    override suspend fun hasSlot() = coordinator.hasSlot()
    override suspend fun execute(command: PennantCommand, seed: String, club: String, replacement: Boolean,
        stop: () -> Boolean, progress: suspend (StoredSeason) -> Unit): StoredSeason = when (command) {
        PennantCommand.CREATE -> coordinator.create(seed, club, replacement)
        PennantCommand.SELECT -> coordinator.selectClub(club)
        PennantCommand.PITCH -> coordinator.advance(SeasonAdvance.PITCH)
        PennantCommand.PLATE_APPEARANCE -> coordinator.advance(SeasonAdvance.PLATE_APPEARANCE)
        PennantCommand.GAME -> coordinator.advance(SeasonAdvance.GAME)
        PennantCommand.OPEN_SELECTED -> coordinator.openNextSelected(stop, progress)
        PennantCommand.DAY -> coordinator.advanceDay(stop, progress)
        PennantCommand.SEASON -> coordinator.finishSeason(stop, progress)
        PennantCommand.SAVE -> coordinator.saveSlot()
        PennantCommand.LOAD -> coordinator.loadSlot()
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
    val replay = GameSession(last.game.seed)
    replay.runToEnd()
    check(GameSummary.fromCore(last.game, replay.snapshot) == last) { "Displayed result disagrees with its fixed game" }
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
) {
    val season get() = display?.stored?.state
    val canUse get() = loaded && !busy && error == null
    val canProgress get() = canUse && season?.status == SeasonStatus.IN_PROGRESS
    val canAdvanceMatch get() = canProgress && season?.active?.snapshot?.status == Status.IN_PROGRESS
    val canSave get() = canUse && season != null && season?.active == null
    val canLoad get() = canSave && hasSlot
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
        mutable.value = mutable.value.copy(busy = true, bulk = bulk, stopRequested = false, error = null, notice = null)
        generation
    }
    private fun current(token: Long) = !cleared && generation == token
    private fun publish(token: Long, value: PennantDisplay?, slot: Boolean? = null, finished: Boolean = false,
        inputs: Boolean = false, notice: String? = null) = synchronized(lock) {
        if (!current(token)) return@synchronized
        val old = mutable.value
        mutable.value = old.copy(display = value, hasSlot = slot ?: old.hasSlot,
            seedInput = if (inputs && value != null) value.stored.state.masterSeed else old.seedInput,
            selectedClubInput = value?.stored?.state?.selectedClubId ?: old.selectedClubInput,
            loaded = true, busy = !finished, bulk = if (finished) false else old.bulk,
            stopRequested = if (finished) false else old.stopRequested, error = null, notice = notice)
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
    fun execute(command: PennantCommand, replacementConfirmed: Boolean = false, club: String = mutable.value.selectedClubInput) {
        val before = mutable.value
        if (command == PennantCommand.CREATE) {
            if (before.busy || !before.canUse) return
            if (MatchViewModel.validatedSeed(before.seedInput) == null) {
                synchronized(lock) { if (!cleared && !mutable.value.busy) mutable.value = mutable.value.copy(seedError = MatchViewModel.SEED_ERROR) }
                return
            }
            if (before.season != null && !replacementConfirmed) return
        } else if (command == PennantCommand.SELECT) { if (!before.canUse || before.season == null) return }
        else if (command == PennantCommand.SAVE) { if (!before.canSave) return }
        else if (command == PennantCommand.LOAD) { if (!before.canLoad) return }
        else if (command in listOf(PennantCommand.PITCH, PennantCommand.PLATE_APPEARANCE, PennantCommand.GAME)) { if (!before.canAdvanceMatch) return }
        else if (!before.canProgress) return
        val bulk = command in listOf(PennantCommand.OPEN_SELECTED, PennantCommand.DAY, PennantCommand.SEASON)
        val token = claim(bulk) ?: return
        viewModelScope.launch {
            try {
                val (display, slot) = withContext(computation) {
                    val committed = actions.execute(command, before.seedInput, club, replacementConfirmed, stopping::get) { value ->
                        publish(token, pennantDisplay(value))
                    }
                    pennantDisplay(committed) to actions.hasSlot()
                }
                val notice = when (command) { PennantCommand.SAVE -> "手動セーブしました"; PennantCommand.LOAD -> "手動セーブから再開しました"; else -> null }
                publish(token, display, slot, finished = true, inputs = command in listOf(PennantCommand.CREATE, PennantCommand.LOAD), notice = notice)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Re-read durable data; never keep an advanced candidate after a failed write.
                try {
                    val (display, slot) = withContext(computation) { actions.read()?.let(::pennantDisplay) to actions.hasSlot() }
                    publish(token, display, slot, finished = true)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The previous published value remains visible. */ }
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
