package dev.flowstate

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry

internal fun saveUiScreenshot(context: Context, name: String) {
    val screenshot =
        requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
    // Public test screenshots survive Gradle uninstalling the application after instrumentation.
    val values =
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FlowState-ui-reports")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    val resolver = context.contentResolver
    val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    requireNotNull(resolver.openOutputStream(uri)).use {
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    resolver.update(
        uri,
        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
        null,
        null,
    )
    screenshot.recycle()
}
