package com.example.baseballsim.data

import com.example.baseballsim.core.Status
import com.example.baseballsim.season.CheckpointCodec
import com.example.baseballsim.season.SeasonState
import java.security.MessageDigest

data class StoreToken(val worldId: String, val generationId: String, val revision: Long) {
    init { require(validIdentifier(worldId) && validIdentifier(generationId) && revision >= 0) }
}
data class StoredSeason(val token: StoreToken, val state: SeasonState, val operationId: String)
class StoreConflict(message: String) : IllegalStateException(message)
class StoreCorruption(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** No mutable engine instance crosses this boundary. Every successful write returns durable data. */
interface SeasonStore {
    suspend fun read(): StoredSeason?
    suspend fun create(masterSeed: String, selectedClubId: String, operationId: String, expected: StoreToken? = null, replacementConfirmed: Boolean = false): StoredSeason
    suspend fun commit(expected: StoreToken, operationId: String, candidate: SeasonState): StoredSeason
    suspend fun saveSlot(expected: StoreToken, operationId: String): StoredSeason
    suspend fun loadSlot(expected: StoreToken, operationId: String): StoredSeason
    suspend fun hasSlot(): Boolean
}

internal fun hash(bytes: ByteArray): String = lowercaseHex(MessageDigest.getInstance("SHA-256").digest(bytes))
internal fun lowercaseHex(bytes: ByteArray): String {
    val digits = "0123456789abcdef"
    val text = CharArray(bytes.size * 2)
    bytes.forEachIndexed { index, byte ->
        val value = byte.toInt() and 0xff
        text[index * 2] = digits[value ushr 4]
        text[index * 2 + 1] = digits[value and 0x0f]
    }
    return String(text)
}
internal fun validIdentifier(value: String) = Regex("[A-Za-z0-9._-]{1,128}").matches(value)
internal fun outsideMatch(state: SeasonState) = state.active == null
internal fun checkedState(bytes: ByteArray, expectedHash: String): SeasonState {
    try {
        require(hash(bytes) == expectedHash) { "Payload hash mismatch" }
        val value = CheckpointCodec.decodeSeason(bytes)
        require(CheckpointCodec.encodeSeason(value).contentEquals(bytes)) { "Noncanonical payload" }
        value.active?.let { if (it.snapshot.status != Status.ABORTED) it.recover(value.masterSeed) }
        return value
    } catch (error: Exception) {
        if (error is StoreCorruption) throw error
        throw StoreCorruption("Saved season is inconsistent; data has been retained", error)
    }
}

/** Test-only fault points are no-ops in production; they exercise real transaction boundaries. */
enum class StoreFaultPoint { BEFORE_TRANSACTION, AFTER_RESULTS, AFTER_HEAD, AFTER_COMMIT }
fun interface StoreFaults { fun hit(point: StoreFaultPoint) }
