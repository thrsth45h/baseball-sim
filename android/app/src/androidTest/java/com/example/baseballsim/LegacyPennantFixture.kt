package com.example.baseballsim

import androidx.test.platform.app.InstrumentationRegistry
import com.example.baseballsim.data.PennantDatabase
import com.example.baseballsim.data.RoomSeasonStore
import com.example.baseballsim.data.StoredSeason
import java.util.UUID

/** New app reading an explicitly v1-created fixture, never described as an old APK upgrade. */
internal suspend fun createLegacyTargetWorld(): StoredSeason {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val db = PennantDatabase.open(context)
    return try {
        val store = RoomSeasonStore(db)
        val before = store.read()
        store.create("42", "L1-T01", "legacy-fixture-${UUID.randomUUID()}", before?.token, before != null)
    } finally { db.close() }
}

/** Explicit v2 fixture entry, kept separate from the v3 UI CREATE command. */
internal suspend fun createLineupTargetWorld(): StoredSeason {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val db = PennantDatabase.open(context)
    return try {
        val store = RoomSeasonStore(db)
        val before = store.read()
        store.createLineup("42", "L1-T01", "lineup-fixture-${UUID.randomUUID()}", before?.token, before != null)
    } finally { db.close() }
}
