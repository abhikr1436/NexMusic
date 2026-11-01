package com.nexmusic.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nexmusic.app.ui.components.Destructive
import com.nexmusic.app.ui.components.GlassButton
import com.nexmusic.app.ui.components.GlassDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.NewPlaylistDialog
import com.nexmusic.app.ui.components.GlassTextField
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import com.nexmusic.app.ui.icon.Add
import com.nexmusic.app.ui.icon.AddCircle
import com.nexmusic.app.ui.icon.Palette
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.nexmusic.app.library.LocalImport
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.utils.toArrayListTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.rememberTabListState
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
import com.nexmusic.app.ui.icon.MusicNote
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Person
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.icon.QueueMusic
import com.nexmusic.app.ui.icon.Shuffle
import com.nexmusic.app.ui.icon.TrendingUp
import com.nexmusic.app.ui.icon.Tune
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
        // Every song here is one the user loved.
        Kind("songs", "Liked Songs"),
        Kind("downloaded", "Downloaded"),
        Kind("recentlyPlayed", "Recently Played"),
        Kind("mostPlayed", "Most Played"),
    )

@Composable
fun LibraryScreen() {
    val vm: LibraryViewModel = koinViewModel()
    val albums by vm.savedAlbums.collectAsStateWithLifecycle()
    val playlists by vm.savedPlaylists.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val icons = listOf(NexIcons.QueueMusic, NexIcons.Person, NexIcons.Album, NexIcons.MusicNote, NexIcons.Downloaded, NexIcons.History, NexIcons.TrendingUp)

    // Albums and playlists the user added, newest first, as one "Recently Added" grid.
    val recentlyAdded =
        (albums.map { Triple(it.inLibrary, it) { navigator.album(it.browseId) } } + playlists.map { Triple(it.inLibrary, it) { navigator.playlist(it.id) } })
            .sortedByDescending { it.first }

    LazyColumn(Modifier.fillMaxSize(), state = rememberTabListState("library"), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { LargeTitle("Library") }
        item { AccountRow { navigator.nav.navigate("account") } }
        kinds.forEachIndexed { i, k -> item { ListLink(icons[i], k.title) { navigator.library(k.route) } } }
        item { ListLink(NexIcons.Tune, "Your Taste") { navigator.editingTaste = true } }
        item { ListLink(NexIcons.Palette, "Appearance") { navigator.nav.navigate("appearance") } }
        item { UpdateRow { navigator.nav.navigate("update") } }
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
    val mine by vm.myPlaylists.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    if (naming) NewPlaylistDialog({ naming = false }) { vm.create(it) }
    val liked by vm.liked.collectAsStateWithLifecycle()
    val downloaded by vm.downloaded.collectAsStateWithLifecycle()
    val recentlyPlayed by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by vm.mostPlayed.collectAsStateWithLifecycle()

    // Downloaded › Import Music Files: audio from the phone, kept as downloaded songs.
    val importer: LocalImport = koinInject()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<String?>(null) }
    val pickFiles =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            importing = true
            scope.launch {
                val n = importer.import(uris)
                importing = false
                imported =
                    when {
                        n == uris.size -> "Imported $n song${if (n == 1) "" else "s"}."
                        n == 0 -> "Couldn't import ${if (uris.size == 1) "that file" else "those files"}. Is it an audio file?"
                        else -> "Imported $n of ${uris.size}; the rest couldn't be read as audio."
                    }
            }
        }

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
                item { ListLink(NexIcons.Add, "New Playlist…", trailing = {}) { naming = true } }
                items(mine, key = { "mine-${it.id}" }) { p ->
                    val n = p.tracks?.size ?: 0
                    SongRow(p.title, "$n song${if (n == 1) "" else "s"}", p.thumbnail, onClick = { navigator.localPlaylist(p.id) })
                }
                val list = playlists
                if (list.isEmpty() && mine.isEmpty()) item { Message("Playlists you make or add show up here.") }
                items(list) { SongRow(it.title, it.author, it.thumbnails.hiRes(), onClick = { navigator.playlist(it.id) }) }
            }
            else -> {
                if (kind == "downloaded") {
                    item {
                        ListLink(NexIcons.AddCircle, if (importing) "Importing…" else "Import Music Files", trailing = {}) {
                            if (!importing) pickFiles.launch(arrayOf("audio/*"))
                        }
                    }
                    imported?.let { item { Text(it, style = NexType.Footnote, color = LocalNex.current.secondaryLabel, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) } }
                }
                val list =
                    when (kind) {
                        "songs" -> liked
                        "downloaded" -> downloaded
                        "recentlyPlayed" -> recentlyPlayed
                        else -> mostPlayed
                    }
                songs(list, title, if (kind == "songs") "Nothing here yet. Tap ♥ on a song to love it." else if (kind == "downloaded") "Songs you download show up here." else "Songs you play show up here.")
            }
        }
    }
}

