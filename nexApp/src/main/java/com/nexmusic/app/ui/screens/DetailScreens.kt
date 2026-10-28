package com.nexmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.CapsuleButton
import com.nexmusic.app.ui.components.CoverCard
import com.nexmusic.app.ui.components.DownloadButton
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.icon.Back
import com.nexmusic.app.ui.icon.Favorite
import com.nexmusic.app.ui.icon.FavoriteBorder
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.icon.Shuffle
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

/** Back chevron plus an optional add-to-library heart, in the accent colour like iOS bar buttons. */
@Composable
fun BackBar(
    tint: Color = Accent,
    saved: Boolean? = null,
    onToggleSaved: () -> Unit = {},
) {
    val navigator = LocalNavigator.current
    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(NexIcons.Back, "Back", tint = tint, modifier = Modifier.size(40.dp).clip(CircleShape).clickable { navigator.nav.popBackStack() }.padding(9.dp))
        Spacer(Modifier.weight(1f))
        if (saved != null) {
            Icon(
                if (saved) NexIcons.Favorite else NexIcons.FavoriteBorder,
                if (saved) "Remove from Library" else "Add to Library",
                tint = tint,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onToggleSaved).padding(8.dp),
            )
        }
    }
}

@Composable
private fun playingId(): String {
    val state by LocalNavigator.current.player.nowPlaying.collectAsStateWithLifecycle()
    return state.mediaItem.mediaId
}

/** Centered cover, title, accent subtitle, then the Play / Shuffle pair — Apple's album header. */
private fun LazyListScope.collectionHeader(
    art: String?,
    title: String,
    subtitle: String,
    meta: String,
    onSubtitle: (() -> Unit)?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    item {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(art, Modifier.padding(top = 8.dp).size(250.dp).shadow(20.dp, RoundedCornerShape(10.dp)), RoundedCornerShape(10.dp))
            Spacer(Modifier.height(18.dp))
            Text(title, style = NexType.Title2, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = NexType.Title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = Accent,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onSubtitle != null) Modifier.clickable(onClick = onSubtitle) else Modifier,
            )
            if (meta.isNotBlank()) Text(meta.uppercase(), style = NexType.Caption, color = LocalNex.current.secondaryLabel, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 18.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CapsuleButton(NexIcons.PlayArrow, "Play", Modifier.weight(1f), onPlay)
                CapsuleButton(NexIcons.Shuffle, "Shuffle", Modifier.weight(1f), onShuffle)
            }
        }
    }
}

private fun LazyListScope.trackList(
    tracks: List<Track>,
    numbered: Boolean,
    playingId: String,
    onPlay: (Int) -> Unit,
) {
    itemsIndexed(tracks, key = { i, t -> "$i${t.videoId}" }) { i, t ->
        SongRow(
            t.title,
            if (numbered) null else t.artists?.joinToString { it.name },
            t.thumbnails.best(),
            onClick = { onPlay(i) },
            leadingNumber = if (numbered) i + 1 else null,
            playing = t.videoId == playingId,
            trailing = { DownloadButton(t) },
        )
    }
}

@Composable
private fun <T> DetailPage(
    load: Load<T>,
    saved: Boolean?,
    onToggleSaved: () -> Unit,
    content: LazyListScope.(T) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { BackBar(saved = saved.takeIf { load is Load.Ready }, onToggleSaved = onToggleSaved) }
        when (load) {
            Load.Loading -> item { Loading() }
            is Load.Failed -> item { Message(load.message) }
            is Load.Ready -> content(load.value)
        }
    }
}

