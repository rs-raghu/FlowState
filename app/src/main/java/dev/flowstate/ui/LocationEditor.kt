package dev.flowstate.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.flowstate.data.LocationEntity
import dev.flowstate.data.Preferences
import dev.flowstate.platform.Platform
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

@Composable
internal fun LocationDialog(vm: FlowViewModel, location: LocationEntity?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val defaults by remember {
        Preferences(context)
    }
        .options
        .collectAsStateWithLifecycle(Preferences.defaults)
    val id = location?.id
    var name by rememberSaveable(id) { mutableStateOf(location?.name ?: "") }
    var lat by rememberSaveable(id) { mutableStateOf(location?.latitude?.toString() ?: "") }
    var lon by rememberSaveable(id) { mutableStateOf(location?.longitude?.toString() ?: "") }
    var radius by
        rememberSaveable(id, defaults["radius"]) {
            mutableStateOf(location?.radius?.toInt()?.toString() ?: defaults.getValue("radius"))
        }
    var dwell by rememberSaveable(id) { mutableStateOf((location?.dwellSeconds ?: 120).toString()) }
    var cooldown by
        rememberSaveable(id) { mutableStateOf((location?.cooldownSeconds ?: 30).toString()) }
    var description by rememberSaveable(id) { mutableStateOf(location?.description ?: "") }
    var icon by rememberSaveable(id) { mutableStateOf(location?.icon ?: "") }
    var enabled by rememberSaveable(id) { mutableStateOf(location?.enabled ?: true) }
    var coordinates by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val presets = listOf(100, 150, 200, 300, 500, 1000).map { it.toString() }
    var customRadius by rememberSaveable { mutableStateOf(radius !in presets) }
    var finding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun locate() {
        scope.launch {
            finding = true
            error = null
            val cancellation = CancellationTokenSource()
            try {
                val position =
                    withTimeout(15_000) {
                        LocationServices.getFusedLocationProviderClient(context)
                            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                            .await()
                    } ?: error("Position unavailable. Tap the map or enter coordinates.")
                lat = position.latitude.toString()
                lon = position.longitude.toString()
            } catch (e: TimeoutCancellationException) {
                error = "Couldn't find your position yet. Check location services, or tap the map."
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                error =
                    "Location access changed. Tap Use device position to allow it again, or use coordinates."
            } catch (e: Exception) {
                error = e.message ?: "Couldn't find your position. You can enter coordinates."
            } finally {
                cancellation.cancel()
                finding = false
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (Platform(context).precise()) locate()
            else
                error =
                    "Precise location access is needed for device position. You can still use the map or coordinates."
        }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = onDismiss) { Text("Back") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            try {
                                val latitude = lat.toDoubleOrNull()
                                val longitude = lon.toDoubleOrNull()
                                val metres = radius.toFloatOrNull()
                                require(name.isNotBlank() && name.length <= 120) {
                                    "Enter a location name (up to 120 characters)."
                                }
                                require(
                                    latitude != null &&
                                        latitude.isFinite() &&
                                        latitude in -90.0..90.0 &&
                                        longitude != null &&
                                        longitude.isFinite() &&
                                        longitude in -180.0..180.0
                                ) {
                                    "Choose a point on the map, use your position, or enter valid coordinates."
                                }
                                require(
                                    metres != null && metres.isFinite() && metres in 100f..100000f
                                ) {
                                    "Radius must be between 100 and 100000 metres."
                                }
                                require(
                                    dwell.toIntOrNull() in 30..86400 &&
                                        cooldown.toIntOrNull() in 0..86400
                                ) {
                                    "Dwell must be 30–86400 seconds and cooldown 0–86400 seconds."
                                }
                                saving = true
                                error = null
                                vm.saveLocation(
                                    id,
                                    name.trim(),
                                    latitude,
                                    longitude,
                                    metres,
                                    description,
                                    enabled,
                                    dwell.toInt(),
                                    cooldown.toInt(),
                                    icon,
                                    onSaved = onDismiss,
                                    onError = {
                                        error = it
                                        saving = false
                                    },
                                )
                            } catch (e: Exception) {
                                error = e.message ?: "Check the location details."
                            }
                        },
                        modifier = Modifier.testTag("save-location"),
                    ) {
                        Text(if (saving) "Saving…" else "Save")
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp).testTag("location-error"),
                    )
                }
                Column(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (location == null) "New location" else "Edit location",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().testTag("location-name"),
                    )
                    OutlinedButton(
                        enabled = !finding,
                        onClick = {
                            focus.clearFocus()
                            if (Platform(context).precise()) locate()
                            else
                                permission.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (finding) "Finding your position…" else "Use device position")
                    }
                    LocationMap(
                        lat.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } ?: 20.0,
                        lon.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 }
                            ?: 78.0,
                        radius.toFloatOrNull()?.takeIf { it.isFinite() && it in 100f..100000f }
                            ?: 150f,
                    ) { latitude, longitude ->
                        focus.clearFocus()
                        lat = latitude.toString()
                        lon = longitude.toString()
                        error = null
                    }
                    Text(
                        if (lat.isBlank() || lon.isBlank())
                            "No point chosen yet. Tap the map or use device position."
                        else
                            "Pin: " +
                                String.format(
                                    java.util.Locale.US,
                                    "%.5f, %.5f",
                                    lat.toDoubleOrNull() ?: 0.0,
                                    lon.toDoubleOrNull() ?: 0.0,
                                ),
                        modifier = Modifier.testTag("location-pin"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SettingChoice(
                        "Area radius",
                        if (customRadius) "custom" else radius,
                        presets.map { it to "$it m" } + ("custom" to "Custom radius"),
                    ) {
                        customRadius = it == "custom"
                        if (!customRadius) radius = it
                    }
                    if (customRadius)
                        OutlinedTextField(
                            radius,
                            { radius = it },
                            label = { Text("Radius metres (100–100000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    TextButton(
                        onClick = { coordinates = !coordinates },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (coordinates) "Hide coordinates ▴" else "Enter coordinates ▾")
                    }
                    if (coordinates) {
                        OutlinedTextField(
                            lat,
                            { lat = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("location-latitude"),
                        )
                        OutlinedTextField(
                            lon,
                            { lon = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("location-longitude"),
                        )
                    }
                    TextButton(
                        onClick = { advanced = !advanced },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (advanced) "Hide advanced options ▴" else "Advanced options ▾")
                    }
                    if (advanced) {
                        Text(
                            "Dwell waits until you've stayed inside the area. Cooldown helps avoid repeated boundary events."
                        )
                        OutlinedTextField(
                            dwell,
                            { dwell = it },
                            label = { Text("Minimum dwell seconds (30–86400)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            cooldown,
                            { cooldown = it },
                            label = { Text("Event cooldown seconds (0–86400)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            icon,
                            { if (it.length <= 32) icon = it },
                            label = { Text("Icon (optional emoji or symbol)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            description,
                            { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row {
                            Checkbox(
                                enabled,
                                { enabled = it },
                                modifier =
                                    Modifier.semantics { contentDescription = "Location enabled" },
                            )
                            Text("Location enabled", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
