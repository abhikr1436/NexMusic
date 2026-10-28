package com.nexmusic.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.searchResult.songs.Thumbnail
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.icon.ChevronRight
import com.nexmusic.app.ui.icon.Download
import com.nexmusic.app.ui.icon.Downloaded
import com.nexmusic.app.ui.icon.MusicNote
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType

private val sizeSegment = Regex("w\\d+-h\\d+")

/** YouTube serves a size in the URL; ask for one big enough for a full-width cover. */
fun String.hiRes(): String = replace(sizeSegment, "w544-h544")

fun List<Thumbnail>?.best(): String? = this?.maxByOrNull { it.width }?.url?.hiRes()

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val nex = LocalNex.current
    Box(modifier.clip(shape).background(nex.elevated), contentAlignment = Alignment.Center) {
        Icon(NexIcons.MusicNote, null, tint = nex.tertiaryLabel, modifier = Modifier.fillMaxSize(0.4f))
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Shrinks slightly while pressed, the way iOS cells and tiles respond. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun LargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = NexType.LargeTitle, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onMore: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NexType.Title2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (onMore != null) {
                Icon(NexIcons.ChevronRight, null, tint = LocalNex.current.secondaryLabel, modifier = Modifier.size(24.dp))
            }
        }
        if (subtitle != null) Text(subtitle, style = NexType.Subhead, color = LocalNex.current.secondaryLabel)
    }
}

@Composable
fun CoverCard(
    url: String?,
    title: String,
    subtitle: String?,
    width: Dp = Dp.Unspecified,
    circle: Boolean = false,
    onClick: () -> Unit,
) {
    Column((if (width == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(width)).pressable(onClick)) {
        Artwork(url, Modifier.fillMaxWidth().aspectRatio(1f), if (circle) CircleShape else RoundedCornerShape(10.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = NexType.Footnote,
            maxLines = if (circle) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (circle) Modifier.align(Alignment.CenterHorizontally) else Modifier,
        )
        if (!subtitle.isNullOrBlank() && !circle) {
            Text(subtitle, style = NexType.Footnote, color = LocalNex.current.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SongRow(
    title: String,
    subtitle: String?,
    artwork: String?,
    onClick: () -> Unit,
    leadingNumber: Int? = null,
    playing: Boolean = false,
    divider: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    val nex = LocalNex.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp).height(if (leadingNumber != null) 50.dp else 60.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingNumber != null) {
                Text("$leadingNumber", style = NexType.Body, color = nex.secondaryLabel, modifier = Modifier.width(32.dp))
            } else {
                Artwork(artwork, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = NexType.Body, color = if (playing) Accent else nex.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = NexType.Subhead, color = nex.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (leadingNumber != null) 52.dp else 80.dp), thickness = 0.5.dp, color = nex.separator)
    }
}

/** Apple's grey capsule with an accent glyph: the Play / Shuffle pair on album pages. */
@Composable
fun CapsuleButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(LocalNex.current.fill).clickable(onClick = onClick).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = NexType.Headline, color = Accent)
    }
}

@Composable
fun ListLink(
    icon: ImageVector,
    text: String,
    trailing: @Composable () -> Unit = { Icon(NexIcons.ChevronRight, null, tint = LocalNex.current.tertiaryLabel, modifier = Modifier.size(22.dp)) },
    onClick: () -> Unit,
) {
    val nex = LocalNex.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 20.dp).height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Text(text, style = NexType.Title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), modifier = Modifier.weight(1f))
            trailing()
        }
        HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = nex.separator)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = LocalNex.current.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun Message(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = NexType.Subhead, color = LocalNex.current.secondaryLabel)
    }
}


/** Download / downloaded / spinner for a song row; tapping a downloaded song removes it. */
@Composable
fun DownloadButton(track: Track) {
    val player = LocalNavigator.current.player
    val state by remember(track.videoId) { player.downloadState(track.videoId) }.collectAsStateWithLifecycle(DownloadState.STATE_NOT_DOWNLOADED)
    IconButton(onClick = { player.toggleDownload(track, state) }) {
        when (state) {
            DownloadState.STATE_DOWNLOADED -> Icon(NexIcons.Downloaded, "Remove download", tint = Accent, modifier = Modifier.size(22.dp))
            DownloadState.STATE_NOT_DOWNLOADED -> Icon(NexIcons.Download, "Download", tint = LocalNex.current.secondaryLabel, modifier = Modifier.size(22.dp))
            else -> CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
        }
    }
}
