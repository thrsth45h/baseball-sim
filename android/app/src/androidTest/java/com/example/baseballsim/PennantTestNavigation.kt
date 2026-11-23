package com.example.baseballsim

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

private val pennantTabs = setOf(
    PennantTags.NAV_HOME, PennantTags.NAV_SCHEDULE, PennantTags.NAV_LINEUP,
    PennantTags.NAV_STANDINGS, PennantTags.NAV_SETTINGS,
)
private val fixedPennantControls = pennantTabs + setOf(PennantTags.BACK, PennantTags.STOP, PennantTags.RELOAD)

internal fun ComposeContentTestRule.clickPennant(tag: String) {
    val control = onNodeWithTag(tag)
    if (tag !in fixedPennantControls) control.performScrollTo()
    if (tag in pennantTabs) {
        control.assertIsDisplayed()
        val node = control.fetchSemanticsNode()
        Log.i("DashboardTouchProbe", "Compose tap $tag root=${node.boundsInRoot}, window=${node.boundsInWindow}")
    }
    control.performClick()
    if (tag in pennantTabs) {
        try {
            control.assertIsSelected()
        } catch (failure: AssertionError) {
            runCatching {
                val directory = pennantTestOutputDirectory()
                File(directory, "tab-failed-$tag.txt").writeText(onRoot(useUnmergedTree = true).printToString())
                pennantScreenshot("tab-failed-$tag.png")
                val center = control.fetchSemanticsNode().boundsInWindow.center
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val downTime = SystemClock.uptimeMillis()
                Log.i("DashboardTouchProbe", "Android instrumentation tap $tag window=$center")
                MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, center.x, center.y, 0).also { event ->
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                SystemClock.sleep(60)
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, center.x, center.y, 0).also { event ->
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                }
                Log.i("DashboardTouchProbe", "Android tap selected $tag: ${runCatching { control.assertIsSelected() }.isSuccess}")
            }
            throw failure
        }
    }
}
