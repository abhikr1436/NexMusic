package com.nexmusic.app

import android.Manifest
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maxrave.media3.di.setServiceActivitySession
import com.maxrave.media3.di.startService
import com.nexmusic.app.ui.NexApp
import com.nexmusic.app.ui.theme.NexTheme

class MainActivity : ComponentActivity() {
    private var bound = false
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                // Tapping the notification (or the car's "open on phone") returns here.
                setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)
                bound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { NexTheme { NexApp() } }
    }

    override fun onStart() {
        super.onStart()
        startService(this, connection)
    }

    override fun onStop() {
        super.onStop()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
