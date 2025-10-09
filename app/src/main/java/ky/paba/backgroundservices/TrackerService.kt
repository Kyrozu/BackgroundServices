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
    // Action string untuk Broadcast Receiver yang menangani transisi aktivitas
    private val TRANSITION_RECEIVER_ACTION = "ky.paba.backgroundservice.ACTIVITY_TRANSITIONS"

    private lateinit var pendingIntent: PendingIntent
    private lateinit var activityReceiver: BroadcastReceiver
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var notificationManager: NotificationManagerCompat

    // ID channel dan notifikasi untuk foreground service dan deteksi lompatan
    private val NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING = "tracker service_running"
    private val NOTIFICATION_CHANNEL_ID_JUMP_DETECTED = "tracker_jump_detected"
    private val NOTIFICATION_ID_SERVICE_RUNNING = 1
    private val NOTIFICATION_ID_JUMP_DETECTED = 2

    // Fungsi dipanggil saat service pertama kali dibuat
    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)

        // Setup activity recognition dan sensor deteksi lompatan
        setupActivityRecognition()
        setupJumpDetector()
        Log.d("TrackerService", "Service onCreate")

        // Membuat notification channel jika versi Android >= O
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID_SERVICE_RUNNING,
                "Tracker Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val jumpChannel = android.app.NotificationChannel(
                NOTIFICATION_CHANNEL_ID_JUMP_DETECTED,
                "Jump Detection",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(jumpChannel)
        }
    }

    // Mengatur activity recognition untuk mendeteksi perubahan aktivitas pengguna
    private fun setupActivityRecognition() {
        val intent = Intent(TRANSITION_RECEIVER_ACTION)
        pendingIntent = PendingIntent.getBroadcast(
            this,
            8,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // BroadcastReceiver untuk menangani event activity transition
        activityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ActivityTransitionResult.hasResult(intent)) {
                }
            }
        }
        // Register receiver agar menerima broadcast
        registerReceiver(
            activityReceiver, IntentFilter(
                TRANSITION_RECEIVER_ACTION
            ),
            RECEIVER_NOT_EXPORTED
        )
    }

    // Menginisialisasi sensor accelerometer untuk mendeteksi lompatan
    private fun setupJumpDetector() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    // Memulai pendeteksian lompatan dengan mendaftarkan listener sensor
    private fun startJumpDetection() {
        accelerometer?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    // Callback saat sensor mengalami perubahan (digunakan untuk deteksi lompatan)
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val y = event.values[1]
            val jumpThreshold = 15.0

            // Jika nilai accelerometer melebihi threshold, dianggap sebagai lompatan
            if (Math.abs(y) > jumpThreshold) {
                Log.d("TrackerService", "LOMPATAN terdeteksi! (y: $y)")

                // Intent untuk membuka MainActivity saat notifikasi diklik
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

                // Membuat notifikasi lompatan
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

                // Menampilkan notifikasi jika izin tersedia
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

    // Membuat notifikasi untuk foreground service
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

    // Callback saat service dihentikan
    @RequiresPermission(Manifest.permission.ACTIVITY_RECOGNITION)
    override fun onDestroy() {
        super.onDestroy()
        Log.d("TrackerService", "Service onDestroy")
        try {
            // Menghapus pembaruan activity recognition
            ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pendingIntent)
                .addOnSuccessListener { Log.d("TrackerService", "Activity updates removed.") }
                .addOnFailureListener { e ->
                    Log.e("TrackerService", "Failed to remove activity updates", e)
                }

            // Melepaskan receiver dan sensor listener
            unregisterReceiver(activityReceiver)
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(
                "TrackerService",
                "Error during unregistering receivers/Listeners: ${e.message}"
            )
        }

    }

    // Fungsi ini diperlukan karena Service mengimplementasikan IBinder, tapi tidak digunakan
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // Callback saat akurasi sensor berubah, tidak digunakan di sini
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Dipanggil saat service dijalankan, memulai foreground service dan pendeteksian lompatan
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