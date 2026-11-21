package com.example.baseballsim

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.example.baseballsim.season.PennantSchedule
import com.example.baseballsim.season.SeasonState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File

/** The artifact records rendered semantic Text values; the expected SeasonState is used only for assertions. */
internal fun ComposeContentTestRule.observeAllPlayerDetails(expected: SeasonState, filename: String) {
    fun click(tag: String) {
        val node = onNodeWithTag(tag)
        if (tag != PennantTags.BACK) node.performScrollTo()
        node.performClick()
    }
    fun text(tag: String) = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    while (onAllNodesWithTag(PennantTags.BACK).fetchSemanticsNodes().isNotEmpty()) click(PennantTags.BACK)
    click(PennantTags.NAV_PLAYERS)
    val observed = JSONArray()
    PennantSchedule.clubs.forEach { club ->
        click(PennantTags.BROWSE_CLUB)
        onNodeWithText(club.displayName, useUnmergedTree = true).performScrollTo().performClick()
        val people = expected.playerIds(club.id)
        assertEquals(if (expected.lineupEnabled) 12 else 9, people.size)
        people.forEach { id ->
            click(PennantTags.player(id))
            onNodeWithTag("player_id").assertTextEquals(id)
            onNodeWithTag("player_club").assertTextEquals(club.displayName)
            val row = expected.playerCounts.single { it.playerId == id }
            onNodeWithTag("player_slot").assertTextEquals(playerRoleLabel(expected, row))
            val count = row.counts
            val values = listOf("player_completed_pa" to count.completedPlateAppearances, "player_safe_hits" to count.safeHitEvents,
                "player_walks" to count.walkEvents, "player_strikeouts" to count.strikeoutEvents, "player_home_runs" to count.homeRunEvents)
            val sample = JSONObject().put("player_id", text("player_id")).put("club", text("player_club")).put("slot", text("player_slot"))
            expected.playerProfile(id)?.let { profile ->
                onNodeWithTag("player_profile").assertTextEquals(batterProfileLabel(profile))
                sample.put("profile", text("player_profile"))
            }
            values.forEach { (tag, value) -> onNodeWithTag(tag).assertTextEquals(value.toString()); sample.put(tag, text(tag)) }
            observed.put(sample)
            click(PennantTags.BACK)
        }
    }
    assertEquals(if (expected.lineupEnabled) 144 else 108, observed.length())
    File(pennantTestOutputDirectory(), filename).writeText(JSONObject().put("schema", "club-home-observed-ui-v1")
        .put("world_format", if (expected.profilesEnabled) "v3" else if (expected.lineupEnabled) "v2" else "v1").put("completed_games", expected.results.size)
        .put("source", "Compose semantic Text nodes, not expected-state copies").put("players", observed).toString(2))
    click(PennantTags.BACK)
}
