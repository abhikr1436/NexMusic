package com.nexmusic.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.components.CapsuleButton
import com.nexmusic.app.ui.components.Destructive
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.ListLink
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.icon.Download
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import com.nexmusic.app.update.AppUpdater
import com.nexmusic.app.update.Release
import com.nexmusic.app.update.UpdateState
import org.koin.compose.koinInject

/** Library › Software Update: check GitHub for a newer release and install it in place. */
@Composable
fun UpdateScreen() {
    val updater: AppUpdater = koinInject()
    val state by updater.state.collectAsStateWithLifecycle()
    val nex = LocalNex.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (state is UpdateState.Idle || state is UpdateState.Failed) updater.check() }
    // Back from Settings with installing allowed: carry on where the user left off.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        (state as? UpdateState.NeedsPermission)?.let { if (context.packageManager.canRequestPackageInstalls()) updater.update(it.release) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = BottomChromePadding)) {
        BackBar()
        LargeTitle("Software Update")
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(NexIcons.Download, null, tint = Accent, modifier = Modifier.padding(top = 16.dp).size(72.dp))
            Text("NexMusic ${updater.currentVersion}", style = NexType.Title3, modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(20.dp))
            when (val s = state) {
                UpdateState.Idle, UpdateState.Checking -> {
                    Loading()
                    Text("Checking for updates…", style = NexType.Subhead, color = nex.secondaryLabel)
                }
                UpdateState.UpToDate -> {
                    Text("NexMusic is up to date.", style = NexType.Headline)
                    CapsuleButton(NexIcons.Download, "Check Again", Modifier.padding(top = 20.dp).fillMaxWidth()) { updater.check() }
                }
                is UpdateState.Available -> ReleaseCard(s.release, "Update Now") { updater.update(s.release) }
                is UpdateState.NeedsPermission ->
                    ReleaseCard(s.release, "Allow & Update", note = "Android asks you once to let NexMusic install its own updates. Turn on \"Allow from this source\", then come back.") {
                        context.startActivity(updater.permissionIntent())
                    }
                is UpdateState.Downloading -> {
                    Text("Downloading ${s.release.version}…", style = NexType.Headline)
                    if (s.progress < 0) {
                        LinearProgressIndicator(Modifier.padding(top = 16.dp).fillMaxWidth(), color = Accent, trackColor = nex.fill)
                    } else {
                        LinearProgressIndicator({ s.progress }, Modifier.padding(top = 16.dp).fillMaxWidth(), color = Accent, trackColor = nex.fill)
                        Text("${(s.progress * 100).toInt()}%", style = NexType.Footnote, color = nex.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                is UpdateState.Installing -> {
                    Loading()
                    Text("Installing… Confirm in the window Android shows.", style = NexType.Subhead, color = nex.secondaryLabel, textAlign = TextAlign.Center)
                }
                is UpdateState.Failed -> {
                    Text(s.message, style = NexType.Subhead, color = Destructive, textAlign = TextAlign.Center)
                    CapsuleButton(NexIcons.Download, "Try Again", Modifier.padding(top = 20.dp).fillMaxWidth()) {
                        s.release?.let(updater::update) ?: updater.check()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    action: String,
    note: String? = null,
    onClick: () -> Unit,
) {
    val nex = LocalNex.current
    Text("Version ${release.version} is available", style = NexType.Headline)
    if (release.apkSize > 0) Text("%.1f MB".format(release.apkSize / 1_048_576f), style = NexType.Footnote, color = nex.secondaryLabel)
    CapsuleButton(NexIcons.Download, action, Modifier.padding(top = 20.dp).fillMaxWidth(), onClick)
    note?.let { Text(it, style = NexType.Footnote, color = nex.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp)) }
    if (release.notes.isNotBlank()) {
        Text("What's New", style = NexType.Headline, modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 6.dp))
        Text(release.notes, style = NexType.Subhead, color = nex.secondaryLabel, modifier = Modifier.fillMaxWidth())
    }
}

/** Library row; says so when the launch check found a newer version. */
@Composable
fun UpdateRow(onClick: () -> Unit) {
    val updater: AppUpdater = koinInject()
    val state by updater.state.collectAsStateWithLifecycle()
    val waiting = state is UpdateState.Available || state is UpdateState.NeedsPermission
    ListLink(NexIcons.Download, if (waiting) "Update Available" else "Software Update", onClick = onClick)
}
