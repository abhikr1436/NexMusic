package com.nexmusic.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.nexmusic.app.ui.components.GlassOverlay
import com.nexmusic.app.ui.components.GlassOverlayHost
import com.nexmusic.app.ui.components.LocalGlassOverlay
import com.nexmusic.app.ui.components.liquidGlass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import com.nexmusic.app.taste.OnboardingScreen
import com.nexmusic.app.taste.TasteStore
import org.koin.compose.koinInject
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
import com.nexmusic.app.ui.screens.LocalPlaylistScreen
import com.nexmusic.app.ui.screens.MoodScreen
import com.nexmusic.app.ui.screens.NewScreen
import com.nexmusic.app.ui.screens.PlaylistScreen
import com.nexmusic.app.ui.screens.SearchScreen
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import org.koin.androidx.compose.koinViewModel

/** Where pages go and how things start playing — everything a card or row needs. */
class Navigator(
    val nav: NavController,
    val player: PlayerViewModel,
) {
    /** Route of a tab tapped while already open; Search focuses its field on this. */
    val reselected = MutableSharedFlow<String>(extraBufferCapacity = 1)

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

    /** Library › Your Taste reopens the first-launch questions. */
    var editingTaste by mutableStateOf(false)

    fun localPlaylist(id: Long) = nav.navigate("local/$id")

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

/** A tab's list: tapping that tab again scrolls it back to the top, then runs [then]. */
@Composable
fun rememberTabListState(
    route: String,
    then: suspend () -> Unit = {},
): LazyListState {
    val state = rememberLazyListState()
    val navigator = LocalNavigator.current
    val after by rememberUpdatedState(then)
    LaunchedEffect(navigator, route) {
        navigator.reselected.filter { it == route }.collect {
            state.animateScrollToItem(0)
            after()
        }
    }
    return state
}

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
    // What the glass chrome (mini player, tab bar) refracts: the page scrolling beneath it.
    // layerBackdrop must wrap the background, or the glass blurs see-through text and the sharp page shows through.
    val backdrop = rememberLayerBackdrop()
    // Everything on screen, for the glass dialogs and menus drawn over it.
    val root = rememberLayerBackdrop()
    val overlay = remember { GlassOverlay() }

    CompositionLocalProvider(LocalNavigator provides navigator, LocalGlassOverlay provides overlay) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(root).background(nex.background)) {
                NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(nex.background)) {
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
                    composable("local/{id}") { LocalPlaylistScreen(it.arguments?.getString("id")?.toLongOrNull() ?: -1L) }
                }

                // Pages scroll under the clock; fade them out there like iOS's scroll-edge effect. Artist pages keep their full-bleed photo.
                val route by nav.currentBackStackEntryAsState()
                if (route?.destination?.route?.startsWith("artist") != true) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(0.55f to nex.background, 1f to nex.background.copy(alpha = 0f)))
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .height(14.dp),
                    )
                }

                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    if (hasTrack) MiniPlayer(backdrop, onOpen = { showPlayer = true })
                    TabBar(nav, backdrop)
                }

                AnimatedVisibility(
                    visible = showPlayer && hasTrack,
                    enter = slideInVertically(tween(380)) { it } + fadeIn(),
                    exit = slideOutVertically(tween(300)) { it } + fadeOut(),
                ) {
                    NowPlayingScreen(onClose = { showPlayer = false })
                }
                BackHandler(showPlayer) { showPlayer = false }

                // First launch asks what the user likes; Library › Your Taste brings it back.
                val taste: TasteStore = koinInject()
                val onboarded by taste.onboarded.collectAsStateWithLifecycle()
                if (!onboarded || navigator.editingTaste) {
                    OnboardingScreen(onDone = { navigator.editingTaste = false })
                    BackHandler(navigator.editingTaste) { navigator.editingTaste = false }
                }
            }
            GlassOverlayHost(overlay, root)
        }
    }
}

/** Space the mini player and tab bar take, so lists can scroll their last row clear of them. */
val BottomChromePadding = 150.dp

@Composable
private fun TabBar(
    nav: NavController,
    backdrop: Backdrop,
) {
    val nex = LocalNex.current
    val navigator = LocalNavigator.current
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // Tabs stay lit while a page pushed from them is open.
    var lastTab by rememberSaveable { mutableStateOf(Tab.Home.route) }
    Tab.entries.firstOrNull { it.route == current }?.let { lastTab = it.route }
    // iOS 26: the tab bar floats as a frosted glass capsule, and a clear liquid-glass lens slides over it to the selected tab.
    val bar = rememberLayerBackdrop()
    val index = Tab.entries.indexOfFirst { it.route == lastTab }.coerceAtLeast(0)
    BoxWithConstraints(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(62.dp)
            .liquidGlass(backdrop, Capsule(), exported = bar),
    ) {
        val inset = 4.dp
        val slot = (maxWidth - inset * 2) / Tab.entries.size
        val x by animateDpAsState(inset + slot * index, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), label = "tabLens")
        Box(
            Modifier
                .offset(x = x, y = inset)
                .size(slot, maxHeight - inset * 2)
                .drawBackdrop(
                    backdrop = bar,
                    shape = { Capsule() },
                    effects = { lens(10.dp.toPx(), 20.dp.toPx(), chromaticAberration = true) },
                    onDrawSurface = { drawRect(nex.label.copy(alpha = 0.07f)) },
                ),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val selected = lastTab == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            when {
                                current == tab.route -> navigator.reselected.tryEmit(tab.route)
                                // Inside this tab with a page pushed on top: back to the tab's root, like iOS.
                                selected -> nav.popBackStack(tab.route, inclusive = false)
                                else ->
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        // Search always opens on its field, never on the artist or album left open in it.
                                        restoreState = tab != Tab.Search
                                    }
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
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
