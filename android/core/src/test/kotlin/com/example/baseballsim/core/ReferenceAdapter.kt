package com.example.baseballsim.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.zip.GZIPInputStream

/** Test-only normalization. No Python flags/identity are exported by the native engine. */
object ReferenceAdapter {
    val gson = GsonBuilder().serializeNulls().create()
    fun bytes(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/reference/" + name)).use { it.readBytes() }
    fun json(name: String): JsonElement = JsonParser.parseString(bytes(name).toString(Charsets.UTF_8))
    fun transitions(): JsonObject = GZIPInputStream(bytes("frozen/transition_cases.json.gz").inputStream()).reader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    private fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonElement.array() = asJsonArray.toList()
    fun snapshot(value: JsonObject): GameSnapshot {
        val seq = value.getAsJsonObject("sequence"); val current = seq.getAsJsonObject("current"); val half = current.getAsJsonObject("half")
        val count = current.get("count").takeUnless { it.isJsonNull }?.asJsonObject?.let { CountState(it["balls"].asInt, it["strikes"].asInt, it["pitches_seen"].asLong, it["status"].asString) }
        return GameSnapshot(SequenceState(seq["inning"].asInt, if (seq["side"].asString == "top") Side.TOP else Side.BOTTOM,
            seq["closed_away_runs"].asLong, seq["closed_home_runs"].asLong, count,
            HalfState(half["outs"].asInt, half["bases"].array().map { it.asBoolean }, half["runs"].asLong)),
            value["batting_indices"].array().map { it.asInt }, value["batter_counts"].array().map { e -> e.asJsonObject.let {
                EventCounts(it["completed_plate_appearances"].asLong, it["safe_hit_events"].asLong, it["walk_events"].asLong, it["strikeout_events"].asLong, it["home_run_events"].asLong) } },
            value["pitches_accepted"].asLong, value["line_score"].array().map { if (it.isJsonNull) null else it.asLong },
            when (value["status"].asString) { "in_progress" -> Status.IN_PROGRESS; "completed" -> Status.COMPLETED; else -> Status.ABORTED },
            value.text("reason"), value.text("outcome"), value["terminal_score_delta"].asJsonArray.let { Score(it[0].asLong, it[1].asLong) }, value["history_origin"].asString)
    }
    fun input(value: JsonObject): SourceInput {
        if (value.has("abort_reason")) return SourceInput.Abort(value["abort_reason"].asString)
        val fair = value["fair"].takeUnless { it.isJsonNull }?.asJsonObject?.let {
            FairPlay(it["kind"].asString, it["candidate_bases"].takeUnless { it.isJsonNull }?.asInt,
                it["runner_destinations"].takeUnless { it.isJsonNull }?.array()?.map { d -> if (d.isJsonNull) null else d.asInt }) }
        return SourceInput.Pitch(PitchInput(value["event"].asString, fair, value["pitch_draw"].takeUnless { it.isJsonNull }?.asInt,
            value["fair_draw"].takeUnless { it.isJsonNull }?.asInt))
    }
    fun score(value: Score) = mapOf("away" to value.away, "home" to value.home)
    fun counts(c: EventCounts) = mapOf("completed_plate_appearances" to c.completedPlateAppearances, "safe_hit_events" to c.safeHitEvents,
        "walk_events" to c.walkEvents, "strikeout_events" to c.strikeoutEvents, "home_run_events" to c.homeRunEvents)
    fun sequence(s: SequenceState): Map<String, Any?> = mapOf("inning" to s.inning, "side" to if (s.side == Side.TOP) "top" else "bottom",
        "closed_away_runs" to s.closedAwayRuns, "closed_home_runs" to s.closedHomeRuns, "current" to mapOf(
            "count" to s.count?.let { mapOf("balls" to it.balls, "strikes" to it.strikes, "pitches_seen" to it.pitchesSeen, "status" to it.status) },
            "half" to mapOf("outs" to s.half.outs, "bases" to s.half.bases, "runs" to s.half.runs)))
    fun snapshotShape(s: GameSnapshot): JsonElement = gson.toJsonTree(mapOf("sequence" to sequence(s.sequence), "batting_indices" to s.battingIndices,
        "batter_counts" to s.batterCounts.map(::counts), "pitches_accepted" to s.pitchesAccepted, "line_score" to s.lineScore,
        "status" to s.status.name.lowercase(), "reason" to s.reason, "outcome" to s.outcome,
        "terminal_score_delta" to listOf(s.terminalScoreDelta.away, s.terminalScoreDelta.home), "history_origin" to s.historyOrigin))
    val resultKeys = listOf("status", "reason", "outcome", "score", "final_score", "partial_score", "pitches_accepted", "line_score",
        "team_events", "batter_events", "next_batting_indices", "last_projected_sequence", "terminal_score_delta", "history_origin")
    fun resultShape(result: GameResult): JsonElement {
        val s = result.snapshot; val groups = listOf("away", "home"); val rows = mutableListOf<Map<String, Any?>>()
        val team = mutableMapOf<String, Any>()
        for ((index, name) in groups.withIndex()) {
            val group = s.batterCounts.subList(index * 9, index * 9 + 9)
            team[name] = counts(EventCounts(group.sumOf { it.completedPlateAppearances }, group.sumOf { it.safeHitEvents }, group.sumOf { it.walkEvents }, group.sumOf { it.strikeoutEvents }, group.sumOf { it.homeRunEvents }))
            group.forEachIndexed { slot, c -> rows += mapOf("batter_id" to name + "_" + (slot + 1), "slot" to slot + 1, "team" to name) + counts(c) }
        }
        return gson.toJsonTree(mapOf("status" to s.status.name.lowercase(), "reason" to s.reason, "outcome" to s.outcome, "score" to score(s.score),
            "final_score" to if (s.status == Status.COMPLETED) score(s.score) else null, "partial_score" to if (s.status == Status.ABORTED) score(s.score) else null,
            "pitches_accepted" to s.pitchesAccepted, "line_score" to mapOf("innings" to (1..9).toList(), "away" to s.lineScore.filterIndexed { i, _ -> i % 2 == 0 },
                "home" to s.lineScore.filterIndexed { i, _ -> i % 2 == 1 }, "walkoff_half" to if (s.reason?.startsWith("walkoff_") == true) s.halfIndex else null),
            "team_events" to team, "batter_events" to rows, "next_batting_indices" to mapOf("away" to s.battingIndices[0], "home" to s.battingIndices[1]),
            "last_projected_sequence" to sequence(s.sequence), "terminal_score_delta" to score(s.terminalScoreDelta), "history_origin" to s.historyOrigin))
    }
    fun expectedResult(value: JsonObject) = JsonObject().apply { for (key in resultKeys) add(key, value[key]) }
    fun pitchShape(input: PitchInput): JsonElement = gson.toJsonTree(mapOf("event" to input.event, "pitch_draw" to input.pitchDraw, "fair_draw" to input.fairDraw,
        "fair" to input.fair?.let { mapOf("kind" to it.kind, "candidate_bases" to it.candidateBases, "runner_destinations" to it.runnerDestinations) }))
    fun rng(source: ToyPitchSource): JsonElement = gson.toJsonTree(mapOf("pitch_calls" to source.pitchCalls, "fair_calls" to source.fairCalls,
        "logical_draws" to source.rng.draw100Calls, "draw100_calls" to source.rng.draw100Calls, "raw_output_calls" to source.rng.rawOutputCalls,
        "rejected_raw_outputs" to source.rng.rejectedRawOutputs, "state_u64" to source.rng.state.toString()))
}