@Composable
fun AlbumScreen(id: String) {
    val vm: DetailViewModel = koinViewModel(key = "album-$id")
    LaunchedEffect(id) { vm.openAlbum(id) }
    val load by vm.album.collectAsStateWithLifecycle()
    val saved by vm.inLibrary.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val playing = playingId()
    DetailPage(load, saved, vm::toggleLibrary) { a ->
        val name = a.title
        val play = { i: Int, shuffle: Boolean -> navigator.player.playList(a.tracks, i, "Album \"$name\"", a.audioPlaylistId, PlaylistType.ALBUM, shuffle) }
        collectionHeader(
            a.thumbnails.best(),
            a.title,
            a.artists.joinToString { it.name },
            listOfNotNull(a.type, a.year).joinToString(" · "),
            a.artists.firstOrNull()?.id?.let { artistId -> { navigator.artist(artistId) } },
            { play(0, false) },
            { play(0, true) },
        )
        trackList(a.tracks, numbered = true, playingId = playing) { play(it, false) }
        item {
            Text(
                listOfNotNull("${a.trackCount} songs", a.duration).joinToString(", "),
                style = NexType.Footnote,
                color = LocalNex.current.secondaryLabel,
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
fun PlaylistScreen(id: String) {
    val vm: DetailViewModel = koinViewModel(key = "playlist-$id")
    LaunchedEffect(id) { vm.openPlaylist(id) }
    val load by vm.playlist.collectAsStateWithLifecycle()
    val saved by vm.inLibrary.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val playing = playingId()
    DetailPage(load, saved, vm::toggleLibrary) { p ->
        val play = { i: Int, shuffle: Boolean -> navigator.player.playList(p.tracks, i, p.title, p.id.removePrefix("VL"), PlaylistType.PLAYLIST, shuffle) }
        collectionHeader(
            p.thumbnails.best(),
            p.title,
            p.author.name,
            listOfNotNull("Playlist", p.year.takeIf { it.isNotBlank() }).joinToString(" · "),
            null,
            { play(0, false) },
            { play(0, true) },
        )
        p.description?.takeIf { it.isNotBlank() }?.let {
            item { Text(it, style = NexType.Footnote, color = LocalNex.current.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        }
        trackList(p.tracks, numbered = false, playingId = playing) { play(it, false) }
    }
}

@Composable
fun ArtistScreen(id: String) {
    val vm: DetailViewModel = koinViewModel(key = "artist-$id")
    LaunchedEffect(id) { vm.openArtist(id) }
    val load by vm.artist.collectAsStateWithLifecycle()
    val followed by vm.inLibrary.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val nex = LocalNex.current

    when (val l = load) {
        Load.Loading -> Column(Modifier.statusBarsPadding()) { BackBar(); Loading() }
        is Load.Failed -> Column(Modifier.statusBarsPadding()) { BackBar(); Message(l.message) }
        is Load.Ready -> {
            val a = l.value
            val topSongs = a.songs?.results.orEmpty().map { it.toTrack() }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
                item {
                    // Full-bleed portrait with the name set into it, like Apple's artist pages.
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        Artwork(a.thumbnails.best(), Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                        Box(Modifier.statusBarsPadding()) { BackBar(tint = Color.White, saved = followed, onToggleSaved = vm::toggleLibrary) }
                        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, style = NexType.LargeTitle, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            val endpoint = a.shuffleId ?: a.radioId
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Accent)
                                    .clickable {
                                        if (endpoint != null) navigator.player.playEndpoint(endpoint, a.name) else navigator.player.playList(topSongs, 0, a.name, null)
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Icon(NexIcons.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
                if (topSongs.isNotEmpty()) {
                    item { SectionHeader("Top Songs") }
                    itemsIndexed(topSongs.take(10)) { i, t ->
                        SongRow(t.title, t.album?.name, t.thumbnails.best(), onClick = { navigator.player.playList(topSongs, i, "${a.name} Top Songs", null) }, trailing = { DownloadButton(t) })
                    }
                }
                a.albums?.results?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { SectionHeader("Albums") }
                    item { CardRow(list.map { Triple(it.thumbnails.best(), it.title, it.year) }) { navigator.album(list[it].browseId) } }
                }
                a.singles?.results?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { SectionHeader("Singles & EPs") }
                    item { CardRow(list.map { Triple(it.thumbnails.best(), it.title, it.year) }) { navigator.album(list[it].browseId) } }
                }
                a.featuredOn?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { SectionHeader("Appears On") }
                    item { CardRow(list.map { Triple(it.thumbnails.best(), it.title, it.author) }) { navigator.playlist(list[it].id) } }
                }
                a.related?.results?.takeIf { it.isNotEmpty() }?.let { list ->
                    item { SectionHeader("Similar Artists") }
                    item { CardRow(list.map { Triple(it.thumbnails.best(), it.title, null) }, circle = true) { navigator.artist(list[it].browseId) } }
                }
                a.description?.takeIf { it.isNotBlank() }?.let {
                    item { SectionHeader("About ${a.name}") }
                    item { Text(it, style = NexType.Subhead, color = nex.secondaryLabel, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CardRow(
    cards: List<Triple<String?, String, String?>>,
    circle: Boolean = false,
    onClick: (Int) -> Unit,
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards.size) { i ->
            val (art, title, sub) = cards[i]
            CoverCard(art, title, sub, if (circle) 130.dp else 160.dp, circle = circle) { onClick(i) }
        }
    }
}

@Composable
fun MoodScreen(
    params: String,
    title: String,
) {
    val vm: DetailViewModel = koinViewModel(key = "mood-$params")
    LaunchedEffect(params) { vm.openMood(params) }
    val load by vm.mood.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { BackBar() }
        item { com.nexmusic.app.ui.components.LargeTitle(title) }
        when (val l = load) {
            Load.Loading -> item { Loading() }
            is Load.Failed -> item { Message(l.message) }
            is Load.Ready ->
                l.value.items.filter { it.contents.isNotEmpty() }.forEach { shelf ->
                    item { SectionHeader(shelf.header) }
                    item {
                        CardRow(shelf.contents.map { Triple(it.thumbnails.best(), it.title, it.subtitle) }) { i ->
                            val c = shelf.contents[i]
                            val v = c.videoId
                            if (v != null) {
                                navigator.player.playEndpoint(YouTubeWatchEndpoint(videoId = v, playlistId = "RDAMVM$v"), c.title)
                            } else {
                                navigator.playlist(c.playlistBrowseId)
                            }
                        }
                    }
                }
        }
    }
}
