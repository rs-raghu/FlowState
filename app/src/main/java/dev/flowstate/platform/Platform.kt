package dev.flowstate.platform

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import dev.flowstate.MainActivity
import dev.flowstate.data.*
import dev.flowstate.engine.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class Platform(private val context: Context) {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun notificationsAllowed() =
        (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun precise() = granted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun background() = granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun locationEnabled() =
        (context.getSystemService(Context.LOCATION_SERVICE) as LocationManager).isLocationEnabled

    fun playServices() =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == 0

    fun exact() =
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun alarmIntent(
        kind: String,
        id: String,
        at: Long = 0,
        key: String = "",
        version: Int = 0,
    ): PendingIntent {
        val intent =
            Intent(context, AlarmReceiver::class.java)
                .setData(Uri.parse("flowstate://alarm/$kind/$id"))
                .putExtra("kind", kind)
                .putExtra("id", id)
                .putExtra("at", at)
                .putExtra("key", key)
                .putExtra("version", version)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancelAlarm(kind: String, id: String) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(kind, id))
        WorkManager.getInstance(context).cancelUniqueWork("timer:$kind:$id")
        if (kind == "automation")
            WorkManager.getInstance(context).cancelAllWorkByTag("automation:$id")
    }

    fun schedule(kind: String, id: String, at: Long, key: String = "", version: Int = 0) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(kind, id, at, key, version)
        try {
            if (exact()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
        // Independent durable fallback; duplicated delivery is filtered in the coordinator.
        val data =
            workDataOf("kind" to kind, "id" to id, "at" to at, "key" to key, "version" to version)
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "timer:$kind:$id",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EngineWorker>()
                    .setInputData(data)
                    .addTag("$kind:$id")
                    .setInitialDelay(
                        (at - System.currentTimeMillis()).coerceAtLeast(0),
                        TimeUnit.MILLISECONDS,
                    )
                    .build(),
            )
    }

    fun enqueue(
        kind: String,
        id: String = "",
        key: String = "",
        at: Long = 0,
        response: String = "",
        token: String = "",
        version: Int = 0,
    ) {
        val data =
            workDataOf(
                "kind" to kind,
                "id" to id,
                "key" to key,
                "at" to at,
                "response" to response,
                "token" to token,
                "version" to version,
            )
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "event:$kind:$id:$key:$token",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<EngineWorker>()
                    .setInputData(data)
                    .addTag("$kind:$id")
                    .build(),
            )
    }

    fun periodicRecovery() {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "recovery",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EngineWorker>(15, TimeUnit.MINUTES)
                    .setInputData(workDataOf("kind" to "reconcile"))
                    .build(),
            )
    }

    private fun geofenceIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceReceiver::class.java).setAction("dev.flowstate.GEOFENCE"),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0,
        )

    suspend fun register(locations: List<LocationEntity>): String? {
        val preferences =
            context.getSharedPreferences("geofence-registration", Context.MODE_PRIVATE)
        val configuration =
            locations
                .sortedBy { it.id }
                .joinToString(";") {
                    "${it.id}:${it.latitude}:${it.longitude}:${it.radius}:${it.dwellSeconds}:${it.cooldownSeconds}"
                }
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val fingerprint =
            MessageDigest.getInstance("SHA-256")
                .digest(
                    "$configuration:$boot:${precise()}:${background()}:${locationEnabled()}:${playServices()}"
                        .toByteArray()
                )
                .joinToString("") { "%02x".format(it) }
        if (preferences.getString("fingerprint", null) == fingerprint) return null
        val client = LocationServices.getGeofencingClient(context)
        if (!playServices()) return "Google Play Services unavailable"
        preferences.edit().remove("fingerprint").commit()
        try {
            withTimeout(15000) { client.removeGeofences(geofenceIntent()).await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            return "Geofence removal timed out; will retry"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Geofence removal failed: ${e.message}"
        }
        if (locations.isEmpty()) {
            preferences.edit().putString("fingerprint", fingerprint).commit()
            return null
        }
        if (!precise() || !background()) return "Precise and background location required"
        if (!locationEnabled()) return "Device location is disabled"
        if (locations.size > 100) return "More than 100 locations; disable some automations"
        return try {
            val fences = locations.map {
                Geofence.Builder()
                    .setRequestId(it.id)
                    .setCircularRegion(it.latitude, it.longitude, it.radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(
                        Geofence.GEOFENCE_TRANSITION_ENTER or
                            Geofence.GEOFENCE_TRANSITION_EXIT or
                            Geofence.GEOFENCE_TRANSITION_DWELL
                    )
                    .setLoiteringDelay(it.dwellSeconds * 1000)
                    .setNotificationResponsiveness(120000)
                    .build()
            }
            withTimeout(15000) {
                client
                    .addGeofences(
                        GeofencingRequest.Builder()
                            .setInitialTrigger(0)
                            .addGeofences(fences)
                            .build(),
                        geofenceIntent(),
                    )
                    .await()
            }
            preferences.edit().putString("fingerprint", fingerprint).commit()
            null
        } catch (e: SecurityException) {
            "Location permission revoked: ${e.message}"
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            "Geofence registration timed out; will retry"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Geofence registration failed: ${e.message}"
        }
    }

    fun invalidateGeofences() {
        context
            .getSharedPreferences("geofence-registration", Context.MODE_PRIVATE)
            .edit()
            .remove("fingerprint")
            .commit()
    }

    fun cancelNotification(execution: String, token: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val dismissed = context.getSharedPreferences("dismissed-reminders", Context.MODE_PRIVATE)
        val edit = dismissed.edit()
        dismissed.all.keys
            .filter {
                if (token == null) it.startsWith("$execution:") else it == "$execution:$token"
            }
            .forEach { edit.remove(it) }
        edit.commit()
        if (token != null) manager.cancel("$execution:question:$token", 1)
        else {
            manager.cancel(execution, 1)
            manager.activeNotifications
                .filter { it.tag?.startsWith("$execution:question:") == true }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    fun cancelAllExecutionNotifications(execution: String) {
        cancelNotification(execution)
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .filter { it.tag?.startsWith("$execution:message") == true }
            .forEach { NotificationManagerCompat.from(context).cancel(it.tag, it.id) }
    }

    fun hasInteractionNotification(execution: String, token: String) =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.tag == "$execution:question:$token"
        }

    fun rememberDismissal(execution: String, token: String) {
        context
            .getSharedPreferences("dismissed-reminders", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("$execution:$token", true)
            .commit()
    }

    fun wasDismissed(execution: String, token: String) =
        context
            .getSharedPreferences("dismissed-reminders", Context.MODE_PRIVATE)
            .getBoolean("$execution:$token", false)

    fun clearDismissal(execution: String, token: String) {
        context
            .getSharedPreferences("dismissed-reminders", Context.MODE_PRIVATE)
            .edit()
            .remove("$execution:$token")
            .commit()
    }

    fun notification(execution: String, effect: Effect): Boolean {
        if (effect.kind == "cancelOwned") {
            if (effect.notification.target.isBlank()) cancelAllExecutionNotifications(execution)
            else
                NotificationManagerCompat.from(context)
                    .cancel("$execution:message:${Uri.encode(effect.notification.target)}", 1)
            return true
        }
        if (effect.kind == "cancel") {
            cancelNotification(execution, effect.cancelToken.ifBlank { null })
            return true
        }
        if (!notificationsAllowed()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        val config = effect.notification
        val channel = "workflows-${config.channel}"
        val importance =
            when (config.channel) {
                "low" -> NotificationManager.IMPORTANCE_LOW
                "high" -> NotificationManager.IMPORTANCE_HIGH
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }
        manager.createNotificationChannel(
            NotificationChannel(
                channel,
                "Workflow ${config.channel} priority",
                importance,
            )
        )
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setData(Uri.parse("flowstate://execution/$execution"))
                    .putExtra("execution", execution),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val n =
            NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(effect.title)
                .setContentText(
                    effect.body.ifBlank { effect.interaction?.options?.joinToString(" · ") ?: "" }
                )
                .setStyle(NotificationCompat.BigTextStyle().bigText(effect.body))
                .setContentIntent(open)
                .setAutoCancel(effect.interaction == null)
                .setOnlyAlertOnce(true)
                .setGroup(config.group.ifBlank { "flowstate" })
                .setCategory(config.category)
                .setOngoing(config.ongoing)
                .setPriority(
                    when (config.channel) {
                        "low" -> NotificationCompat.PRIORITY_LOW
                        "high" -> NotificationCompat.PRIORITY_HIGH
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    }
                )
        if (config.expireSeconds > 0) n.setTimeoutAfter(config.expireSeconds * 1000)
        effect.interaction?.let { i ->
            clearDismissal(execution, i.token)
            val dismiss =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, NotificationDismissReceiver::class.java)
                        .setData(Uri.parse("flowstate://dismiss/$execution/${i.token}"))
                        .putExtra("id", execution)
                        .putExtra("token", i.token),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            n.setDeleteIntent(dismiss)
            n.setContentText("Open to respond · ${i.kind}")
            if (i.kind in setOf("choice", "yesno", "confirm"))
                i.options.take(2).forEachIndexed { index, label ->
                    val intent =
                        Intent(context, ResponseReceiver::class.java)
                            .setData(Uri.parse("flowstate://respond/$execution/${i.token}/$index"))
                            .putExtra("id", execution)
                            .putExtra("token", i.token)
                            .putExtra("response", label)
                    val action =
                        PendingIntent.getBroadcast(
                            context,
                            0,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    n.addAction(0, label, action)
                }
            n.addAction(0, "Open choices", open)
            i.deadline?.let {
                n.setTimeoutAfter((it - System.currentTimeMillis()).coerceAtLeast(1))
            }
        }
        return try {
            NotificationManagerCompat.from(context)
                .notify(
                    effect.interaction?.let { "$execution:question:${it.token}" }
                        ?: "$execution:message:${Uri.encode(config.target.ifBlank { effect.id })}",
                    1,
                    n.build(),
                )
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
