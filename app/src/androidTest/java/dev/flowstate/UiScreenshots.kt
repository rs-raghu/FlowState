package dev.flowstate

import android.content.Context
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal fun saveUiScreenshot(context: Context, name: String) {
    val folder = File(context.getExternalFilesDir(null), "ui-reports").apply { mkdirs() }
    val screenshot =
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    File(folder, "$name.png").outputStream().use {
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    screenshot.recycle()
}
