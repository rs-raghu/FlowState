package dev.flowstate.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.preferences by preferencesDataStore("settings")

class Preferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val onboardingKey = booleanPreferencesKey("onboarded")
    val theme = context.preferences.data.map { it[themeKey] ?: "system" }
    val onboarded = context.preferences.data.map { it[onboardingKey] ?: false }

    suspend fun theme(value: String) {
        require(value in listOf("system", "light", "dark"))
        context.preferences.edit { it[themeKey] = value }
    }

    suspend fun snapshot(): Map<String, String> =
        mapOf("theme" to theme.first(), "onboarded" to onboarded.first().toString())

    suspend fun restore(values: Map<String, String>) {
        require(values.keys.all { it in setOf("theme", "onboarded") })
        values["theme"]?.let { require(it in listOf("system", "light", "dark")) }
        values["onboarded"]?.toBooleanStrict()
        context.preferences.edit { p ->
            values["theme"]?.let { p[themeKey] = it }
            values["onboarded"]?.let { p[onboardingKey] = it.toBooleanStrict() }
        }
    }

    suspend fun onboard() {
        context.preferences.edit { it[onboardingKey] = true }
    }
}
