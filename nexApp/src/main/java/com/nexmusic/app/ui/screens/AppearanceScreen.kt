package com.nexmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexmusic.app.taste.TasteStore
import com.nexmusic.app.taste.ThemeMode
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.icon.Check
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.Accents
import com.nexmusic.app.ui.theme.Backdrop
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.compose.koinInject

/** Library › Appearance: light or dark, accent colour, and the page background. Every change shows at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen() {
    val taste: TasteStore = koinInject()
    val mode by taste.theme.collectAsStateWithLifecycle()
    val backdrop by taste.backdrop.collectAsStateWithLifecycle()
    val nex = LocalNex.current
    val systemDark = isSystemInDarkTheme()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = BottomChromePadding)) {
        BackBar()
        LargeTitle("Appearance")

        SectionHeader("Mode")
        ToggleRow("Match System", mode == ThemeMode.System) { on ->
            // Leaving Automatic keeps whatever the phone shows right now, so nothing jumps.
            taste.setTheme(if (on) ThemeMode.System else if (systemDark) ThemeMode.Dark else ThemeMode.Light)
        }
        ToggleRow("Dark Mode", nex.isDark, enabled = mode != ThemeMode.System) { on -> taste.setTheme(if (on) ThemeMode.Dark else ThemeMode.Light) }

        SectionHeader("Accent Color")
        FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Accents.forEach { (name, color) ->
                val picked = color == Accent
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (picked) 3.dp else 0.dp, if (picked) nex.label else Color.Transparent, CircleShape)
                        .clickable { taste.setAccent(color.toArgb().toLong()) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (picked) Icon(NexIcons.Check, "$name, selected", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        SectionHeader("Background")
        Backdrop.entries.chunked(3).forEach { row ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { b ->
                    val picked = b == backdrop
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { taste.setBackdrop(b) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(b.brush(nex.isDark, Accent))
                                .border(if (picked) 3.dp else 1.dp, if (picked) Accent else nex.separator, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (picked) Icon(NexIcons.Check, null, tint = Accent, modifier = Modifier.size(26.dp))
                        }
                        Text(b.label, style = NexType.Footnote, color = if (picked) Accent else nex.label, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val nex = LocalNex.current
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }) {
        Row(Modifier.padding(horizontal = 20.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NexType.Body, color = if (enabled) nex.label else nex.tertiaryLabel, modifier = Modifier.weight(1f))
            Switch(
                checked,
                onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White, uncheckedThumbColor = Color.White, uncheckedTrackColor = nex.fill, uncheckedBorderColor = Color.Transparent),
            )
        }
        HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = nex.separator)
    }
}
