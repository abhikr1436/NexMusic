package com.nexmusic.app.player

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.hiRes
import com.nexmusic.app.ui.icon.ArrowDown
import com.nexmusic.app.ui.icon.FastForward
import com.nexmusic.app.ui.icon.FastRewind
import com.nexmusic.app.ui.icon.Download
import com.nexmusic.app.ui.icon.Downloaded
import com.nexmusic.app.ui.icon.Favorite
import com.nexmusic.app.ui.icon.FavoriteBorder
import com.nexmusic.app.ui.icon.Lyrics
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Pause
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.icon.QueueList
import com.nexmusic.app.ui.icon.Repeat
import com.nexmusic.app.ui.icon.RepeatOne
import com.nexmusic.app.ui.icon.Shuffle
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private enum class Pane { Artwork, Lyrics, Queue }

private val White60 = Color.White.copy(alpha = 0.6f)
private val White35 = Color.White.copy(alpha = 0.35f)

@Composable
fun NowPlayingScreen(onClose: () -> Unit) {
    val player: PlayerViewModel = koinViewModel()
    val state by player.nowPlaying.collectAsStateWithLifecycle()
    val control by player.control.collectAsStateWithLifecycle()
    val download by player.downloadState.collectAsStateWithLifecycle()
    val meta = state.mediaItem.metadata
    val art = meta.artworkUri?.hiRes()
    var pane by remember { mutableStateOf(Pane.Artwork) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(LocalDensity.current) { 140.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragY.coerceAtLeast(0f).roundToInt()) }
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color.Black)
            // Swallow taps so nothing underneath reacts.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Backdrop(art)
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Grabber: drag down to dismiss, or tap.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .draggable(
                            rememberDraggableState { dragY += it },
                            Orientation.Vertical,
                            onDragStopped = { if (dragY > dismissPx) onClose() else dragY = 0f },
                        ).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(40.dp, 5.dp).clip(CircleShape).background(White35))
                }

                AnimatedContent(pane, transitionSpec = { fadeIn() togetherWith fadeOut() }, modifier = Modifier.weight(1f), label = "pane") { p ->
                    when (p) {
                        Pane.Artwork ->
                            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
                                BigArtwork(art, control.isPlaying, Modifier.weight(1f))
                                TitleRow(meta.title.orEmpty(), meta.artist.orEmpty(), control.isLiked, player::toggleLike, download, { state.track?.let { player.toggleDownload(it, download) } })
                            }
                        Pane.Lyrics ->
                            Column(Modifier.fillMaxSize()) {
                                SmallHeader(art, meta.title.orEmpty(), meta.artist.orEmpty())
                                LyricsPane(player, Modifier.weight(1f))
                            }
                        Pane.Queue ->
                            Column(Modifier.fillMaxSize()) {
                                SmallHeader(art, meta.title.orEmpty(), meta.artist.orEmpty())
                                QueuePane(player, Modifier.weight(1f))
                            }
                    }
                }

                Column(Modifier.padding(horizontal = 28.dp)) {
                    Scrubber(player)
                    Transport(player, control.isPlaying)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        PaneToggle(NexIcons.Lyrics, "Lyrics", pane == Pane.Lyrics) { pane = if (pane == Pane.Lyrics) Pane.Artwork else Pane.Lyrics }
                        PaneToggle(NexIcons.QueueList, "Playing Next", pane == Pane.Queue) { pane = if (pane == Pane.Queue) Pane.Artwork else Pane.Queue }
                    }
                }
            }
        }
    }
}

/** Apple's Now Playing background: the cover itself, blown up and blurred. */
@Composable
private fun Backdrop(art: String?) {
    val canBlur = Build.VERSION.SDK_INT >= 31
    AsyncImage(
        model = art,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.6f; scaleY = 1.6f }.then(if (canBlur) Modifier.blur(90.dp) else Modifier),
    )
    // Without blur (Android 11 and older) the cover would read as-is, so it gets a much heavier veil.
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (canBlur) 0.35f else 0.72f)))
}

