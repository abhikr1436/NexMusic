package com.nexmusic.app.island

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.nexmusic.app.MainActivity
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.icon.FastForward
import com.nexmusic.app.ui.icon.FastRewind
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.icon.Pause
import com.nexmusic.app.ui.icon.PlayArrow
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.NexTheme
import com.nexmusic.app.ui.theme.NexType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.math.roundToInt

/**
 * iOS-style Dynamic Island: a black pill wrapped around the front camera while music plays and the app is in the background.
 * Tap expands it into a card with controls, long-press opens the app.
 *
 * It lives in [IslandService], an accessibility service, because only accessibility overlays sit above the status bar
 * and take touches there, which is where the camera is. The service being on *is* the setting.
 */
object DynamicIsland {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var service: AccessibilityService? = null
    private var scope: CoroutineScope? = null
    private var shown: Pair<FrameLayout, OverlayOwner>? = null
    private val expanded = MutableStateFlow(false)

    /** Only the user can turn an accessibility service on, so this opens the system page for it. */
    fun turnOn(context: Context) = context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun turnOff() {
        service?.disableSelf()
    }

    internal fun attach(s: AccessibilityService) {
        service = s
        _enabled.value = true
        val handler: MediaPlayerHandler = GlobalContext.get().get()
        scope =
            MainScope().apply {
                launch {
                    combine(
                        ProcessLifecycleOwner.get().lifecycle.currentStateFlow,
                        handler.controlState,
                        expanded,
                    ) { process, control, open -> !process.isAtLeast(Lifecycle.State.STARTED) && (control.isPlaying || open) }
                        .distinctUntilChanged()
                        .collect { if (it) show(s, handler) else hide() }
                }
            }
    }

    /** Rotation or a display change moves the camera, so the island is placed again. */
    internal fun relayout() {
        val s = service ?: return
        if (shown == null) return
        val open = expanded.value
        hide()
        show(s, GlobalContext.get().get())
        expanded.value = open
    }

    internal fun detach() {
        scope?.cancel()
        scope = null
        hide()
        service = null
        _enabled.value = false
    }

