package com.nexmusic.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.data.model.home.HomeItem
import com.maxrave.domain.utils.toArrayListTrack
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.ui.BottomChromePadding
import com.nexmusic.app.ui.LocalNavigator
import com.nexmusic.app.ui.rememberTabListState
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.CoverCard
import com.nexmusic.app.ui.components.LargeTitle
import com.nexmusic.app.ui.components.Loading
import com.nexmusic.app.ui.components.Message
import com.nexmusic.app.ui.components.SectionHeader
import com.nexmusic.app.ui.components.SongRow
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.hiRes
import com.nexmusic.app.ui.components.pressable
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

private val Content.subtitle: String?
    get() = artists?.joinToString { it.name }?.takeIf { it.isNotBlank() } ?: description

private val Content.isArtist: Boolean
    get() = videoId == null && browseId?.startsWith("UC") == true

@Composable
fun HomeScreen() {
    val vm: HomeViewModel = koinViewModel()
    LaunchedEffect(Unit) { vm.ensureHome() }
    val feed by vm.feed.collectAsStateWithLifecycle()
    val recent by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val mixes by vm.madeForYou.collectAsStateWithLifecycle()
    FeedPage("Home", "home", feed, onRetry = vm::refresh) {
        if (mixes.isNotEmpty()) {
            item(key = "h-made") { SectionHeader("Made For You") }
            item(key = "c-made") {
                val player = LocalNavigator.current.player
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(mixes) { m ->
                        CoverCard(m.art, m.title, m.subtitle, 160.dp) {
                            if (m.radio) player.playRadio(m.tracks.first()) else player.playList(m.tracks, 0, m.title, null)
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "h-recent") { SectionHeader("Recently Played") }
            item(key = "c-recent") {
                val player = LocalNavigator.current.player
                val shown = recent.take(20)
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown.size) { i ->
                        val s = shown[i]
                        CoverCard(s.thumbnails?.hiRes(), s.title, s.artistName?.joinToString(), 160.dp, videoId = s.videoId) { player.playList(recent.toArrayListTrack(), i, "Recently Played", null) }
                    }
                }
            }
        }
    }
}

@Composable
fun NewScreen() {
    val vm: HomeViewModel = koinViewModel()
    LaunchedEffect(Unit) { vm.ensureNew() }
    val feed by vm.newReleases.collectAsStateWithLifecycle()
    val forYou by vm.newForYou.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    FeedPage("New", "new", feed, onRetry = vm::refreshNew) {
        if (forYou.isNotEmpty()) {
            item(key = "h-foryou") { SectionHeader("New From Your Artists", "Based on what you've been playing") }
            item(key = "c-foryou") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(forYou.size) { i ->
                        val r = forYou[i]
                        CoverCard(r.art, r.title, listOf(r.artist, r.year).filter { it.isNotBlank() }.joinToString(" · "), 160.dp) { navigator.album(r.browseId) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPage(
    title: String,
    route: String,
    feed: Load<List<HomeItem>>,
    onRetry: () -> Unit,
    personal: LazyListScope.() -> Unit = {},
) {
    // Only a pull shows the indicator; the first load has its own spinner in the list.
    var pulled by remember { mutableStateOf(false) }
    val refreshing = pulled && feed is Load.Loading
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        refreshing,
        onRefresh = {
            pulled = true
            onRetry()
        },
        state = pull,
        indicator = {
            PullToRefreshDefaults.Indicator(state = pull, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars))
        },
    ) { FeedList(title, route, feed, onRetry, personal) }
}

@Composable
private fun FeedList(
    title: String,
    route: String,
    feed: Load<List<HomeItem>>,
    onRetry: () -> Unit,
    personal: LazyListScope.() -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), state = rememberTabListState(route), contentPadding = PaddingValues(bottom = BottomChromePadding)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { LargeTitle(title) }
        personal()
        when (feed) {
            Load.Loading -> item { Loading() }
            is Load.Failed -> item { Message("Couldn't load. Tap to try again.", Modifier.clickable(onClick = onRetry)) }
            is Load.Ready -> {
                val sections = feed.value.filter { it.contents.any { c -> c != null } }
                // Apple leads with one large "Top Picks" shelf; the first card shelf gets that treatment.
                val heroIndex = sections.indexOfFirst { it.itemsPerColumn == null && it.contents.firstOrNull()?.isArtist == false }
                sections.forEachIndexed { i, section ->
                    shelf(section, hero = i == heroIndex)
                }
            }
        }
    }
}

private fun LazyListScope.shelf(
    section: HomeItem,
    hero: Boolean,
) {
    val contents = section.contents.filterNotNull()
    item(key = "h-${section.title}") { SectionHeader(section.title, section.subtitle) }
    item(key = "c-${section.title}") {
        val navigator = LocalNavigator.current
        when {
            section.itemsPerColumn != null -> SongGrid(contents)
            hero ->
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(contents) { HeroCard(it) { navigator.open(it) } }
                }
            else ->
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(contents) {
                        CoverCard(it.thumbnails.best(), it.title, it.subtitle, 160.dp, circle = it.isArtist) { navigator.open(it) }
                    }
                }
        }
    }
}

/** Song shelves (Quick Picks and friends) page sideways in columns of four, like Apple's song lists. */
@Composable
private fun SongGrid(contents: List<Content>) {
    val navigator = LocalNavigator.current
    LazyHorizontalGrid(GridCells.Fixed(4), Modifier.height(244.dp)) {
        items(contents) { c ->
            Box(Modifier.width(330.dp)) {
                SongRow(c.title, c.subtitle, c.thumbnails.best(), onClick = { navigator.open(c) }, menu = c.videoId?.let { c.toTrack() })
            }
        }
    }
}

@Composable
private fun HeroCard(
    content: Content,
    onClick: () -> Unit,
) {
    Box(Modifier.width(280.dp).aspectRatio(0.78f).pressable(onClick)) {
        Artwork(content.thumbnails.best(), Modifier.fillMaxSize(), RoundedCornerShape(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(content.title, style = NexType.Title3, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content.subtitle?.let { Text(it, style = NexType.Subhead, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