/** A playlist the user made: its songs, Play / Shuffle, Add Songs, Edit (name, remove, reorder) and Delete at the bottom. */
@Composable
fun LocalPlaylistScreen(id: Long) {
    val vm: LibraryViewModel = koinViewModel()
    val navigator = LocalNavigator.current
    val nex = LocalNex.current
    val mine by vm.myPlaylists.collectAsStateWithLifecycle()
    val list by remember(id) { vm.tracksOf(id) }.collectAsStateWithLifecycle(emptyList())
    val playlist = mine.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }

    // Edit mode works on a copy: Done writes it back in one go, Cancel (or Back) drops it.
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val draft = remember { mutableStateListOf<SongEntity>() }
    // Between Done and the re-read landing, keep showing the edit so the old order doesn't flash back.
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(list) { saving = false }
    BackHandler(editing) { editing = false }
    val listState = rememberLazyListState()
    val reorder = remember { Reorder(listState, draft) }

    val title = if (saving) name else playlist?.title.orEmpty()
    val shown = if (saving) draft else list

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            if (editing) {
                EditBar(doneEnabled = name.isNotBlank(), onCancel = { editing = false }) {
                    val keep = draft.map { it.videoId }.toSet()
                    vm.saveEdits(id, name.trim(), draft.toList(), list.filter { it.videoId !in keep })
                    saving = true
                    editing = false
                }
            } else {
                BackBar(trailing = {
                    BarButton("Edit") {
                        name = playlist?.title.orEmpty()
                        draft.clear()
                        draft.addAll(list)
                        editing = true
                    }
                })
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(playlist?.thumbnail, Modifier.padding(top = 8.dp).size(if (editing) 160.dp else 250.dp).shadow(20.dp, RoundedCornerShape(10.dp)), RoundedCornerShape(10.dp))
                Spacer(Modifier.height(18.dp))
                if (editing) {
                    GlassTextField(name, { name = it }, "Playlist Name", autoFocus = false)
                } else {
                    Text(title, style = NexType.Title2, textAlign = TextAlign.Center, maxLines = 2)
                }
                val n = if (editing) draft.size else shown.size
                Text("$n song${if (n == 1) "" else "s"}", style = NexType.Subhead, color = nex.secondaryLabel, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            }
        }
        if (editing) {
            editableSongs(draft, reorder)
        } else {
            item { ListLink(NexIcons.AddCircle, "Add Songs", trailing = {}) { navigator.addSongs(id) } }
            songs(shown, title, "Tap Add Songs to fill this playlist.") { vm.remove(id, it) }
            item {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                    Text("Delete Playlist", style = NexType.Body, color = Destructive)
                }
            }
        }
    }
    if (confirmDelete) {
        GlassDialog({ confirmDelete = false }, "Delete \"$title\"?", "The songs stay in your library.") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Cancel", Modifier.weight(1f)) { confirmDelete = false }
                GlassButton("Delete", Modifier.weight(1f), color = Destructive) {
                    confirmDelete = false
                    navigator.nav.popBackStack()
                    vm.delete(id)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.songs(
    list: List<SongEntity>,
    title: String,
    empty: String,
    onRemove: ((SongEntity) -> Unit)? = null,
) {
    if (list.isEmpty()) {
        item { Message(empty) }
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
        val track = s.toTrack()
        SongRow(s.title, s.artistName?.joinToString(), s.thumbnails?.hiRes(), onClick = { navigator.player.playList(list.toArrayListTrack(), i, title, null) }, trailing = { DownloadButton(track) }, menu = track, onRemove = onRemove?.let { { it(s) } })
    }
}
