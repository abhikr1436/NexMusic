package com.nexmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.Destructive
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.hiRes
import com.nexmusic.app.ui.icon.AddCircle
import com.nexmusic.app.ui.icon.Check
import com.nexmusic.app.ui.icon.DragHandle
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.RemoveCircle
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

/** An accent text button for the top bar, like iOS's Edit / Done. */
@Composable
fun BarButton(
    text: String,
    bold: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = Text(
    text,
    style = NexType.Body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
    color = if (enabled) Accent else LocalNex.current.tertiaryLabel,
    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
)

/** Edit mode's top bar: Cancel on the left, Done on the right. */
@Composable
fun EditBar(
    doneEnabled: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) = Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    BarButton("Cancel", onClick = onCancel)
    Spacer(Modifier.weight(1f))
    BarButton("Done", bold = true, enabled = doneEnabled, onClick = onDone)
}

private fun editKey(s: SongEntity) = "edit-${s.videoId}"

/** Drag-to-reorder for the edit list: which song is held and how far it has moved from its slot. */
class Reorder(
    private val list: LazyListState,
    private val items: SnapshotStateList<SongEntity>,
) {
    var held by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)

    /** Moves the held song by [dy]; once its middle is over a neighbour, they swap places. */
    // shortcut: no auto-scroll at the list edges, so a long move takes a scroll in between; add it if playlists get long.
    fun drag(dy: Float) {
        val id = held ?: return
        offset += dy
        val visible = list.layoutInfo.visibleItemsInfo
        val me = visible.firstOrNull { it.key == "edit-$id" } ?: return
        val middle = (me.offset + offset + me.size / 2).toInt()
        val over =
            visible.firstOrNull {
                it.key != me.key && (it.key as? String)?.startsWith("edit-") == true && middle in it.offset until it.offset + it.size
            } ?: return
        val from = items.indexOfFirst { it.videoId == id }
        val to = items.indexOfFirst { editKey(it) == over.key }
        if (from < 0 || to < 0) return
        items.add(to, items.removeAt(from))
        // The row now sits in the neighbour's slot; keep it under the finger.
        offset -= over.offset - me.offset
    }
}

/** Every song with a red remove button and a drag handle. */
fun LazyListScope.editableSongs(
    draft: SnapshotStateList<SongEntity>,
    reorder: Reorder,
) {
    if (draft.isEmpty()) item { Message("No songs left. Tap Done to save.") }
    items(draft, key = ::editKey) { s ->
        val nex = LocalNex.current
        val haptics = LocalHapticFeedback.current
        val held = reorder.held == s.videoId
        Column(
            Modifier
                .zIndex(if (held) 1f else 0f)
                .then(if (held) Modifier.graphicsLayer { translationY = reorder.offset } else Modifier.animateItem())
                // Only the held row needs a fill, to cover the rows it passes over; the rest show the page background.
                .background(if (held) nex.elevated else androidx.compose.ui.graphics.Color.Transparent),
        ) {
            Row(Modifier.padding(start = 8.dp, end = 4.dp).height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    NexIcons.RemoveCircle,
                    "Remove ${s.title}",
                    tint = Destructive,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { draft.remove(s) }.padding(10.dp),
                )
                Artwork(s.thumbnails?.hiRes(), Modifier.size(48.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = NexType.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.artistName?.joinToString().orEmpty(), style = NexType.Subhead, color = nex.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    NexIcons.DragHandle,
                    "Reorder ${s.title}",
                    tint = nex.secondaryLabel,
                    modifier =
                        Modifier.size(48.dp).pointerInput(s.videoId) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    reorder.held = s.videoId
                                    reorder.offset = 0f
                                },
                                onDragEnd = { reorder.held = null },
                                onDragCancel = { reorder.held = null },
                            ) { change, dy ->
                                change.consume()
                                reorder.drag(dy)
                            }
                        }.padding(12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(start = 120.dp), thickness = 0.5.dp, color = nex.separator)
        }
    }
}

private enum class Source(val label: String) {
    ForYou("For You"),
    MostPlayed("Most Played"),
    Downloaded("Downloaded"),
    Liked("Liked"),
}

