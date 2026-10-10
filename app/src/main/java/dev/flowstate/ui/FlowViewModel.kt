package dev.flowstate.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.flowstate.FlowStateApp
import dev.flowstate.data.*
import dev.flowstate.engine.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

@HiltViewModel
class FlowViewModel @Inject constructor(app: Application) : AndroidViewModel(app) {
    val application = app as FlowStateApp
    val dao = application.database.dao()
    val automations =
        dao.observeAutomations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locations =
        dao.observeLocations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val executions =
        dao.observeExecutions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val diagnostics =
        dao.observeDiagnostics()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    val checklists =
        dao.observeChecklists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val events =
        dao.observeEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val locationEvents =
        dao.observeLocationEvents()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.message ?: "Operation failed"
            }
        }
    }

    fun reconcile() = work { application.coordinator.reconcile() }

    fun enable(a: AutomationEntity, value: Boolean) = work {
        application.coordinator.enable(a, value)
    }

    fun start(a: AutomationEntity) = work { application.coordinator.start(a.id) }

    fun delete(a: AutomationEntity) = work { application.coordinator.delete(a.id) }

    fun duplicate(a: AutomationEntity) = work {
        application.coordinator.save(UUID.randomUUID().toString(), a.name + " copy", a.workspace)
    }

    fun rename(a: AutomationEntity, name: String) = work {
        require(name.isNotBlank() && name.length <= 120)
        application.coordinator.rename(a.id, name)
    }

    fun cancel(id: String) = work { application.coordinator.cancel(id) }

    fun checklist(id: String, token: String, checked: Set<Int>) = work {
        application.coordinator.checklist(id, token, checked)
    }

    fun respond(id: String, token: String, value: String) = work {
        application.coordinator.respond(id, token, value)
    }

    fun snooze(id: String, minutes: Long, token: String) = work {
        application.coordinator.snooze(id, minutes, token)
    }

    fun create(name: String, template: String, location: String?, onCreated: (String) -> Unit) =
        work {
            val id = UUID.randomUUID().toString()
            application.coordinator.save(
                id,
                name,
                template(template, location, Preferences(application).options.first()),
            )
            onCreated(id)
        }

    fun addExamples() = work {
        val count = application.coordinator.importBackup(BuiltInExamples.source(application))
        message.value =
            "Added $count example workflows, disabled. Edit demo locations before enabling."
    }

    fun saveLocation(
        id: String?,
        name: String,
        latitude: Double,
        longitude: Double,
        radius: Float,
        description: String,
        enabled: Boolean = true,
        dwellSeconds: Int = 120,
        cooldownSeconds: Int = 30,
        icon: String = "",
    ) = work {
        require(name.isNotBlank() && name.length <= 120)
        require(
            latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
        )
        require(radius.isFinite() && radius in 100f..100000f)
        val now = System.currentTimeMillis()
        val old = id?.let { dao.location(it) }
        application.coordinator.saveLocation(
            LocationEntity(
                id ?: UUID.randomUUID().toString(),
                name,
                latitude,
                longitude,
                radius,
                description,
                enabled,
                old?.created ?: now,
                now,
                dwellSeconds = dwellSeconds,
                cooldownSeconds = cooldownSeconds,
                icon = icon,
            )
        )
    }

    fun deleteLocation(l: LocationEntity) = work {
        application.coordinator.deleteLocation(l.id)
    }

    companion object {
        val templates =
            listOf(
                "Blank message",
                "Leaving Hostel",
                "Going to Class",
                "Going to Mess",
                "Gym Preparation",
                "Returning Home",
                "Morning Routine",
                "Bedtime Routine",
                "Daily Planning",
                "Weekly Checklist",
                "Location-based Safety Checklist",
            )

        fun template(
            name: String,
            location: String? = null,
            defaults: Map<String, String> = Preferences.defaults,
        ): String = WorkflowPresets.source(name, location, defaults)
    }
}
