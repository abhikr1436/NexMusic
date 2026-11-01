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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nexmusic.app.taste.TasteStore
import com.nexmusic.app.taste.ThemeMode
import org.koin.android.ext.android.inject
import com.maxrave.media3.di.setServiceActivitySession
import com.maxrave.media3.di.startService
import com.nexmusic.app.ui.NexApp
import com.nexmusic.app.ui.theme.NexTheme

class MainActivity : ComponentActivity() {
    private val taste: TasteStore by inject()
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
        setContent {
            val mode by taste.theme.collectAsState()
            val dark =
                when (mode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            // The app's own theme can differ from the phone's, so the status and nav bar icons follow it, not the system.
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(bars, bars)
            }
            NexTheme(dark) { NexApp() }
        }
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
