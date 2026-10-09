package dev.flowstate.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

val Context.preferences by preferencesDataStore("settings")
class Preferences(private val context: Context) {
    private val themeKey=stringPreferencesKey("theme")
    private val onboardingKey=booleanPreferencesKey("onboarded")
    val theme=context.preferences.data.map { it[themeKey] ?: "system" }
    val onboarded=context.preferences.data.map { it[onboardingKey] ?: false }
    suspend fun theme(value: String) { require(value in listOf("system","light","dark"));context.preferences.edit { it[themeKey]=value } }
    suspend fun onboard() { context.preferences.edit { it[onboardingKey]=true } }
}
