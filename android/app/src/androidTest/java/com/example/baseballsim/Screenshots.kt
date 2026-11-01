package com.example.baseballsim

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** AGP collects this runner-supplied directory separately from the test result XML. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeContentTestRule.saveScreenshot(name: String) {
    waitForIdle()
    val registry = InstrumentationRegistry.getInstrumentation()
    val configured = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
    val directory = if (configured.isNullOrBlank()) {
        File(requireNotNull(registry.targetContext.getExternalFilesDir(null)), "test-screenshots")
    } else {
        File(configured)
    }
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create screenshot output: $directory" }
    val output = File(directory, name)
    val bitmap = onNodeWithTag(MatchTags.SCREEN).captureToImage().asAndroidBitmap()
    output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    check(output.length() > 0) { "Screenshot output is empty: $output" }
}
