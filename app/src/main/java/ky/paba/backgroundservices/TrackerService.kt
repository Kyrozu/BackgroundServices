package ky.paba.backgroundservices

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest

// dari GPT
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransitionResult


class TrackerService : Service(), SensorEventListener {
    private val TRANSITION_RECEIVER_ACTION = "ky.paba.backgroundservice.ACTIVITY_TRANSITIONS"
    private lateinit var pendingIntent: PendingIntent
    private lateinit var activityReceiver: BroadcastReceiver
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var notificationManager: NotificationManagerCompat

    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2

    private fun setupActivityRecognition() {
        val intent = Intent(TRANSITION_RECEIVER_ACTION)
        pendingIntent = PendingIntent.getBroadcast(
            this,
            8,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        activityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ActivityTransitionResult.hasResult(intent)) {
                }
            }
        }
        registerReceiver(
            activityReceiver, IntentFilter(
                TRANSITION_RECEIVER_ACTION
            ),
            RECEIVER_NOT_EXPORTED
        )
    }

    private fun setupJumpDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val jumpThreshold = 15.0

            if (Math.abs(y) > jumpThreshold) {
                Log.d("TrackerService", "LOMPATAN terdeteksi! (y: $y)")

                val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_SERVICE_STOPPED_JUMP
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }

                val pendingMainActivityIntent = PendingIntent.getActivity(
                    this,
                    1,
                    mainActivityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val jumpNotification =
                    NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_JUMP_DETECTED)
                        .setContentTitle("Lompatan Terdeteksi!")
                        .setContentText("Service Berhenti. Klik untuk membuka aplikasi.")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(pendingMainActivityIntent)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .build()

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    NotificationManagerCompat.from(this).areNotificationsEnabled()
                ) {
                    notificationManager.notify(NOTIFICATION_ID_JUMP_DETECTED, jumpNotification)
                } else {
                    Log.w(
                        "TrackerService",
                        "izin POST_NOTIFICATIONS belum ada. "
                    )
                    stopSelf()
                }
            }
        }
    }

    private fun buildServiceRunningNotification(contentText: String): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingNotificationIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING)
            .setContentTitle("Pelacak Aktivitas Latar")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingNotificationIntent)
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onDestroy() {
        super.onDestroy()
        Log.d("TrackerService", "Service onDestroy")
        try {
            ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener { Log.d("TrackerService", "Activity updates removed.") }
                .addOnFailureListener { e ->
                    Log.e("TrackerService", "Failed to remove activity updates", e)
                }
            unregisterReceiver(activityReceiver)
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(
                "TrackerService",
                "Error during unregistering receivers/Listeners: ${e.message}"
            )
        }

    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        setupActivityRecognition()
        setupJumpDetector()
        Log.d("TrackerService", "Service onCreate")
    }

    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TrackerService", "Service onStartCommand")
        startForeground(
            NOTIFICATION_ID_SERVICE_RUNNING,
            buildServiceRunningNotification(
                "Aplikasi Tracker Services sedang berjalan..."
            ).build()
        )
        startJumpDetection()
        return START_STICKY
    }
}