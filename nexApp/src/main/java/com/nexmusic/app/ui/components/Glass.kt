package com.nexmusic.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import kotlin.math.roundToInt

/** iOS's destructive red. */
val Destructive = Color(0xFFFF3B30)

/**
 * Dialogs and menus are drawn inside the app's own window, above everything, instead of in a Dialog or Popup window:
 * a separate window can't see the app, so its glass would have nothing to blur.
 */
class GlassOverlay {
    internal val entries = mutableStateListOf<Entry>()

    internal class Entry(
        val onDismiss: State<() -> Unit>,
        val content: @Composable () -> Unit,
    )
}

val LocalGlassOverlay = staticCompositionLocalOf { GlassOverlay() }

/** The whole app as drawn, for overlay glass to blur. */
private val LocalAppBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** The frosted pane a control sits on, for liquid-glass controls to refract. */
private val LocalGlassPane = staticCompositionLocalOf<Backdrop?> { null }

/** Draws the open dialogs and menus over the app. [backdrop] must record everything beneath it. */
@Composable
fun GlassOverlayHost(
    overlay: GlassOverlay,
    backdrop: Backdrop,
) {
    val nex = LocalNex.current
    overlay.entries.forEachIndexed { i, entry ->
        key(entry) {
            val shown = remember { Animatable(0f) }
            LaunchedEffect(Unit) { shown.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 500f)) }
            if (i == overlay.entries.lastIndex) BackHandler { entry.onDismiss.value() }
            Box(Modifier.fillMaxSize().imePadding()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = shown.value }
                        .background(Color.Black.copy(alpha = if (nex.isDark) 0.35f else 0.2f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { entry.onDismiss.value() },
                )
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = shown.value
                        scaleX = 0.9f + 0.1f * shown.value
                        scaleY = 0.9f + 0.1f * shown.value
                    },
                ) {
                    CompositionLocalProvider(LocalAppBackdrop provides backdrop) { entry.content() }
                }
            }
        }
    }
}

/** Lets glass cards, buttons and fields in [content] blur [backdrop], for full-screen glass outside the overlay. */
@Composable
fun ProvideGlassBackdrop(
    backdrop: Backdrop,
    content: @Composable () -> Unit,
) = CompositionLocalProvider(LocalAppBackdrop provides backdrop, content = content)

/** Puts [content] in the overlay for as long as the caller stays composed, like AlertDialog. */
@Composable
private fun InOverlay(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val overlay = LocalGlassOverlay.current
    val dismiss = rememberUpdatedState(onDismiss)
    val body = rememberUpdatedState(content)
    DisposableEffect(overlay) {
        val entry = GlassOverlay.Entry(dismiss) { body.value() }
        overlay.entries += entry
        onDispose { overlay.entries -= entry }
    }
}

/** Frosted glass: heavy blur and a thick tint so text on it stays readable, with a refracting rim. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(20.dp),
    corner: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nex = LocalNex.current
    val pane = rememberLayerBackdrop()
    val behind = LocalAppBackdrop.current
    val tint = nex.elevated.copy(alpha = if (android.os.Build.VERSION.SDK_INT < 31) 0.97f else if (nex.isDark) 0.7f else 0.78f)
    Column(
        modifier
            .then(if (behind != null) Modifier.liquidGlass(behind, RoundedRectangle(corner), tint, exported = pane) else Modifier.clip(RoundedRectangle(corner)).background(nex.elevated))
            // Taps on the card mustn't fall through to the scrim and dismiss it.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(padding),
    ) {
        CompositionLocalProvider(LocalGlassPane provides pane, LocalContentColor provides nex.label) { content() }
    }
}

/** Liquid glass on frosted glass: a clear, refracting capsule over the card it sits on. */
private fun Modifier.liquidOnPane(
    pane: Backdrop?,
    tint: Color,
): Modifier =
    if (pane == null) {
        clip(CircleShape).background(tint)
    } else {
        drawBackdrop(
            backdrop = pane,
            shape = { Capsule() },
            effects = {
                vibrancy()
                lens(8.dp.toPx(), 16.dp.toPx())
            },
            shadow = null,
            onDrawSurface = { drawRect(tint) },
        )
    }

@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Accent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val nex = LocalNex.current
    Box(
        modifier
            .height(48.dp)
            .liquidOnPane(LocalGlassPane.current, nex.label.copy(alpha = 0.08f))
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = NexType.Headline, color = if (enabled) color else nex.tertiaryLabel)
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    val nex = LocalNex.current
    val focus = remember { FocusRequester() }
    // A field in the overlay can only be focused once it is composed there.
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value,
        onValueChange,
        singleLine = true,
        textStyle = NexType.Body.copy(color = nex.label),
        cursorBrush = SolidColor(Accent),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onSearch = { onDone() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth().height(48.dp).liquidOnPane(LocalGlassPane.current, nex.fill).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = NexType.Body, color = nex.tertiaryLabel)
                field()
            }
        },
    )
}

/** iOS-style alert: title, optional message, any body, then a row or stack of glass buttons. */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) = InOverlay(onDismiss) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        GlassCard(Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
            Text(title, style = NexType.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (message != null) Text(message, style = NexType.Subhead, color = LocalNex.current.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.height(16.dp))
            content()
        }
    }
}

/** A long-press menu next to the thing pressed ([anchor], in window coordinates), like iOS's context menu. */
@Composable
fun GlassMenu(
    anchor: Rect,
    onDismiss: () -> Unit,
    items: List<Pair<String, () -> Unit>>,
    destructive: Set<String> = emptySet(),
) = InOverlay(onDismiss) {
    val nex = LocalNex.current
    Layout(
        content = {
            GlassCard(Modifier.widthIn(min = 220.dp, max = 280.dp), padding = PaddingValues(vertical = 6.dp), corner = 22.dp) {
                items.forEachIndexed { i, (label, action) ->
                    Text(
                        label,
                        style = NexType.Body,
                        color = if (label in destructive) Destructive else nex.label,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    action()
                                }.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                    if (i != items.lastIndex) HorizontalDivider(thickness = 0.5.dp, color = nex.separator.copy(alpha = 0.6f))
                }
            }
        },
    ) { measurables, constraints ->
        val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 16.dp.roundToPx()
        // Below the pressed row when it fits, otherwise above it.
        val below = anchor.bottom.roundToInt()
        val y = if (below + card.height + margin <= constraints.maxHeight) below else (anchor.top.roundToInt() - card.height).coerceAtLeast(margin)
        layout(constraints.maxWidth, constraints.maxHeight) { card.place(margin, y) }
    }
}