@Composable
private fun BigArtwork(
    art: String?,
    playing: Boolean,
    modifier: Modifier,
) {
    // The cover sits back when paused and springs forward on play.
    val scale by animateFloatAsState(if (playing) 1f else 0.82f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow), label = "art")
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Artwork(
            art,
            Modifier
                .size(side)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (playing) 28.dp else 10.dp, RoundedCornerShape(12.dp)),
            RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun TitleRow(
    title: String,
    artist: String,
    liked: Boolean,
    onLike: () -> Unit,
    download: Int,
    onDownload: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NexType.Title3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = NexType.Title3.copy(fontWeight = FontWeight.Normal), color = White60, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) {
            when (download) {
                DownloadState.STATE_DOWNLOADED -> Icon(NexIcons.Downloaded, "Remove download", Modifier.size(20.dp))
                DownloadState.STATE_NOT_DOWNLOADED -> Icon(NexIcons.Download, "Download", Modifier.size(20.dp))
                else -> CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        // IconButton grows each side to its 48dp touch target, so 36dp circles need this much to look apart.
        Spacer(Modifier.width(20.dp))
        IconButton(onClick = onLike, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f))) {
            Icon(if (liked) NexIcons.Favorite else NexIcons.FavoriteBorder, if (liked) "Unlike" else "Like", Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SmallHeader(
    art: String?,
    title: String,
    artist: String,
) {
    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(art, Modifier.size(64.dp).shadow(8.dp, RoundedCornerShape(6.dp)), RoundedCornerShape(6.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = NexType.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = NexType.Subhead, color = White60, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun Long.clock(): String {
    val s = (this / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Scrubber(player: PlayerViewModel) {
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) dragFraction else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val height by animateDpAsState(if (dragging) 12.dp else 6.dp, label = "track")
    Column(Modifier.padding(top = 8.dp)) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(duration) {
                    detectTapGestures { player.seekTo((it.x / size.width).coerceIn(0f, 1f)) }
                }.pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragFraction = (it.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            player.seekTo(dragFraction)
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(White35)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (dragging) Color.White else White60.copy(alpha = 0.85f)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text((if (dragging) (duration * dragFraction).toLong() else position).clock(), style = NexType.Caption, color = White60, modifier = Modifier.weight(1f))
            Text("-" + (duration - (if (dragging) (duration * dragFraction).toLong() else position)).clock(), style = NexType.Caption, color = White60)
        }
    }
}

@Composable
private fun Transport(
    player: PlayerViewModel,
    playing: Boolean,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        BounceButton(NexIcons.FastRewind, "Previous", 46.dp, player::previous)
        BounceButton(if (playing) NexIcons.Pause else NexIcons.PlayArrow, if (playing) "Pause" else "Play", 64.dp, player::playPause)
        BounceButton(NexIcons.FastForward, "Next", 46.dp, player::next)
    }
}

@Composable
private fun BounceButton(
    icon: ImageVector,
    label: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(dampingRatio = 0.5f), label = "bounce")
    Box(
        Modifier
            .size(size + 20.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(onClick) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }, onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size))
    }
}

@Composable
private fun PaneToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent),
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = if (active) Color.White else White60)
    }
}

@Composable
private fun LyricsPane(
    player: PlayerViewModel,
    modifier: Modifier,
) {
    val lyrics: Lyrics? by player.lyrics.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    val duration by player.duration.collectAsStateWithLifecycle()
    val lines = lyrics?.lines.orEmpty()
    if (lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lyrics aren't available for this song.", style = NexType.Headline, color = White60) }
        return
    }
    val synced = lyrics?.syncType != "UNSYNCED" && lines.any { (it.startTimeMs.toLongOrNull() ?: 0L) > 0L }
    val current by remember(lines, synced) {
        derivedStateOf { if (!synced) -1 else lines.indexOfLast { (it.startTimeMs.toLongOrNull() ?: 0L) <= position } }
    }
    val list = rememberLazyListState()
    LaunchedEffect(current) { if (current > 0) list.animateScrollToItem((current - 1).coerceAtLeast(0)) }
    LazyColumn(modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 240.dp)) {
        itemsIndexed(lines) { i, line ->
            val active = !synced || i == current
            val alpha by animateFloatAsState(if (active) 1f else 0.32f, label = "line")
            Text(
                line.words.ifBlank { "♪" },
                style = NexType.Title1.copy(fontSize = 28.sp, lineHeight = 34.sp),
                color = Color.White.copy(alpha = alpha),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = synced && duration > 0) {
                            player.seekTo(((line.startTimeMs.toLongOrNull() ?: 0L).toFloat() / duration).coerceIn(0f, 1f))
                        }.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun QueuePane(
    player: PlayerViewModel,
    modifier: Modifier,
) {
    val queue by player.queue.collectAsStateWithLifecycle()
    val control by player.control.collectAsStateWithLifecycle()
    val current by player.currentIndex.collectAsStateWithLifecycle()
    val tracks = queue?.data?.listTracks.orEmpty()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Playing Next", style = NexType.Headline)
                queue?.data?.playlistName?.let { Text("From $it", style = NexType.Footnote, color = White60, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            QueueToggle(NexIcons.Shuffle, "Shuffle", control.isShuffle, player::shuffle)
            Spacer(Modifier.width(8.dp))
            QueueToggle(if (control.repeatState == RepeatState.One) NexIcons.RepeatOne else NexIcons.Repeat, "Repeat", control.repeatState != RepeatState.None, player::repeat)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(tracks, key = { i, t -> "$i-${t.videoId}" }) { i, t ->
                if (i <= current) return@itemsIndexed
                Row(
                    Modifier.fillMaxWidth().clickable { player.playQueueItem(i) }.padding(horizontal = 24.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(t.thumbnails?.lastOrNull()?.url, Modifier.size(46.dp), RoundedCornerShape(6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, style = NexType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artists?.joinToString { it.name }.orEmpty(), style = NexType.Subhead, color = White60, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueToggle(
    icon: ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(20.dp), tint = if (on) Color.Black else Color.White)
    }
}
