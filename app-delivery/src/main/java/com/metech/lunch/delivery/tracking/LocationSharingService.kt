package com.metech.lunch.delivery.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.metech.lunch.core.model.LocationUpdate
import com.metech.lunch.core.network.ApiResult
import com.metech.lunch.core.network.apiCall
import com.metech.lunch.delivery.MainActivity
import com.metech.lunch.delivery.R
import com.metech.lunch.delivery.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Shares the partner's GPS position with the kitchen while a batch is on the road.
 *
 * Runs as a foreground service (ongoing notification), so it keeps working with the screen off
 * without the background-location permission. The server answers each fix with whether the
 * partner still has meals on the road; when it says no, the service stops itself.
 */
class LocationSharingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sending = Mutex()
    private var lastSentAt = 0L
    private var lastGpsAt = 0L
    private var locationManager: LocationManager? = null

    private val listener = LocationListener { location -> onLocation(location) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !hasLocationPermission(this)) {
            stopSharing()
            return START_NOT_STICKY
        }
        startInForeground()
        startUpdates()
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Delivery tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while your location is shared with the kitchen during a delivery"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Delivery in progress")
            .setContentText("Sharing your location with the kitchen until the meals are handed over.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
    }

    @SuppressLint("MissingPermission") // checked in onStartCommand
    private fun startUpdates() {
        if (locationManager != null) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        locationManager = lm
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) {
                lm.requestLocationUpdates(provider, INTERVAL_MS, 0f, listener, Looper.getMainLooper())
            }
        }
        // Send a recent last-known GPS fix straight away so the map isn't empty for the first interval.
        runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.time < 2 * 60_000 }
            ?.let { onLocation(it, force = true) }
    }

    private fun onLocation(location: Location, force: Boolean = false) {
        val now = System.currentTimeMillis()
        // GPS is precise; a network (cell/Wi-Fi) fix can be hundreds of metres off. Use network
        // fixes only when GPS has been silent for a while (indoors, GPS switched off), and never
        // very coarse ones.
        if (location.provider == LocationManager.GPS_PROVIDER) {
            lastGpsAt = now
        } else {
            if (now - lastGpsAt < GPS_PREFERRED_MS) return
            if (location.hasAccuracy() && location.accuracy > MAX_NETWORK_ACCURACY_M) return
        }
        if (!force && now - lastSentAt < INTERVAL_MS - 1_000) return
        lastSentAt = now
        val update = LocationUpdate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMps = if (location.hasSpeed()) location.speed else null,
            headingDegrees = if (location.hasBearing()) location.bearing else null,
            recordedAt = Instant.ofEpochMilli(location.time).toString(),
        )
        scope.launch {
            sending.withLock {
                when (val r = apiCall { appContainer.api.sendLocation(update) }) {
                    is ApiResult.Success -> if (!r.value.tracking) stopSharing()
                    // Offline or a server hiccup: keep going, the next fix will try again.
                    is ApiResult.Failure -> Log.w(TAG, "Location not sent: ${r.message}")
                }
            }
        }
    }

    private fun stopSharing() {
        locationManager?.removeUpdates(listener)
        locationManager = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(listener)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationSharing"
        private const val CHANNEL = "delivery_tracking"
        private const val NOTIFICATION_ID = 42
        private const val INTERVAL_MS = 10_000L
        private const val GPS_PREFERRED_MS = 45_000L
        private const val MAX_NETWORK_ACCURACY_M = 150f
        private const val ACTION_STOP = "stop"

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Starts sharing (no-op if already running). Call while the app is on screen. */
        fun start(context: Context) {
            if (!hasLocationPermission(context)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LocationSharingService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start location sharing", it) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, LocationSharingService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
