package dev.flowstate.data

import java.util.UUID
import kotlinx.serialization.json.*

object WorkflowPresets {
    val locations =
        setOf(
            "Leaving Hostel",
            "Going to Class",
            "Going to Mess",
            "Gym Preparation",
            "Returning Home",
            "Location-based Safety Checklist",
        )

    fun source(
        name: String,
        location: String? = null,
        defaults: Map<String, String> = Preferences.defaults,
    ): String {
        fun block(
            type: String,
            fields: Map<String, String>,
            inputs: Map<String, JsonObject> = emptyMap(),
            next: JsonObject? = null,
        ) = buildJsonObject {
            put("type", "fs_$type")
            put("id", UUID.randomUUID().toString())
            put("fields", JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
            if (inputs.isNotEmpty())
                put(
                    "inputs",
                    JsonObject(inputs.mapValues { buildJsonObject { put("block", it.value) } }),
                )
            if (next != null) put("next", buildJsonObject { put("block", next) })
            if (type == "ask")
                put(
                    "extraState",
                    buildJsonObject {
                        put("version", 1)
                        put("choices", inputs.keys.count { it.startsWith("CHOICE") })
                    },
                )
        }
        fun checklist(title: String, items: String) =
            block(
                "checklist",
                mapOf(
                    "TITLE" to title,
                    "OPTIONS" to items,
                    "TIMEOUT" to defaults.getValue("expiration"),
                    "CHANNEL" to defaults.getValue("channel"),
                ),
            )
        val items =
            when (name) {
                "Going to Class" -> "Notebook|ID|Pen|?Water"
                "Going to Mess" -> "ID|Meal card|?Water"
                "Gym Preparation" -> "Shoes|Water|Towel"
                "Returning Home" -> "Put keys away|Charge phone|?Plan tomorrow"
                "Morning Routine" -> "Brush teeth|Breakfast|Review today's plan"
                "Bedtime Routine" -> "Set alarm|Charge phone|Prepare tomorrow's clothes"
                "Daily Planning" -> "Choose three priorities|Check calendar|Plan breaks"
                "Weekly Checklist" -> "Laundry|Review expenses|Plan next week"
                "Location-based Safety Checklist" ->
                    "Check belongings|Check route|Share plans if needed"
                else -> "Keys|Wallet|?Water"
            }
        val action =
            if (name == "Blank message")
                block(
                    "message",
                    mapOf(
                        "TITLE" to "Reminder",
                        "BODY" to "Your workflow is running",
                        "CHANNEL" to defaults.getValue("channel"),
                    ),
                )
            else if (name == "Leaving Hostel")
                block(
                    "ask",
                    mapOf(
                        "KIND" to "choice",
                        "TITLE" to "Where are you going?",
                        "OPTIONS" to "Class|Mess|Gym",
                        "TIMEOUT" to defaults.getValue("expiration"),
                        "FOLLOWUP" to "300",
                        "CHANNEL" to defaults.getValue("channel"),
                    ),
                    mapOf(
                        "CHOICE0" to checklist("Class essentials", "Notebook|ID|Pen"),
                        "CHOICE1" to checklist("Mess essentials", "ID|?Water"),
                        "CHOICE2" to checklist("Gym essentials", "Shoes|Water|Towel"),
                    ),
                )
            else checklist(name, items)
        val trigger =
            mutableMapOf(
                "KIND" to "manual",
                "ZONE" to defaults.getValue("timezone"),
                "COOLDOWN" to defaults.getValue("cooldown"),
                "CONCURRENCY" to defaults.getValue("concurrency"),
                "CATCHUP" to defaults.getValue("catchUp"),
            )
        if (name in locations && location != null) {
            trigger["KIND"] = "location"
            trigger["LOCATION"] = location
            trigger["TRANSITION"] =
                if (name in setOf("Leaving Hostel", "Location-based Safety Checklist")) "exit"
                else "enter"
        }
        if (
            name in
                setOf("Morning Routine", "Bedtime Routine", "Daily Planning", "Weekly Checklist")
        ) {
            trigger["KIND"] = "time"
            trigger["TIMES"] =
                when (name) {
                    "Morning Routine" -> "07:00"
                    "Bedtime Routine" -> "22:00"
                    "Daily Planning" -> "08:00"
                    else -> "17:00"
                }
            trigger["RECURRENCE"] = if (name == "Weekly Checklist") "weeklyWindow" else "daily"
            if (name == "Weekly Checklist") {
                trigger["WINDOWDAY"] = "5"
                trigger["CATCHUP"] = "window"
            }
        }
        return buildJsonObject {
            put(
                "blocks",
                buildJsonObject {
                    put("languageVersion", 0)
                    put(
                        "blocks",
                        buildJsonArray { add(block("trigger", trigger, next = action)) },
                    )
                },
            )
        }
            .toString()
    }
}
