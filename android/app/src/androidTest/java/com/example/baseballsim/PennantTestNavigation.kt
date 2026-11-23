package com.example.baseballsim

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import java.io.File

private val pennantTabs = setOf(
    PennantTags.NAV_HOME, PennantTags.NAV_SCHEDULE, PennantTags.NAV_LINEUP,
    PennantTags.NAV_STANDINGS, PennantTags.NAV_SETTINGS,
)
private val fixedPennantControls = pennantTabs + setOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD)

internal fun ComposeContentTestRule.clickPennant(tag: String) {
    val control = onNodeWithTag(tag)
    if (tag !in fixedPennantControls) control.performScrollTo()
    if (tag in pennantTabs) control.assertIsDisplayed()
    control.performClick()
    if (tag in pennantTabs) {
        try {
            control.assertIsSelected()
        } catch (failure: AssertionError) {
            runCatching {
                val directory = pennantTestOutputDirectory()
                File(directory, "tab-failed-$tag.txt").writeText(onRoot(useUnmergedTree = true).printToString())
                pennantScreenshot("tab-failed-$tag.png")
            }
            throw failure
        }
    }
}
