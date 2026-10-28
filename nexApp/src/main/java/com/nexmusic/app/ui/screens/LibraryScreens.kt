package com.nexmusic.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.utils.toArrayListTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.island.DynamicIsland
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.components.CapsuleButton
import com.nexmusic.app.ui.components.CoverCard
import com.nexmusic.app.ui.components.DownloadButton
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.ListLink
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.hiRes
import com.nexmusic.app.ui.icon.Album
import com.nexmusic.app.ui.icon.Downloaded
import com.nexmusic.app.ui.icon.History
import com.nexmusic.app.ui.icon.Island
import com.nexmusic.app.ui.icon.MusicNote
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Person
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.icon.QueueMusic
import com.nexmusic.app.ui.icon.Shuffle
import com.nexmusic.app.ui.theme.IosGreen
import org.koin.androidx.compose.koinViewModel

private data class Kind(
    val route: String,
    val title: String,
)

private val kinds =
    listOf(
        Kind("playlists", "Playlists"),
        Kind("artists", "Artists"),
        Kind("albums", "Albums"),
        Kind("songs", "Songs"),
        Kind("downloaded", "Downloaded"),
        Kind("recent", "Most Played"),
    )

@Composable
fun LibraryScreen() {
    val vm: LibraryViewModel = koinViewModel()
    val albums by vm.savedAlbums.collectAsStateWithLifecycle()
    val playlists by vm.savedPlaylists.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val icons = listOf(NexIcons.QueueMusic, NexIcons.Person, NexIcons.Album, NexIcons.MusicNote, NexIcons.Downloaded, NexIcons.History)

    // Albums and playlists the user added, newest first, as one "Recently Added" grid.
    val recentlyAdded =
        (albums.map { Triple(it.inLibrary, it) { navigator.album(it.browseId) } } + playlists.map { Triple(it.inLibrary, it) { navigator.playlist(it.id) } })
            .sortedByDescending { it.first }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { LargeTitle("Library") }
        kinds.forEachIndexed { i, k -> item { ListLink(icons[i], k.title) { navigator.library(k.route) } } }
        item {
            val context = LocalContext.current
            val island by DynamicIsland.enabled.collectAsStateWithLifecycle()
            ListLink(
                NexIcons.Island,
                "Dynamic Island",
                trailing = { Switch(island, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = IosGreen, checkedBorderColor = IosGreen)) },
            ) { if (island) DynamicIsland.turnOff() else DynamicIsland.turnOn(context) }
        }
        item { SectionHeader("Recently Added") }
        if (recentlyAdded.isEmpty()) item { Message("Albums and playlists you add show up here.") }
        items(recentlyAdded.chunked(2)) { pair ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (_, entity, open) ->
                    val (title, subtitle, art) =
                        when (entity) {
                            is com.maxrave.domain.data.entities.AlbumEntity -> Triple(entity.title, entity.artistName?.joinToString(), entity.thumbnails)
                            is com.maxrave.domain.data.entities.PlaylistEntity -> Triple(entity.title, entity.author, entity.thumbnails)
                            else -> Triple("", null, null)
                        }
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { CoverCard(art?.hiRes(), title, subtitle, onClick = open) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun LibraryListScreen(kind: String) {
    val vm: LibraryViewModel = koinViewModel()
    val navigator = LocalNavigator.current
    val title = kinds.firstOrNull { it.route == kind }?.title.orEmpty()
    val artists by vm.followedArtists.collectAsStateWithLifecycle()
    val albums by vm.savedAlbums.collectAsStateWithLifecycle()
    val playlists by vm.savedPlaylists.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { BackBar() }
        item { LargeTitle(title) }
        when (kind) {
            "artists" -> {
                val list = artists
                if (list.isEmpty()) item { Message("Artists you follow show up here.") }
                items(list) { SongRow(it.name, null, it.thumbnails?.hiRes(), onClick = { navigator.artist(it.channelId) }) }
            }
            "albums" -> {
                val list = albums
                if (list.isEmpty()) item { Message("Albums you add show up here.") }
                items(list) { SongRow(it.title, it.artistName?.joinToString(), it.thumbnails?.hiRes(), onClick = { navigator.album(it.browseId) }) }
            }
            "playlists" -> {
                val list = playlists
                if (list.isEmpty()) item { Message("Playlists you add show up here.") }
                items(list) { SongRow(it.title, it.author, it.thumbnails.hiRes(), onClick = { navigator.playlist(it.id) }) }
            }
            else -> {
                val list =
                    when (kind) {
                        "songs" -> liked
                        "downloaded" -> downloaded
                        else -> recent
                    }
                songs(list, title)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songs(
    list: List<SongEntity>,
    title: String,
) {
    if (list.isEmpty()) {
        item { Message("Nothing here yet. Tap ♥ on a song to love it.") }
        return
    }
    item {
        val navigator = LocalNavigator.current
        val tracks = list.toArrayListTrack()
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CapsuleButton(NexIcons.PlayArrow, "Play", Modifier.weight(1f)) { navigator.player.playList(tracks, 0, title, null) }
            CapsuleButton(NexIcons.Shuffle, "Shuffle", Modifier.weight(1f)) { navigator.player.playList(tracks, 0, title, null, shuffle = true) }
        }
    }
    items(list.size) { i ->
        val navigator = LocalNavigator.current
        val s = list[i]
        SongRow(s.title, s.artistName?.joinToString(), s.thumbnails?.hiRes(), onClick = { navigator.player.playList(list.toArrayListTrack(), i, title, null) }, trailing = { DownloadButton(s.toTrack()) })
    }
}
