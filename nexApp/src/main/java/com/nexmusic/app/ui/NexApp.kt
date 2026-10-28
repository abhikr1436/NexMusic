package com.nexmusic.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.maxrave.domain.data.model.home.Content
import com.maxrave.domain.data.model.streams.YouTubeWatchEndpoint
import com.maxrave.domain.utils.isRadioQueueId
import com.maxrave.domain.utils.toTrack
import com.nexmusic.app.player.MiniPlayer
import com.nexmusic.app.player.NowPlayingScreen
import com.nexmusic.app.player.PlayerViewModel
import com.nexmusic.app.ui.icon.GridView
import com.nexmusic.app.ui.icon.Home
import com.nexmusic.app.ui.icon.LibraryMusic
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Search
import com.nexmusic.app.ui.screens.AlbumScreen
import com.nexmusic.app.ui.screens.ArtistScreen
import com.nexmusic.app.ui.screens.HomeScreen
import com.nexmusic.app.ui.screens.LibraryListScreen
import com.nexmusic.app.ui.screens.LibraryScreen
import com.nexmusic.app.ui.screens.MoodScreen
import com.nexmusic.app.ui.screens.NewScreen
import com.nexmusic.app.ui.screens.PlaylistScreen
import com.nexmusic.app.ui.screens.SearchScreen
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import org.koin.androidx.compose.koinViewModel

/** Where pages go and how things start playing — everything a card or row needs. */
class Navigator(
    val nav: NavController,
    val player: PlayerViewModel,
) {
    fun album(id: String) = nav.navigate("album/${Uri.encode(id)}")

    fun playlist(id: String) {
        // Mixes and radios are endless queues with no page; Apple Music just plays them.
        if (id.isRadioQueueId()) player.playEndpoint(YouTubeWatchEndpoint(playlistId = id.removePrefix("VL")), "Mix") else nav.navigate("playlist/${Uri.encode(id)}")
    }

    fun artist(id: String) = nav.navigate("artist/${Uri.encode(id)}")

    fun mood(
        params: String,
        title: String,
    ) = nav.navigate("mood/${Uri.encode(params)}/${Uri.encode(title)}")

    fun library(kind: String) = nav.navigate("library/$kind")

    fun open(content: Content) {
        val browseId = content.browseId
        when {
            content.videoId != null -> player.playRadio(content.toTrack())
            browseId != null && (browseId.startsWith("UC") || browseId.startsWith("FEmusic_library_privately_owned_artist")) -> artist(browseId)
            browseId != null && browseId.startsWith("MPRE") -> album(browseId)
            content.playlistId != null -> playlist(content.playlistId!!)
            browseId != null -> playlist(browseId)
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }

private enum class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", NexIcons.Home),
    New("new", "New", NexIcons.GridView),
    Library("library", "Library", NexIcons.LibraryMusic),
    Search("search", "Search", NexIcons.Search),
}

@Composable
fun NexApp() {
    val nav = rememberNavController()
    val player: PlayerViewModel = koinViewModel()
    val navigator = remember(nav, player) { Navigator(nav, player) }
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val hasTrack = nowPlaying.mediaItem.mediaId.isNotEmpty()
    val nex = LocalNex.current

    CompositionLocalProvider(LocalNavigator provides navigator) {
        Box(Modifier.fillMaxSize().background(nex.background)) {
            NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize()) {
                composable(Tab.Home.route) { HomeScreen() }
                composable(Tab.New.route) { NewScreen() }
                composable(Tab.Library.route) { LibraryScreen() }
                composable(Tab.Search.route) { SearchScreen() }
                composable("album/{id}") { AlbumScreen(Uri.decode(it.arguments?.getString("id").orEmpty())) }
                composable("playlist/{id}") { PlaylistScreen(Uri.decode(it.arguments?.getString("id").orEmpty())) }
                composable("artist/{id}") { ArtistScreen(Uri.decode(it.arguments?.getString("id").orEmpty())) }
                composable("mood/{params}/{title}") {
                    MoodScreen(Uri.decode(it.arguments?.getString("params").orEmpty()), Uri.decode(it.arguments?.getString("title").orEmpty()))
                }
                composable("library/{kind}") { LibraryListScreen(it.arguments?.getString("kind").orEmpty()) }
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (hasTrack) MiniPlayer(onOpen = { showPlayer = true })
                TabBar(nav)
            }

            AnimatedVisibility(
                visible = showPlayer && hasTrack,
                enter = slideInVertically(tween(380)) { it } + fadeIn(),
                exit = slideOutVertically(tween(300)) { it } + fadeOut(),
            ) {
                NowPlayingScreen(onClose = { showPlayer = false })
            }
            BackHandler(showPlayer) { showPlayer = false }
        }
    }
}

/** Space the mini player and tab bar take, so lists can scroll their last row clear of them. */
val BottomChromePadding = 150.dp

@Composable
private fun TabBar(nav: NavController) {
    val nex = LocalNex.current
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // Tabs stay lit while a page pushed from them is open.
    var lastTab by rememberSaveable { mutableStateOf(Tab.Home.route) }
    Tab.entries.firstOrNull { it.route == current }?.let { lastTab = it.route }
    val barColor = if (Build.VERSION.SDK_INT >= 31) nex.grouped.copy(alpha = 0.94f) else nex.grouped
    Column(Modifier.background(barColor).windowInsetsPadding(WindowInsets.navigationBars)) {
        HorizontalDivider(thickness = 0.5.dp, color = nex.separator)
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Tab.entries.forEach { tab ->
                val selected = lastTab == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (current == tab.route) return@clickable
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val tint = if (selected) Accent else nex.secondaryLabel
                    Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = NexType.Tab, color = tint)
                }
            }
        }
    }
}
