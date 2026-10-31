package com.example.baseballsim

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdvanceMode { PITCH, PLATE_APPEARANCE, GAME }

data class MatchUiState(
    val seedInput: String = "42",
    val game: GamePresentation? = null,
    val isBusy: Boolean = false,
    val seedError: String? = null,
    val operationError: String? = null,
) {
    val canAdvance: Boolean
        get() = !isBusy && operationError == null && game?.status == GameStatus.IN_PROGRESS
}

/** Owns one game (including its RNG); UI composition is a read-only observer. */
class MatchViewModel(
    private val factory: GamePortFactory,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val lock = Any()
    private val mutableState = MutableStateFlow(MatchUiState())
    val state: StateFlow<MatchUiState> = mutableState.asStateFlow()

    private var game: GamePort? = null
    private var generation = 0L
    private var cleared = false

    init {
        newGame()
    }

    fun changeSeedInput(value: String) {
        synchronized(lock) {
            if (cleared || mutableState.value.isBusy) return
            mutableState.value = mutableState.value.copy(seedInput = value, seedError = null)
        }
    }

    fun newGame() {
        val seed: String
        val token: Long
        synchronized(lock) {
            val before = mutableState.value
            if (cleared || before.isBusy) return
            seed = validatedSeed(before.seedInput) ?: run {
                mutableState.value = before.copy(seedError = SEED_ERROR)
                return
            }
            token = ++generation
            mutableState.value = before.copy(isBusy = true, seedError = null, operationError = null)
        }
        viewModelScope.launch {
            try {
                val (created, snapshot) = withContext(computationDispatcher) {
                    val created = factory.create(seed)
                    created to created.snapshot().detached()
                }
                synchronized(lock) {
                    if (!isCurrent(token)) return@synchronized
                    game = created
                    mutableState.value = mutableState.value.copy(
                        seedInput = snapshot.seed,
                        game = snapshot,
                        isBusy = false,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                publishFailure(token, "試合を開始できませんでした。もう一度お試しください。")
            }
        }
    }

    fun advance(mode: AdvanceMode) {
        val owned: GamePort
        val initial: GamePresentation
        val token: Long
        synchronized(lock) {
            val before = mutableState.value
            if (cleared || !before.canAdvance) return
            owned = game ?: return
            initial = before.game ?: return
            token = generation
            // Claim synchronously: taps during the launched work are dropped, never queued.
            mutableState.value = before.copy(isBusy = true)
        }
        viewModelScope.launch {
            try {
                val snapshot = withContext(computationDispatcher) {
                    var latest = initial
                    do {
                        currentCoroutineContext().ensureActive()
                        val previousPitches = latest.pitchesAccepted
                        latest = owned.advanceOnePitch().detached()
                        check(latest.pitchesAccepted > previousPitches || latest.status != GameStatus.IN_PROGRESS) {
                            "An accepted pitch must advance the game."
                        }
                    } while (
                        latest.status == GameStatus.IN_PROGRESS && when (mode) {
                            AdvanceMode.PITCH -> false
                            AdvanceMode.PLATE_APPEARANCE -> latest.completedPa == initial.completedPa
                            AdvanceMode.GAME -> true
                        }
                    )
                    latest
                }
                synchronized(lock) {
                    if (!isCurrent(token)) return@synchronized
                    mutableState.value = mutableState.value.copy(game = snapshot, isBusy = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed engine is not advanced again from a potentially partial operation.
                publishFailure(token, "試合を進められませんでした。新しい試合を開始してください。")
            }
        }
    }

    private fun publishFailure(token: Long, message: String) {
        synchronized(lock) {
            if (!isCurrent(token)) return
            mutableState.value = mutableState.value.copy(isBusy = false, operationError = message)
        }
    }

    private fun isCurrent(token: Long): Boolean = !cleared && generation == token

    override fun onCleared() {
        synchronized(lock) {
            cleared = true
            generation++
        }
        super.onCleared()
    }

    class Factory(private val gameFactory: GamePortFactory) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MatchViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return MatchViewModel(gameFactory) as T
        }
    }

    companion object {
        const val SEED_ERROR = "0、または0で始まらない半角数字を入力してください（最大18446744073709551615）。"

        internal fun validatedSeed(value: String): String? =
            if (value.length in 1..20 && (value.length == 1 || value.first() != '0') && value.all { it in '0'..'9' }) {
                value.toULongOrNull()?.toString()
            } else null
    }
}
