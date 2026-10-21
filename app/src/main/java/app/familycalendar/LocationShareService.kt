package app.familycalendar

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.familycalendar.data.FamilyLocations
import app.familycalendar.data.FirebaseBootstrap
import app.familycalendar.data.PlaceEvent
import app.familycalendar.data.PlaceTracker
import app.familycalendar.data.SharingPrefs
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Keeps running (with a visible notification) while this phone shares its location or waits for
 * family arrival alerts. Alerts are built from the family's shared data, so they arrive while
 * this service is running; there is no server involved.
 */
class LocationShareService : Service() {
    private var locations: FamilyLocations? = null
    private var callback: LocationCallback? = null
    private var inside: Set<String>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SharingPrefs(this).apply { shareLocation = false; receiveAlerts = false }
            shutDown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        createChannels()
        ServiceCompat.startForeground(this, ONGOING_ID, ongoingNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        val prefs = SharingPrefs(this)
        val db = FirebaseBootstrap.app(this)?.let { FirebaseFirestore.getInstance(it) }
        if (db == null || prefs.familyId.isEmpty() || prefs.uid.isEmpty() || !(prefs.shareLocation || prefs.receiveAlerts)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        shutDown()
        val uid = prefs.uid
        val family = FamilyLocations(db, prefs.familyId) { event -> if (prefs.receiveAlerts && event.uid != uid) notifyAlert(event) }
        locations = family
        if (prefs.shareLocation) startLocationUpdates(family, prefs)
        return START_STICKY
    }

    private fun startLocationUpdates(family: FamilyLocations, prefs: SharingPrefs) {
        if (!hasLocationPermission()) return
        inside = null
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                family.publishLocation(prefs.uid, prefs.personId, loc.latitude, loc.longitude, loc.accuracy)
                val places = family.places.value
                if (places.isEmpty()) return
                val now = PlaceTracker.inside(inside.orEmpty(), loc.latitude, loc.longitude, loc.accuracy, places)
                val before = inside
                inside = now
                // The first reading only sets the starting point, so opening the app at home doesn't announce an arrival.
                if (before == null) return
                val (arrived, left) = PlaceTracker.transitions(before, now)
                arrived.forEach { id -> places.firstOrNull { it.id == id }?.let { family.publishPlaceEvent(prefs.uid, prefs.personId, prefs.personName, it.name, true) } }
                left.forEach { id -> places.firstOrNull { it.id == id }?.let { family.publishPlaceEvent(prefs.uid, prefs.personId, prefs.personName, it.name, false) } }
            }
        }
        this.callback = callback
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
            .setMinUpdateDistanceMeters(40f)
            .setMinUpdateIntervalMillis(30_000L)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission was revoked while starting; the ongoing notification stays so the user can stop sharing.
        }
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun shutDown() {
        callback?.let { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(it) }
        callback = null
        locations?.close()
        locations = null
    }

    override fun onDestroy() {
        val prefs = SharingPrefs(this)
        locations?.let { if (prefs.uid.isNotEmpty() && !prefs.shareLocation) it.clearLocation(prefs.uid) }
        shutDown()
        super.onDestroy()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "Location sharing", NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ALERTS, "Family arrivals", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun ongoingNotification(): Notification {
        val prefs = SharingPrefs(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationShareService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            prefs.shareLocation && prefs.receiveAlerts -> "Sharing your location and watching for family arrivals"
            prefs.shareLocation -> "Sharing your location with your family"
            else -> "Watching for family arrivals"
        }
        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Firefly")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    private fun notifyAlert(event: PlaceEvent) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val who = event.personName.ifEmpty { "Someone" }
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("$who ${if (event.arrived) "arrived at" else "left"} ${event.placeName}")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(event.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notifications are turned off for Firefly.
        }
    }

    companion object {
        const val ACTION_STOP = "app.familycalendar.STOP_SHARING"
        private const val CHANNEL_ONGOING = "sharing"
        private const val CHANNEL_ALERTS = "alerts"
        private const val ONGOING_ID = 41

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LocationShareService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LocationShareService::class.java).setAction(ACTION_STOP))
        }
    }
}
