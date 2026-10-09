package dev.flowstate.platform

import android.content.*
import androidx.work.*
import com.google.android.gms.location.*
import dev.flowstate.FlowStateApp
import dev.flowstate.data.DiagnosticEntity
import kotlinx.coroutines.CancellationException

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Platform(context)
            .enqueue(
                intent.getStringExtra("kind") ?: return,
                intent.getStringExtra("id") ?: return,
                intent.getStringExtra("key") ?: "",
                intent.getLongExtra("at", 0),
                version = intent.getIntExtra("version", 0),
            )
    }
}

class ResponseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Platform(context)
            .enqueue(
                "response",
                intent.getStringExtra("id") ?: return,
                response = intent.getStringExtra("response") ?: return,
                token = intent.getStringExtra("token") ?: return,
            )
    }
}

class RecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action !in
                setOf(
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
                )
        )
            return
        Platform(context).enqueue("reconcile")
        Platform(context).periodicRecovery()
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Platform(context)
                .enqueue(
                    "diagnostic",
                    key = "Geofencing: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}",
                )
            return
        }
        val transition =
            when (event.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> "enter"
                Geofence.GEOFENCE_TRANSITION_EXIT -> "exit"
                Geofence.GEOFENCE_TRANSITION_DWELL -> "dwell"
                else -> return
            }
        val at = event.triggeringLocation?.time ?: System.currentTimeMillis()
        event.triggeringGeofences?.forEach {
            Platform(context).enqueue("geofence", it.requestId, key = transition, at = at)
        }
    }
}

class EngineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FlowStateApp
        return try {
            when (val kind = inputData.getString("kind")) {
                "automation" ->
                    app.coordinator.trigger(
                        inputData.getString("id")!!,
                        inputData.getString("key") ?: "",
                        inputData.getLong("at", 0),
                        inputData.getInt("version", 0),
                    )
                "execution" -> app.coordinator.drive(inputData.getString("id")!!)
                "response" ->
                    app.coordinator.respond(
                        inputData.getString("id")!!,
                        inputData.getString("token")!!,
                        inputData.getString("response")!!,
                    )
                "geofence" ->
                    app.coordinator.geofence(
                        inputData.getString("id")!!,
                        inputData.getString("key")!!,
                        inputData.getLong("at", 0),
                    )
                "diagnostic" ->
                    app.database
                        .dao()
                        .diagnostic(
                            DiagnosticEntity(
                                at = System.currentTimeMillis(),
                                message = inputData.getString("key") ?: "Unknown diagnostic",
                            )
                        )
                "reconcile" -> app.coordinator.reconcile()
                else -> error("Unknown work type $kind")
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            app.database
                .dao()
                .diagnostic(
                    DiagnosticEntity(
                        at = System.currentTimeMillis(),
                        message = "Worker failed: ${e.message}",
                    )
                )
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
