package ky.paba.backgroundservices

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var _statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        _statusTextView = findViewById(R.id.statusTextView)

        var _btnStart = findViewById<Button>(R.id.btnStart)
        _btnStart.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest,
                REQUEST_CODE_PERMISSIONS
            )
        }

        handleIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                val serviceIntent = Intent(this, TrackerService::class.java)
                startService(serviceIntent)
                finish()
            } else {
                _statusTextView.text = "Izin diperlukan untuk menjalankan tayanan"
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_SERVICE_STOPPED_JUMP) {
            _statusTextView.text = "Background services sudah penhants, karena Ande melompat"
        } else {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.let {
            handleIntent(it)
        }
    }

    private val permissionsToRequest =
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACTIVITY_RECOGNITION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }


    companion object {
        const val ACTION_SERVICE_STOPPED_JUMP =
            "ky.paba.backgroundservice.ACTION_SERVICE_STOPPED JUMP"
        const val REQUEST_CODE_PERMISSIONS = 101
    }

}