    private fun show(
        s: AccessibilityService,
        handler: MediaPlayerHandler,
    ) {
        if (shown != null) return
        val wm = s.getSystemService(WindowManager::class.java)
        val density = s.resources.displayMetrics.density
        val camera = cameraBounds(wm)
        // The pill is the camera plus a thin black rim; with no camera along the top (or in landscape) it is a plain 36dp pill.
        val heightPx = maxOf((36 * density).roundToInt(), (camera?.height() ?: 0) + (10 * density).roundToInt())
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    // Centred on the camera both ways; the expanded card grows down from the same top edge.
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    x = if (camera != null) camera.centerX() - s.resources.displayMetrics.widthPixels / 2 else 0
                    y = if (camera != null) (camera.centerY() - heightPx / 2).coerceAtLeast(0) else (6 * density).roundToInt()
                    if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        val owner = OverlayOwner()
        val root =
            object : FrameLayout(s) {
                // A tap anywhere else folds the expanded card back into the pill.
                override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                    if (e.action == MotionEvent.ACTION_OUTSIDE) {
                        expanded.value = false
                        return true
                    }
                    return super.dispatchTouchEvent(e)
                }
            }
        root.setViewTreeLifecycleOwner(owner)
        root.setViewTreeSavedStateRegistryOwner(owner)
        val pillHeight = (heightPx / density).dp
        // The expanded card's content starts below the camera, which stays in the black band at its top like on iOS.
        val cardTop = maxOf(18f, (camera?.let { it.bottom - params.y } ?: 0) / density + 6).dp
        val gap = ((camera?.width() ?: 0) / density + 24).dp
        root.addView(
            ComposeView(s).apply {
                setContent {
                    val open by expanded.collectAsState()
                    NexTheme {
                        Island(handler, open, pillHeight, gap, cardTop, { expanded.value = true }) {
                            expanded.value = false
                            s.startActivity(Intent(s, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            },
        )
        wm.addView(root, params)
        shown = root to owner
    }

    private fun hide() {
        shown?.let { (root, owner) ->
            // The window may already be gone with the service.
            runCatching { root.context.getSystemService(WindowManager::class.java).removeView(root) }
            owner.destroy()
        }
        shown = null
        expanded.value = false
    }

    /** The front camera's outline in screen pixels, or null when there is none along the top edge. */
    private fun cameraBounds(wm: WindowManager): Rect? {
        val cutout =
            when {
                Build.VERSION.SDK_INT >= 30 -> wm.currentWindowMetrics.windowInsets.displayCutout
                Build.VERSION.SDK_INT >= 29 -> @Suppress("DEPRECATION") wm.defaultDisplay.cutout
                else -> null
            } ?: return null
        val box = cutout.boundingRectTop.takeIf { !it.isEmpty } ?: return null
        // The bounding box is stretched to the status bar's height; the cutout path is the real hole, when the phone provides one.
        if (Build.VERSION.SDK_INT >= 31) {
            val path = RectF().also { cutout.cutoutPath?.computeBounds(it, true) }
            if (!path.isEmpty && box.contains(path.centerX().roundToInt(), path.centerY().roundToInt())) {
                return Rect(path.left.roundToInt(), path.top.roundToInt(), path.right.roundToInt(), path.bottom.roundToInt())
            }
        }
        return box
    }
}

@Composable
private fun Island(
    handler: MediaPlayerHandler,
    expanded: Boolean,
    pillHeight: Dp,
    gap: Dp,
    cardTop: Dp,
    onExpand: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val state by handler.nowPlayingState.collectAsState()
    val control by handler.controlState.collectAsState()
    val scope = rememberCoroutineScope()
    val meta = state.mediaItem.metadata
    val corner by animateDpAsState(if (expanded) 34.dp else pillHeight / 2, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "corner")
    fun send(event: PlayerEvent) = scope.launch { handler.onPlayerEvent(event) }

    AnimatedContent(
        expanded,
        transitionSpec = { fadeIn(tween(220, 90)) togetherWith fadeOut(tween(90)) },
        modifier =
            Modifier
                .clip(RoundedCornerShape(corner))
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }, onLongPress = { onOpenApp() }) },
        label = "island",
    ) { open ->
        if (open) {
            Column(Modifier.width(360.dp).padding(start = 18.dp, end = 18.dp, top = cardTop, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(meta.artworkUri, Modifier.size(56.dp), RoundedCornerShape(12.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(meta.title.orEmpty(), style = NexType.Headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(meta.artist.orEmpty(), style = NexType.Subhead, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Bars(control.isPlaying)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IslandButton(NexIcons.FastRewind, "Previous") { send(PlayerEvent.Previous) }
                    IslandButton(if (control.isPlaying) NexIcons.Pause else NexIcons.PlayArrow, if (control.isPlaying) "Pause" else "Play") { send(PlayerEvent.PlayPause) }
                    IslandButton(NexIcons.FastForward, "Next") { send(PlayerEvent.Next) }
                }
            }
        } else {
            Row(Modifier.height(pillHeight).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(meta.artworkUri, Modifier.size(24.dp), CircleShape)
                // The middle stays empty for the camera: art on one side, activity on the other, like iOS.
                Spacer(Modifier.width(gap))
                Bars(control.isPlaying)
            }
        }
    }
}

@Composable
private fun IslandButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, label, Modifier.size(34.dp), tint = Color.White)
    }
}

/** The equalizer bars iOS shows on the right of the island; they settle low when paused. */
@Composable
private fun Bars(playing: Boolean) {
    val t = rememberInfiniteTransition(label = "bars")
    Row(Modifier.size(22.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for (ms in intArrayOf(420, 300, 520, 360)) {
            val h by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(ms), RepeatMode.Reverse), label = "bar")
            Box(Modifier.width(3.dp).fillMaxHeight(if (playing) h else 0.3f).clip(CircleShape).background(Accent))
        }
    }
}

/** Compose needs a lifecycle and saved-state owner; an overlay window has no activity to borrow them from. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry get() = saved.savedStateRegistry

    init {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
