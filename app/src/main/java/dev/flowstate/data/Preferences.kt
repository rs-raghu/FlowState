package dev.flowstate.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

val Context.preferences by preferencesDataStore("settings")

class Preferences(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val onboardingKey = booleanPreferencesKey("onboarded")
    private val optionsKey = stringPreferencesKey("defaults")
    val options =
        context.preferences.data.map {
            defaults +
                (it[optionsKey]?.let { raw ->
                    dev.flowstate.engine.codec.decodeFromString<Map<String, String>>(raw)
                } ?: emptyMap())
        }
    val theme = context.preferences.data.map { it[themeKey] ?: "system" }
    val onboarded = context.preferences.data.map { it[onboardingKey] ?: false }

    suspend fun theme(value: String) {
        require(value in listOf("system", "light", "dark"))
        context.preferences.edit { it[themeKey] = value }
    }

    suspend fun snapshot(): Map<String, String> =
        options.first() +
            mapOf("theme" to theme.first(), "onboarded" to onboarded.first().toString())

    suspend fun restore(values: Map<String, String>) {
        validate(values)
        context.preferences.edit { p ->
            val old =
                p[optionsKey]?.let {
                    dev.flowstate.engine.codec.decodeFromString<Map<String, String>>(it)
                } ?: emptyMap()
            p[optionsKey] =
                dev.flowstate.engine.codec.encodeToString(
                    old + values.filterKeys { it in defaults }
                )
            values["theme"]?.let { p[themeKey] = it }
            values["onboarded"]?.let { p[onboardingKey] = it.toBooleanStrict() }
        }
    }

    suspend fun reset() {
        context.preferences.edit { it.clear() }
    }

    companion object {
        val defaults =
            mapOf(
                "dynamicColor" to "true",
                "timeFormat" to "system",
                "timezone" to "device",
                "cooldown" to "300",
                "expiration" to "900",
                "concurrency" to "parallel",
                "catchUp" to "skip",
                "retention" to "30",
                "radius" to "150",
                "channel" to "normal",
            )

        fun validate(values: Map<String, String>) {
            require(values.keys.all { it in defaults || it in setOf("theme", "onboarded") }) {
                "Unknown preference"
            }
            values["theme"]?.let { require(it in setOf("system", "light", "dark")) }
            for (k in listOf("onboarded", "dynamicColor")) values[k]?.toBooleanStrict()
            values["timeFormat"]?.let { require(it in setOf("system", "12", "24")) }
            values["timezone"]?.let { if (it != "device") java.time.ZoneId.of(it) }
            values["concurrency"]?.let {
                require(it in setOf("parallel", "ignore", "queue", "replace"))
            }
            values["catchUp"]?.let {
                require(it in setOf("skip", "grace", "window", "record", "ask"))
            }
            values["channel"]?.let { require(it in setOf("normal", "low", "high")) }
            for ((k, range) in
                mapOf(
                    "cooldown" to 0..31536000,
                    "expiration" to 0..31536000,
                    "retention" to 1..3650,
                    "radius" to 100..100000,
                )) values[k]?.let { require(it.toInt() in range) { "Invalid $k" } }
        }
    }

    suspend fun onboard() {
        context.preferences.edit { it[onboardingKey] = true }
    }
}
