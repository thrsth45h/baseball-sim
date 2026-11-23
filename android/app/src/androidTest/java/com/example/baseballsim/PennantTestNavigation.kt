package com.example.baseballsim

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

private val fixedPennantControls = setOf(
    PennantTags.NAV_HOME, PennantTags.NAV_SCHEDULE, PennantTags.NAV_LINEUP,
    PennantTags.NAV_STANDINGS, PennantTags.NAV_SETTINGS,
    PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD,
)

internal fun ComposeContentTestRule.clickPennant(tag: String) {
    val control = onNodeWithTag(tag)
    if (tag !in fixedPennantControls) control.performScrollTo()
    control.performClick()
}
