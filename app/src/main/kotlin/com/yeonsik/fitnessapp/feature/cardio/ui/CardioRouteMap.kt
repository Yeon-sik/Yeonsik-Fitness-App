package com.yeonsik.fitnessapp.feature.cardio.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection

/** The one platform renderer retained by the cardio Compose destination. */
@Composable
fun CardioRouteMap(projection: CardioRouteProjection, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember(context) {
        MapView(context).also { view ->
            view.onCreate(null)
            view.onStart()
            view.onResume()
        }
    }
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }

    AndroidView(
        factory = {
            mapView.also { view -> view.getMapAsync { googleMap = it } }
        },
        modifier = modifier.fillMaxWidth().height(280.dp),
        update = { }
    )

    LaunchedEffect(googleMap, projection) {
        val map = googleMap ?: return@LaunchedEffect
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false
        map.clear()
        val bounds = LatLngBounds.builder()
        var first: LatLng? = null
        var count = 0
        projection.segments().forEach { segment ->
            val line = PolylineOptions().color(android.graphics.Color.rgb(0, 122, 255))
                .width(7f).geodesic(false)
            segment.forEach { point ->
                val position = LatLng(point.latitude, point.longitude)
                line.add(position)
                bounds.include(position)
                if (first == null) first = position
                count++
            }
            if (segment.size >= 2) map.addPolyline(line)
        }
        if (count > 0) {
            val target = first
            if (count == 1 && target != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16f))
            } else {
                mapView.post {
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 24))
                }
            }
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
}
