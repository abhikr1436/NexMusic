package com.nexmusic.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
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
import com.nexmusic.app.ui.screens.AccountScreen
import com.nexmusic.app.ui.screens.AddSongsScreen
import com.nexmusic.app.ui.screens.AppearanceScreen
import com.nexmusic.app.ui.screens.UpdateScreen
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
import com.nexmusic.app.ui.theme.LocalBackdrop
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

    fun addSongs(playlistId: Long) = nav.navigate("local/$playlistId/add")

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
            val page = LocalBackdrop.current
            Box(Modifier.fillMaxSize().layerBackdrop(root).background(page)) {
                NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(page)) {
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
                    composable("account") { AccountScreen() }
                    composable("appearance") { AppearanceScreen() }
                    composable("update") { UpdateScreen() }
                    composable("local/{id}/add") { AddSongsScreen(it.arguments?.getString("id")?.toLongOrNull() ?: -1L) }
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
        val select = { tab: Tab ->
            when {
                current == tab.route -> navigator.reselected.tryEmit(tab.route)
                // Inside this tab with a page pushed on top: back to the tab's root, like iOS.
                lastTab == tab.route -> nav.popBackStack(tab.route, inclusive = false)
                else ->
                    nav.navigate(tab.route) {
                        // Every tab opens on its root, never on the page left open in it.
                        popUpTo(nav.graph.findStartDestination().id)
                        launchSingleTop = true
                    }
            }
            Unit
        }
        val inset = 4.dp
        val slot = (maxWidth - inset * 2) / Tab.entries.size
        val insetPx = with(LocalDensity.current) { inset.toPx() }
        val slotPx = with(LocalDensity.current) { slot.toPx() }
        // Lens position in tabs (0 = Home), so it is independent of screen width.
        val lensX = remember { Animatable(index.toFloat()) }
        var dragging by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val settle = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
        LaunchedEffect(index) { if (!dragging) lensX.animateTo(index.toFloat(), settle) }
        // iOS lifts the lens while it is held, and it stretches the way it moves.
        val grow by animateFloatAsState(if (dragging) 1.14f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "lensGrow")
        val currentIndex by rememberUpdatedState(index)
        val currentSelect by rememberUpdatedState(select)
        val hovered = if (dragging) lensX.value.roundToInt() else index
        Box(
            Modifier
                .offset { IntOffset((insetPx + lensX.value * slotPx).roundToInt(), insetPx.roundToInt()) }
                .size(slot, maxHeight - inset * 2)
                .drawBackdrop(
                    backdrop = bar,
                    shape = { Capsule() },
                    effects = { lens(10.dp.toPx(), 20.dp.toPx(), chromaticAberration = true) },
                    layerBlock = {
                        val stretch = (abs(lensX.velocity) / 40f).coerceAtMost(0.12f)
                        scaleX = grow + stretch
                        scaleY = grow - stretch / 2
                    },
                    onDrawSurface = { drawRect(nex.label.copy(alpha = if (dragging) 0.12f else 0.07f)) },
                ),
        )
        Row(
            Modifier
                .fillMaxSize()
                .pointerInput(slotPx) {
                    val last = Tab.entries.lastIndex.toFloat()
                    var target = 0f
                    // Chasing the finger with a stiff spring (not snapping) keeps the lens fluid, like liquid.
                    val follow = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh)
                    fun under(x: Float) = ((x - insetPx) / slotPx - 0.5f).coerceIn(0f, last)
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            dragging = true
                            target = under(start.x)
                            scope.launch { lensX.animateTo(target, follow) }
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            target = (target + dx / slotPx).coerceIn(0f, last)
                            scope.launch { lensX.animateTo(target, follow) }
                        },
                        onDragEnd = {
                            dragging = false
                            val to = target.roundToInt()
                            scope.launch { lensX.animateTo(to.toFloat(), settle) }
                            // Sliding back onto the open tab is not a re-tap: no scroll-to-top.
                            if (to != currentIndex) currentSelect(Tab.entries[to])
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { lensX.animateTo(currentIndex.toFloat(), settle) }
                        },
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { i, tab ->
                val selected = hovered == i
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { select(tab) },
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
