package com.metech.lunch.customer.orders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.metech.lunch.core.model.TrackPoint
import com.metech.lunch.customer.BuildConfig
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

/**
 * OpenStreetMap with the delivery partner's position and route since pickup. Follows the rider
 * until the customer moves the map; the button brings it back.
 */
@Composable
fun RiderMap(location: TrackPoint, trail: List<TrackPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var following by remember { mutableStateOf(true) }
    val map = remember { createMap(context) { following = false } }
    val marker = remember { Marker(map).apply { icon = riderIcon(context); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null) } }
    val route = remember {
        Polyline(map).apply {
            outlinePaint.color = 0xFF1F5F3F.toInt()
            outlinePaint.strokeWidth = 12f
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setInfoWindow(null)
        }
    }

    DisposableEffect(lifecycle) {
        map.overlays.add(route)
        map.overlays.add(marker)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    Box(modifier) {
        AndroidView(
            factory = { map },
            update = { m ->
                val here = GeoPoint(location.latitude, location.longitude)
                route.setPoints(trail.map { GeoPoint(it.latitude, it.longitude) })
                marker.position = here
                if (following) m.controller.animateTo(here)
                m.invalidate()
            },
            modifier = Modifier.fillMaxSize(),
        )
        SmallFloatingActionButton(
            onClick = {
                following = true
                if (trail.size > 1) {
                    val box = BoundingBox.fromGeoPoints(trail.map { GeoPoint(it.latitude, it.longitude) })
                    map.zoomToBoundingBox(box.increaseByScale(1.4f), true)
                } else {
                    map.controller.animateTo(GeoPoint(location.latitude, location.longitude), 16.5, 600L)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
        ) { Icon(Icons.Default.LocationOn, contentDescription = "Show the rider", tint = MaterialTheme.colorScheme.primary) }
    }
}

private fun createMap(context: Context, onUserMoved: () -> Unit): MapView {
    Configuration.getInstance().apply {
        // OpenStreetMap's tile policy asks for an identifying user agent; tiles cache in app storage.
        userAgentValue = BuildConfig.APPLICATION_ID
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(16.0)
        // A drag means the customer wants to look around: stop following the rider.
        @Suppress("ClickableViewAccessibility")
        setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) onUserMoved()
            false
        }
    }
}

/** 🛵 in a white disc with a saffron ring, drawn once as a bitmap for the map marker. */
private fun riderIcon(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = (44 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val r = size / 2f
    canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55F2994A })
    canvas.drawCircle(r, r, r * 0.78f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2994A.toInt() })
    canvas.drawCircle(r, r, r * 0.66f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = r * 0.85f; textAlign = Paint.Align.CENTER }
    canvas.drawText("🛵", r, r - (text.descent() + text.ascent()) / 2, text)
    return BitmapDrawable(context.resources, bitmap)
}
