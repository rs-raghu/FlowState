package dev.flowstate.data

import android.content.Context

/** One foreground editor draft, committed before rotation/process recreation. */
class EditorDrafts(context: Context) {
    private val preferences = context.getSharedPreferences("editor-draft", Context.MODE_PRIVATE)

    fun source(id: String): String? =
        if (preferences.getString("id", null) == id) preferences.getString("source", null) else null

    fun save(id: String, source: String) {
        require(source.length <= 2_000_000)
        preferences.edit().putString("id", id).putString("source", source).commit()
    }

    fun clear(id: String) {
        if (preferences.getString("id", null) == id) preferences.edit().clear().commit()
    }
}
