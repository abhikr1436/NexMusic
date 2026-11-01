package com.nexmusic.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangularShape
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
import kotlinx.coroutines.delay

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
        if (!url.isNullOrEmpty()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Apple's Liquid Glass: [backdrop] seen through a blurred, refracting pane with a specular rim.
 * Blur needs API 31 and the lens API 33; below that the effects are no-ops, so the default tint goes near-opaque.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: RoundedRectangularShape,
    // Dark text behind light glass bleeds through more, so light mode gets the thicker tint.
    surface: Color = LocalNex.current.let { it.grouped.copy(alpha = if (android.os.Build.VERSION.SDK_INT < 31) 0.94f else if (it.isDark) 0.62f else 0.8f) },
    // Hand a LayerBackdrop here to let a lens on top (the tab bar's selection pill) refract this pane.
    exported: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
): Modifier =
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(18.dp.toPx())
            lens(14.dp.toPx(), 28.dp.toPx())
        },
        exportedBackdrop = exported,
        onDrawSurface = { drawRect(surface) },
    )

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
    videoId: String? = null,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A song card shows its download state like a song row does.
                if (videoId != null) DownloadedBadge(videoId)
                Text(subtitle, style = NexType.Footnote, color = LocalNex.current.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    trailing: (@Composable () -> Unit)? = null,
    menu: Track? = null,
    onRemove: (() -> Unit)? = null,
) {
    val nex = LocalNex.current
    var menuOpen by remember { mutableStateOf(false) }
    // Where the row is on screen, so the glass menu can open right next to it.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val haptics = LocalHapticFeedback.current
    val press =
        if (menu == null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier.combinedClickable(onClick = onClick, onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            })
        }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.then(press)) {
        if (menu != null) SongMenu(menu, menuOpen, bounds, onRemove) { menuOpen = false }
        Row(Modifier.padding(start = 20.dp, end = 12.dp).height(if (leadingNumber != null) 50.dp else 60.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingNumber != null) {
                Text("$leadingNumber", style = NexType.Body, color = nex.secondaryLabel, modifier = Modifier.width(32.dp))
            } else {
                Artwork(artwork, Modifier.size(48.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = NexType.Body, color = if (playing) Accent else nex.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Rows without their own download button still show whether the song is on the device.
                val badge = menu != null && trailing == null
                if (!subtitle.isNullOrBlank() || badge) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (badge) DownloadedBadge(menu!!.videoId)
                        Text(subtitle.orEmpty(), style = NexType.Subhead, color = nex.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            trailing?.invoke()
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (leadingNumber != null) 52.dp else 80.dp), thickness = 0.5.dp, color = nex.separator)
    }
}

/** Long-press actions for a song, like Apple's context menu. */
@Composable
private fun SongMenu(
    track: Track,
    open: Boolean,
    anchor: Rect,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val navigator = LocalNavigator.current
    var picking by remember { mutableStateOf(false) }
    val liked by remember(track.videoId) { navigator.player.isLiked(track.videoId) }.collectAsStateWithLifecycle(false)
    if (picking) AddToPlaylistDialog(track) { picking = false }
    val albumId = track.album?.id
    val artistId = track.artists?.firstOrNull { !it.id.isNullOrEmpty() }?.id
    if (!open) return
    val items =
        buildList<Pair<String, () -> Unit>> {
            add("Play Next" to { navigator.player.playNext(track) })
            add("Add to Queue" to { navigator.player.addToQueue(track) })
            add("Add to Playlist…" to { picking = true })
            add((if (liked) "Remove Love" else "Love") to { navigator.player.setLiked(track, !liked) })
            if (!albumId.isNullOrEmpty()) add("Go to Album" to { navigator.album(albumId) })
            if (artistId != null) add("Go to Artist" to { navigator.artist(artistId) })
            if (onRemove != null) add("Remove from Playlist" to onRemove)
        }
    GlassMenu(anchor, onDismiss, items, destructive = setOf("Remove from Playlist"))
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
            else -> DownloadRing(track.videoId, Accent, Modifier.size(20.dp))
        }
    }
}

/** Apple's download ring: fills with the real percentage, spins while the size is still unknown. */
@Composable
fun DownloadRing(
    videoId: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val player = LocalNavigator.current.player
    var percent by remember(videoId) { mutableFloatStateOf(-1f) }
    LaunchedEffect(videoId) {
        while (true) {
            percent = player.downloadProgress(videoId)
            delay(400)
        }
    }
    val shown by animateFloatAsState((percent / 100f).coerceIn(0f, 1f), label = "download")
    val track = color.copy(alpha = 0.25f)
    if (percent < 0f) {
        CircularProgressIndicator(modifier, color = color, strokeWidth = 2.dp, trackColor = track)
    } else {
        CircularProgressIndicator(progress = { shown }, modifier = modifier, color = color, strokeWidth = 2.dp, trackColor = track, gapSize = 0.dp)
    }
}

/** Small glyph before a subtitle: a tick once downloaded, a ring while downloading, nothing otherwise. */
@Composable
fun DownloadedBadge(
    videoId: String,
    tint: Color = LocalNex.current.secondaryLabel,
) {
    val player = LocalNavigator.current.player
    val state by remember(videoId) { player.downloadState(videoId) }.collectAsStateWithLifecycle(DownloadState.STATE_NOT_DOWNLOADED)
    when (state) {
        DownloadState.STATE_DOWNLOADED -> Icon(NexIcons.Downloaded, "Downloaded", tint = tint, modifier = Modifier.padding(end = 4.dp).size(14.dp))
        DownloadState.STATE_NOT_DOWNLOADED -> Unit
        else -> DownloadRing(videoId, tint, Modifier.padding(end = 4.dp).size(12.dp))
    }
}
