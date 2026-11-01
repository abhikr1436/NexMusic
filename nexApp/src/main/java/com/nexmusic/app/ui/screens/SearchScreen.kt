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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.rememberTabListState
import com.nexmusic.app.ui.components.DownloadButton
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.ListLink
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.pressable
import com.nexmusic.app.ui.icon.Cancel
import com.nexmusic.app.ui.icon.History
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
    val history by vm.history.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var typing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Tapping the Search tab again, like iOS: back to the top, field focused, keyboard up.
    val listState =
        rememberTabListState("search") {
            fieldFocus.requestFocus()
            keyboard?.show()
        }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { LargeTitle("Search") }
        item { SearchField(query, fieldFocus, onChange = { vm.query.value = it }, onSearch = vm::saveQuery, onFocus = { typing = it }) }
        // Suggestions while typing, like Apple Music; picking one runs it and drops the keyboard.
        if (typing && query.isNotBlank()) {
            items(suggestions, key = { "s-$it" }) { s ->
                SuggestionRow(s, query) {
                    vm.query.value = s
                    vm.saveQuery()
                    focusManager.clearFocus()
                }
            }
        }
        if (query.isBlank()) {
            if (history.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.weight(1f)) { SectionHeader("Recent Searches") }
                        Text("Clear", style = NexType.Body, color = Accent, modifier = Modifier.padding(end = 20.dp, bottom = 12.dp).clickable { vm.clearHistory() })
                    }
                }
                items(history) { q -> ListLink(NexIcons.History, q, trailing = {}) { vm.query.value = q } }
            }
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
                        SongRow(s.title.orEmpty(), "Song · " + s.artists?.joinToString { it.name }.orEmpty(), s.thumbnails.best(), onClick = {
                            vm.saveQuery()
                            navigator.player.playRadio(s.toTrack())
                        }, trailing = { DownloadButton(s.toTrack()) }, menu = s.toTrack())
                    }
                    items(v.albums) { a ->
                        SongRow(a.title, "${a.type} · " + a.artists.joinToString { it.name }, a.thumbnails.best(), onClick = {
                            vm.saveQuery()
                            navigator.album(a.browseId)
                        })
                    }
                    items(v.artists) { a ->
                        SongRow(a.artist, "Artist", a.thumbnails.best(), onClick = {
                            vm.saveQuery()
                            navigator.artist(a.browseId)
                        })
                    }
                    items(v.playlists) { p ->
                        SongRow(p.title, "Playlist · ${p.author}", p.thumbnails.best(), onClick = {
                            vm.saveQuery()
                            navigator.playlist(p.browseId)
                        })
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
internal fun SearchField(
    query: String,
    focusRequester: FocusRequester,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocus: (Boolean) -> Unit,
    placeholder: String = "Artists, Songs, Lyrics, and More",
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
            if (query.isEmpty()) Text(placeholder, style = NexType.Body, color = nex.secondaryLabel)
            BasicTextField(
                query,
                onChange,
                singleLine = true,
                textStyle = NexType.Body.copy(color = nex.label),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch()
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocus(it.isFocused) },
            )
        }
        if (query.isNotEmpty()) {
            Icon(NexIcons.Cancel, "Clear", tint = nex.secondaryLabel, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

/** One suggested search: the typed part plain, the completion bold, as iOS shows it. */
@Composable
private fun SuggestionRow(
    suggestion: String,
    typed: String,
    onClick: () -> Unit,
) {
    val nex = LocalNex.current
    val t = typed.trim()
    val text =
        buildAnnotatedString {
            if (suggestion.startsWith(t, ignoreCase = true)) {
                append(suggestion.take(t.length))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.drop(t.length)) }
            } else {
                append(suggestion)
            }
        }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(NexIcons.Search, null, tint = nex.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = NexType.Body, color = nex.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ScopeBar(
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
) = SegmentedBar(SearchScope.entries, selected, { it.label }, onSelect)

/** iOS segmented control. */
@Composable
internal fun <T> SegmentedBar(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val nex = LocalNex.current
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(nex.fill).padding(2.dp),
    ) {
        options.forEach { s ->
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
                Text(label(s), style = NexType.Footnote, color = nex.label, maxLines = 1)
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
