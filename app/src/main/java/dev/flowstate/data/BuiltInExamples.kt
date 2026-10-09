package dev.flowstate.data

import android.content.Context
import dev.flowstate.engine.SafeInput
import dev.flowstate.engine.codec
import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

object BuiltInExamples {
    fun source(context: Context): String {
        val text =
            context.assets.open("examples.json").bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
        val backup = codec.decodeFromString<Backup>(SafeInput.json(text))
        val ids =
            (backup.automations.map { it.id } + backup.locations.map { it.id }).associateWith {
                UUID.randomUUID().toString()
            }
        fun rewrite(value: JsonElement, depth: Int = 0): JsonElement {
            require(depth <= 128)
            return when (value) {
                is JsonObject -> JsonObject(value.mapValues { rewrite(it.value, depth + 1) })
                is JsonArray -> JsonArray(value.map { rewrite(it, depth + 1) })
                is JsonPrimitive ->
                    if (value.isString && value.content in ids)
                        JsonPrimitive(ids.getValue(value.content))
                    else value
            }
        }
        return codec.encodeToString(
            backup.copy(
                automations =
                    backup.automations.map { a ->
                        a.copy(
                            id = ids.getValue(a.id),
                            workspace = rewrite(codec.parseToJsonElement(a.workspace)).toString(),
                        )
                    },
                locations = backup.locations.map { it.copy(id = ids.getValue(it.id)) },
            )
        )
    }
}
