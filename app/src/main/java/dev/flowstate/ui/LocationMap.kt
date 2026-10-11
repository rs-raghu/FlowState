package dev.flowstate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

@Composable
fun LocationMap(
    latitude: Double,
    longitude: Double,
    radius: Float,
    onPin: (Double, Double) -> Unit,
) {
    val context = LocalContext.current
    val rings by
        produceState<List<List<Pair<Double, Double>>>>(emptyList(), context) {
            value =
                withContext(Dispatchers.IO) {
                    val root =
                        Json.parseToJsonElement(
                                context.assets.open("maps/countries.geojson").bufferedReader().use {
                                    it.readText()
                                }
                            )
                            .jsonObject
                    root["features"]!!
                        .jsonArray
                        .flatMap { f ->
                            val g = f.jsonObject["geometry"]!!.jsonObject
                            val coords = g["coordinates"]!!.jsonArray
                            if (g["type"]!!.jsonPrimitive.content == "Polygon")
                                coords.map { it.jsonArray }
                            else coords.flatMap { it.jsonArray.map { ring -> ring.jsonArray } }
                        }
                        .map { ring ->
                            ring.map { p ->
                                p.jsonArray[0].jsonPrimitive.double to
                                    p.jsonArray[1].jsonPrimitive.double
                            }
                        }
                }
        }
    val placePin by rememberUpdatedState(onPin)
    var centerLat by rememberSaveable { mutableDoubleStateOf(latitude) }
    var centerLon by rememberSaveable { mutableDoubleStateOf(longitude) }
    var span by rememberSaveable { mutableDoubleStateOf(30.0) }
    LaunchedEffect(latitude, longitude) {
        centerLat = latitude
        centerLon = longitude
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (rings.isEmpty()) "Loading offline map…"
            else "Tap to place a pin · drag or pinch to move and zoom",
            style = MaterialTheme.typography.labelLarge,
        )
        Canvas(
            Modifier.fillMaxWidth()
                .height(240.dp)
                .clipToBounds()
                .testTag("location-map")
                .semantics {
                    stateDescription = if (rings.isEmpty()) "Loading map" else "Map ready"
                    contentDescription =
                        "Location map. Tap to choose coordinates. Zoom controls below."
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        centerLon = (centerLon - pan.x / size.width * span).coerceIn(-180.0, 180.0)
                        centerLat = (centerLat + pan.y / size.width * span).coerceIn(-85.0, 85.0)
                        span = (span / zoom).coerceIn(0.001, 360.0)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        placePin(
                            (centerLat - (p.y - size.height / 2.0) / size.width * span).coerceIn(
                                -90.0,
                                90.0,
                            ),
                            (centerLon + (p.x - size.width / 2.0) / size.width * span).coerceIn(
                                -180.0,
                                180.0,
                            ),
                        )
                    }
                }
        ) {
            drawRect(Color(0xFFDDEBF0))
            fun project(lon: Double, lat: Double) =
                Offset(
                    ((lon - centerLon) / span * size.width + size.width / 2).toFloat(),
                    ((centerLat - lat) / span * size.width + size.height / 2).toFloat(),
                )
            rings.forEach { ring ->
                val path = Path()
                ring.forEachIndexed { index, (lon, lat) ->
                    val p = project(lon, lat)
                    if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, Color(0xFFADCAB0))
                drawPath(path, Color(0xFF789582), style = Stroke(1f))
            }
            val pin = project(longitude, latitude)
            // Equirectangular projection stretches longitude; draw geodesic circle samples instead
            // of pixel approximation.
            val path = Path()
            for (i in 0..72) {
                val angle = i * 2 * PI / 72
                val lat = latitude + radius * sin(angle) / 111320.0
                val lon =
                    longitude +
                        radius * cos(angle) /
                            (111320.0 * cos(latitude * PI / 180).coerceAtLeast(0.01))
                val p = project(lon, lat)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, Color(0x55366A47))
            drawPath(path, Color(0xFF366A47), style = Stroke(3f))
            drawCircle(Color.White, 9.dp.toPx(), pin)
            drawCircle(Color(0xFFAA3333), 6.dp.toPx(), pin)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { span = (span / 2).coerceAtLeast(0.001) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Zoom in +")
            }
            OutlinedButton(
                onClick = { span = (span * 2).coerceAtMost(360.0) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Zoom out −")
            }
        }
        Text(
            "Visible width: " +
                java.lang.String.format(
                    java.util.Locale.US,
                    "%.2f km",
                    span * 111.32 * cos(centerLat * PI / 180),
                ),
            modifier = Modifier.testTag("map-scale"),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = {
                span =
                    (radius * 8 / (111320.0 * cos(latitude * PI / 180).coerceAtLeast(0.01)))
                        .coerceIn(0.001, 360.0)
                centerLat = latitude
                centerLon = longitude
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Center on pin & show radius")
        }
        Text(
            "Natural Earth country boundaries. No street detail. Coordinate entry or device position gives finer placement.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
