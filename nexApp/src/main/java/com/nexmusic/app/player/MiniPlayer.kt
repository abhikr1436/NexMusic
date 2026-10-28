package com.nexmusic.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.icon.FastForward
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Pause
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

/** The floating capsule above the tab bar. */
@Composable
fun MiniPlayer(onOpen: () -> Unit) {
    val player: PlayerViewModel = koinViewModel()
    val state by player.nowPlaying.collectAsStateWithLifecycle()
    val control by player.control.collectAsStateWithLifecycle()
    val nex = LocalNex.current
    val meta = state.mediaItem.metadata
    Row(
        Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(16.dp), ambientColor = nex.label.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(16.dp))
            .background(nex.elevated)
            .clickable(onClick = onOpen)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(meta.artworkUri, Modifier.size(44.dp), RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(meta.title.orEmpty(), style = NexType.Subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta.artist.orEmpty(), style = NexType.Footnote, color = nex.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { player.playPause() }) {
            Icon(if (control.isPlaying) NexIcons.Pause else NexIcons.PlayArrow, if (control.isPlaying) "Pause" else "Play", Modifier.size(30.dp))
        }
        IconButton(onClick = { player.next() }) {
            Icon(NexIcons.FastForward, "Next", Modifier.size(30.dp))
        }
    }
}
