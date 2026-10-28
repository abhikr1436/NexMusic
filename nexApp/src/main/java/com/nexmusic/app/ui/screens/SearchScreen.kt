package com.nexmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.components.DownloadButton
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.pressable
import com.nexmusic.app.ui.icon.Cancel
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Search
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchScreen() {
    val vm: SearchViewModel = koinViewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val scope by vm.scope.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { LargeTitle("Search") }
        item { SearchField(query, onChange = { vm.query.value = it }) }
        if (query.isBlank()) {
            item { SectionHeader("Browse Categories") }
            when (val c = categories) {
                Load.Loading -> item { Loading() }
                is Load.Failed -> item { Message(c.message) }
                is Load.Ready ->
                    items(c.value.sections.flatMap { it.items }.chunked(2)) { pair ->
                        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { item -> CategoryTile(item.title, Color(item.stripeColor), Modifier.weight(1f)) { navigator.mood(item.params, item.title) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
            }
        } else {
            item { ScopeBar(scope) { vm.scope.value = it } }
            when (val r = results) {
                null, Load.Loading -> item { Loading() }
                is Load.Failed -> item { Message(r.message) }
                is Load.Ready -> {
                    val v = r.value
                    items(v.songs) { s ->
                        SongRow(s.title.orEmpty(), "Song · " + s.artists?.joinToString { it.name }.orEmpty(), s.thumbnails.best(), onClick = { navigator.player.playRadio(s.toTrack()) }, trailing = { DownloadButton(s.toTrack()) })
                    }
                    items(v.albums) { a ->
                        SongRow(a.title, "${a.type} · " + a.artists.joinToString { it.name }, a.thumbnails.best(), onClick = { navigator.album(a.browseId) })
                    }
                    items(v.artists) { a ->
                        SongRow(a.artist, "Artist", a.thumbnails.best(), onClick = { navigator.artist(a.browseId) })
                    }
                    items(v.playlists) { p ->
                        SongRow(p.title, "Playlist · ${p.author}", p.thumbnails.best(), onClick = { navigator.playlist(p.browseId) })
                    }
                    if (v.songs.isEmpty() && v.albums.isEmpty() && v.artists.isEmpty() && v.playlists.isEmpty()) {
                        item { Message("No results for \"$query\"") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
) {
    val nex = LocalNex.current
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(nex.fill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NexIcons.Search, null, tint = nex.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Artists, Songs, Lyrics, and More", style = NexType.Body, color = nex.secondaryLabel)
            BasicTextField(
                query,
                onChange,
                singleLine = true,
                textStyle = NexType.Body.copy(color = nex.label),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(NexIcons.Cancel, "Clear", tint = nex.secondaryLabel, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

/** iOS segmented control. */
@Composable
private fun ScopeBar(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
) {
    val nex = LocalNex.current
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(nex.fill).padding(2.dp),
    ) {
        SearchScope.entries.forEach { s ->
            val on = s == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) nex.elevated else Color.Transparent)
                    .clickable { onSelect(s) },
                contentAlignment = Alignment.Center,
            ) {
                Text(s.label, style = NexType.Footnote, color = nex.label)
            }
        }
    }
}

@Composable
private fun CategoryTile(
    title: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(110.dp)
            .pressable(onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 1f))
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(title, style = NexType.Headline, color = Color.White, maxLines = 2)
    }
}