/** Songs to add to playlist [id]: picks from the user's listening, their library, or a search, each with a + button. */
@Composable
fun AddSongsScreen(id: Long) {
    val lib: LibraryViewModel = koinViewModel()
    // Own instances: Home's taste mixes for For You, and a song search that isn't the Search tab's.
    // Keys must differ: one key in one store holds one ViewModel, and a second type would clear the first.
    val home: HomeViewModel = koinViewModel(key = "add-songs-home")
    val search: SearchViewModel = koinViewModel(key = "add-songs-search")
    val navigator = LocalNavigator.current
    val mine by lib.myPlaylists.collectAsStateWithLifecycle()
    val liked by lib.liked.collectAsStateWithLifecycle()
    val downloaded by lib.downloaded.collectAsStateWithLifecycle()
    val mostPlayed by lib.mostPlayed.collectAsStateWithLifecycle()
    val played by home.recentlyPlayed.collectAsStateWithLifecycle()
    val mixes by home.madeForYou.collectAsStateWithLifecycle()
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf(Source.ForYou) }
    // Ticks at once on tap; the playlist's own list catches up when the write lands.
    val added = remember { mutableStateListOf<String>() }
    val inPlaylist = mine.firstOrNull { it.id == id }?.tracks.orEmpty().toSet() + added
    val fieldFocus = remember { FocusRequester() }
    val q = query.trim()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        // Each + saves at once, so there is nothing to cancel: Back or Done just leave.
        item { BackBar(trailing = { BarButton("Done", bold = true) { navigator.nav.popBackStack() } }) }
        item { LargeTitle("Add Songs") }
        item {
            SearchField(
                query,
                fieldFocus,
                onChange = { search.query.value = it },
                onSearch = {},
                onFocus = {},
                placeholder = if (source == Source.ForYou) "Search all songs" else "Search ${source.label}",
            )
        }
        item { SegmentedBar(Source.entries, source, { it.label }) { source = it } }

        // Library lists filter in place; For You searches all of YouTube Music.
        val local =
            when (source) {
                Source.MostPlayed -> mostPlayed
                Source.Downloaded -> downloaded
                Source.Liked -> liked
                Source.ForYou -> null
            }
        val tracks: List<Track>? =
            when {
                local != null ->
                    local
                        .filter { s -> q.isEmpty() || s.title.contains(q, true) || s.artistName.orEmpty().any { it.contains(q, true) } }
                        .map { it.toTrack() }
                q.isNotEmpty() ->
                    when (val r = results) {
                        is Load.Ready -> r.value.songs.map { it.toTrack() }
                        is Load.Failed -> emptyList()
                        else -> null
                    }
                // Discovery Mix (new songs like the ones played most) then Replay (the most played themselves).
                else -> mixes.filterNot { it.radio }.flatMap { it.tracks }.distinctBy { it.videoId }.takeIf { it.isNotEmpty() || played.isEmpty() }
            }
        when {
            tracks == null -> item { Loading() }
            tracks.isEmpty() ->
                item {
                    Message(
                        when {
                            local == null && q.isNotEmpty() -> (results as? Load.Failed)?.message ?: "No songs match \"$q\""
                            q.isNotEmpty() -> "No songs match \"$q\""
                            source == Source.ForYou -> "Play a few songs and picks for you show up here."
                            else -> "Nothing in ${source.label} yet."
                        },
                    )
                }
            else ->
                items(tracks, key = { "${source.name}-${it.videoId}" }) { t ->
                    val has = t.videoId in inPlaylist
                    val add = {
                        if (!has) {
                            added += t.videoId
                            lib.add(id, t)
                        }
                    }
                    SongRow(
                        t.title,
                        t.artists?.joinToString { it.name },
                        t.thumbnails.best(),
                        onClick = add,
                        trailing = {
                            Icon(
                                if (has) NexIcons.Check else NexIcons.AddCircle,
                                if (has) "Added" else "Add ${t.title}",
                                tint = Accent,
                                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = add).padding(12.dp),
                            )
                        },
                        menu = t,
                    )
                }
        }
    }
}
