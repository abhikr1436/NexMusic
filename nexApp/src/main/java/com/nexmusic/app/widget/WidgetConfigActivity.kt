package com.nexmusic.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.nexmusic.app.R
import com.nexmusic.app.taste.TasteStore
import com.nexmusic.app.taste.ThemeMode
import com.nexmusic.app.ui.components.pressable
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalBackdrop
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexTheme
import com.nexmusic.app.ui.theme.NexType
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** Opens when a widget is placed (and on Android 12+ when it is long-pressed › Customise): pick its look. */
class WidgetConfigActivity : ComponentActivity() {
    private val taste: TasteStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves no widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()
        enableEdgeToEdge()
        setContent {
            val mode by taste.theme.collectAsState()
            val dark =
                when (mode) {
                    ThemeMode.System -> isSystemInDarkTheme()
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                }
            val backdrop by taste.backdrop.collectAsState()
            NexTheme(dark, backdrop) { Picker { theme, glass -> choose(widgetId, theme, glass) } }
        }
    }

    private fun choose(
        widgetId: Int,
        theme: WidgetTheme,
        glass: GlassLevel,
    ) = lifecycleScope.launch {
        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(widgetId)
        updateAppWidgetState(this@WidgetConfigActivity, glanceId) {
            it[WidgetTheme.KEY] = theme.name
            it[GlassLevel.KEY] = glass.name
        }
        NexWidget().update(this@WidgetConfigActivity, glanceId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun Picker(onPick: (WidgetTheme, GlassLevel) -> Unit) {
    val nex = LocalNex.current
    var glass by remember { mutableStateOf(GlassLevel.Liquid) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(LocalBackdrop.current)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Widget Style", style = NexType.LargeTitle, color = nex.label)
        Text("Choose how this widget looks on your home screen.", style = NexType.Subhead, color = nex.secondaryLabel)
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            WidgetTheme.entries.forEach { theme ->
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().pressable { onPick(theme, glass) }) {
                        Preview(theme, glass)
                        Text(theme.label, style = NexType.Headline, color = nex.label, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    }
                    if (theme == WidgetTheme.Glass) {
                        Text("Strength, then tap the preview", style = NexType.Footnote, color = nex.secondaryLabel, modifier = Modifier.padding(top = 2.dp, start = 4.dp))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassLevel.entries.forEach { level ->
                                val on = level == glass
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .background(if (on) Accent else nex.label.copy(alpha = 0.08f), RoundedCornerShape(50))
                                        .pressable { glass = level }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(level.label, style = NexType.Subhead, color = if (on) Color.White else nex.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A medium widget drawn over a sample wallpaper, so the glass has something to show through. */
@Composable
private fun Preview(
    theme: WidgetTheme,
    glass: GlassLevel,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Brush.linearGradient(listOf(Color(0xFF5B5FE8), Color(0xFFE85BA8), Color(0xFFF2A65A))), RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        val panel =
            when (theme) {
                WidgetTheme.Light -> Modifier.background(Color(0xF7FFFFFF), shape)
                WidgetTheme.Dark -> Modifier.background(Color(0xF21C1C1E), shape)
                WidgetTheme.Glass ->
                    Modifier
                        .background(Brush.linearGradient(listOf(Color(0x4DFFFFFF), Color(0x1FFFFFFF), Color(0x2E000000))), shape)
                        .background(glass.tint, shape)
                        .border(1.dp, Color(0x80FFFFFF), shape)
            }
        Row(Modifier.fillMaxSize().then(panel).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(82.dp).background(Color(0x338E8E93), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.widget_ic_music_note), null, tint = Color(0xFF8E8E93), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Song Title", style = NexType.Subhead, color = theme.text)
                Text("Artist", style = NexType.Footnote, color = theme.secondary)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.widget_ic_skip_previous), null, tint = theme.text, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    val button =
                        when (theme) {
                            WidgetTheme.Light -> Modifier.background(Color.Black, CircleShape)
                            WidgetTheme.Dark -> Modifier.background(Color.White, CircleShape)
                            WidgetTheme.Glass -> Modifier.background(Color(0x40FFFFFF), CircleShape).border(1.dp, Color(0x8CFFFFFF), CircleShape)
                        }
                    Box(Modifier.size(36.dp).then(button), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.widget_ic_play_arrow), null, tint = theme.buttonIcon, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(painterResource(R.drawable.widget_ic_skip_next), null, tint = theme.text, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}
