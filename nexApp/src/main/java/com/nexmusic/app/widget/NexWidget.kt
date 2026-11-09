package com.nexmusic.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaButtonReceiver
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import coil3.toBitmap
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.PlayerEvent
import com.nexmusic.app.MainActivity
import com.nexmusic.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/** The three looks a widget can take; chosen per widget in [WidgetConfigActivity]. */
enum class WidgetTheme(
    val label: String,
    val background: Int,
    val button: Int,
    val text: Color,
    val secondary: Color,
    val buttonIcon: Color,
) {
    Light("Light", R.drawable.widget_bg_light, R.drawable.widget_btn_light, Color.Black, Color(0x993C3C43), Color.White),
    Dark("Dark", R.drawable.widget_bg_dark, R.drawable.widget_btn_dark, Color.White, Color(0x99EBEBF5), Color.Black),
    Glass("Liquid Glass", R.drawable.widget_bg_glass, R.drawable.widget_btn_glass, Color.White, Color(0xD9FFFFFF), Color.White),
    ;

    companion object {
        val KEY = stringPreferencesKey("theme")

        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: Dark
    }
}

/**
 * Glass opacity, 1 (most opaque) → 4 (fully clear): a flat white wash over the wallpaper, no rim or shading.
 * Names are what existing widgets have stored, so they stay.
 */
enum class GlassLevel(
    val label: String,
    val opacity: Float,
    // One drawable per level: Glance drops the alpha of a ColorFilter tint, so the wash can't be a tinted panel.
    val background: Int,
) {
    Frosted("1", 0.40f, R.drawable.widget_bg_glass_1),
    Misty("2", 0.26f, R.drawable.widget_bg_glass_2),
    Clear("3", 0.13f, R.drawable.widget_bg_glass_3),
    Liquid("4", 0f, R.drawable.widget_bg_glass),
    ;

    val tint get() = Color.White.copy(alpha = opacity)

    companion object {
        val KEY = stringPreferencesKey("glass")

        // Liquid is how glass widgets looked before the setting existed.
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: Liquid
    }
}


class NexWidget : GlanceAppWidget() {
    // Exact, not Responsive: the layouts size their artwork from the real cell size, which varies a lot by launcher.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val handler = GlobalContext.get().get<MediaPlayerHandler>()
        provideContent {
            val now by handler.nowPlayingState.collectAsState()
            val control by handler.controlState.collectAsState()
            val meta = now.mediaItem.metadata
            val artUrl = meta.artworkUri
            val art by produceState<Bitmap?>(null, artUrl) {
                // Clear first, or the last song's cover stays up while this one loads (or if it never does).
                value = null
                value = artUrl?.let { loadArt(context, it) }
            }
            val playing = now.mediaItem.mediaId.isNotEmpty()
            val prefs = currentState<Preferences>()
            WidgetContent(
                theme = WidgetTheme.of(prefs[WidgetTheme.KEY]),
                glass = GlassLevel.of(prefs[GlassLevel.KEY]),
                state =
                    NowPlaying(
                        title = if (playing) meta.title.orEmpty() else "Not Playing",
                        artist = if (playing) meta.artist.orEmpty() else "NexMusic",
                        art = art,
                        hasTrack = playing,
                        isPlaying = control.isPlaying,
                        isLiked = control.isLiked,
                    ),
            )
        }
    }

    private suspend fun loadArt(
        context: Context,
        url: String,
    ): Bitmap? =
        runCatching {
            // Widgets cross into the launcher's process, so keep the bitmap small and software-backed.
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(400)
                    .allowHardware(false)
                    // Rounded in the bitmap: Glance's cornerRadius misses a Fit image's letterboxed corners.
                    .transformations(RoundedCornersTransformation(28f))
                    .build()
            context.imageLoader
                .execute(request)
                .image
                ?.toBitmap()
        }.getOrNull()
}

private data class NowPlaying(
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val hasTrack: Boolean,
    val isPlaying: Boolean,
    val isLiked: Boolean,
)

@Composable
private fun WidgetContent(
    theme: WidgetTheme,
    glass: GlassLevel,
    state: NowPlaying,
) {
    val size = LocalSize.current
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(ImageProvider(if (theme == WidgetTheme.Glass) glass.background else theme.background))
            .cornerRadius(24.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            size.height < 100.dp -> Wide(theme, state)
            size.width >= 220.dp && size.height >= 300.dp -> Large(theme, state)
            size.width >= 220.dp -> Medium(theme, state)
            else -> Small(theme, state)
        }
    }
}

@Composable
private fun Small(
    theme: WidgetTheme,
    s: NowPlaying,
) {
    val size = LocalSize.current
    // Artwork gets what the titles and controls leave; on a very short cell it steps aside for them.
    val art = minOf(size.height.value - 120, size.width.value - 28, 96f).toInt()
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        if (art >= 40) Artwork(s.art, art)
        Spacer(GlanceModifier.defaultWeight())
        Titles(theme, s)
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(R.drawable.widget_ic_skip_previous, "Previous", theme.text, actionRunCallback<SkipPrevious>(), 26)
            Spacer(GlanceModifier.defaultWeight())
            PlayButton(theme, s.isPlaying, 38)
            Spacer(GlanceModifier.defaultWeight())
            Icon(R.drawable.widget_ic_skip_next, "Next", theme.text, actionRunCallback<SkipNext>(), 26)
        }
    }
}

@Composable
private fun Medium(
    theme: WidgetTheme,
    s: NowPlaying,
) {
    Row(GlanceModifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Fill the widget's height, which varies a lot between launchers.
        Artwork(s.art, (LocalSize.current.height.value - 28).toInt().coerceIn(72, 140))
        Spacer(GlanceModifier.width(14.dp))
        Column(GlanceModifier.defaultWeight()) {
            Titles(theme, s)
            Spacer(GlanceModifier.height(10.dp))
            Controls(theme, s)
        }
    }
}

/** One row (4×1): artwork, titles, controls. */
@Composable
private fun Wide(
    theme: WidgetTheme,
    s: NowPlaying,
) {
    val size = LocalSize.current
    val inner = (size.height.value - 12).toInt()
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // Artwork only when the row is roomy enough to keep the titles readable.
        if (size.width >= 200.dp && inner >= 28) {
            Artwork(s.art, inner.coerceAtMost(64))
            Spacer(GlanceModifier.width(10.dp))
        }
        Column(GlanceModifier.defaultWeight()) { Titles(theme, s) }
        Spacer(GlanceModifier.width(8.dp))
        Icon(R.drawable.widget_ic_skip_previous, "Previous", theme.text, actionRunCallback<SkipPrevious>(), 26)
        Spacer(GlanceModifier.width(8.dp))
        PlayButton(theme, s.isPlaying, inner.coerceIn(28, 40))
        Spacer(GlanceModifier.width(8.dp))
        Icon(R.drawable.widget_ic_skip_next, "Next", theme.text, actionRunCallback<SkipNext>(), 26)
    }
}

@Composable
private fun Large(
    theme: WidgetTheme,
    s: NowPlaying,
) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.Center) {
            Artwork(s.art, null)
        }
        Spacer(GlanceModifier.height(12.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) { Titles(theme, s) }
            if (s.hasTrack) {
                Icon(
                    if (s.isLiked) R.drawable.widget_ic_favorite else R.drawable.widget_ic_favorite_border,
                    if (s.isLiked) "Unlove" else "Love",
                    theme.text,
                    actionRunCallback<ToggleLike>(),
                    28,
                )
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        Controls(theme, s, centered = true)
    }
}

@Composable
private fun Titles(
    theme: WidgetTheme,
    s: NowPlaying,
) {
    Text(s.title, maxLines = 1, style = TextStyle(color = ColorProvider(theme.text), fontSize = 15.sp, fontWeight = FontWeight.Medium))
    Text(s.artist, maxLines = 1, style = TextStyle(color = ColorProvider(theme.secondary), fontSize = 13.sp))
}

@Composable
private fun Controls(
    theme: WidgetTheme,
    s: NowPlaying,
    centered: Boolean = false,
) {
    Row(
        GlanceModifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.widget_ic_skip_previous, "Previous", theme.text, actionRunCallback<SkipPrevious>(), 30)
        Spacer(GlanceModifier.width(if (centered) 24.dp else 12.dp))
        PlayButton(theme, s.isPlaying, if (centered) 52 else 40)
        Spacer(GlanceModifier.width(if (centered) 24.dp else 12.dp))
        Icon(R.drawable.widget_ic_skip_next, "Next", theme.text, actionRunCallback<SkipNext>(), 30)
    }
}

@Composable
private fun PlayButton(
    theme: WidgetTheme,
    playing: Boolean,
    size: Int,
) {
    Box(
        GlanceModifier
            .size(size.dp)
            .background(ImageProvider(theme.button))
            .clickable(mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(if (playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play_arrow),
            if (playing) "Pause" else "Play",
            GlanceModifier.size((size * 0.55f).dp),
            colorFilter = ColorFilter.tint(ColorProvider(theme.buttonIcon)),
        )
    }
}

@Composable
private fun Icon(
    res: Int,
    description: String,
    tint: Color,
    onClick: Action,
    size: Int,
) {
    Image(
        ImageProvider(res),
        description,
        GlanceModifier.size(size.dp).clickable(onClick),
        colorFilter = ColorFilter.tint(ColorProvider(tint)),
    )
}

/** [size] in dp, or null to fill the space it is given. */
@Composable
private fun Artwork(
    art: Bitmap?,
    size: Int?,
) {
    val modifier = (if (size != null) GlanceModifier.size(size.dp) else GlanceModifier.fillMaxSize()).cornerRadius(12.dp)
    if (art != null) {
        Image(ImageProvider(art), null, modifier, contentScale = if (size != null) ContentScale.Crop else ContentScale.Fit)
    } else {
        Box(modifier.background(ImageProvider(R.drawable.widget_art_placeholder)), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(R.drawable.widget_ic_music_note),
                null,
                GlanceModifier.size(((size ?: 120) * 0.4f).dp),
                colorFilter = ColorFilter.tint(ColorProvider(Color(0xFF8E8E93))),
            )
        }
    }
}

/**
 * A media key sent to Media3's receiver, the same way headphones do, so Play/Pause also starts the service and
 * resumes the last queue when the app isn't running (SimpleMediaSessionCallback.onPlaybackResumption).
 * Only for Play/Pause: the receiver drops every other key on API 26+, so Next/Previous go through [PlayerAction].
 */
@Composable
private fun mediaKey(code: Int): Action {
    val context = androidx.glance.LocalContext.current
    return actionSendBroadcast(
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            // Distinct data per key, or the system would hand every button the same PendingIntent.
            .setData("nexmusic://widget/key/$code".toUri())
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code)),
    )
}

/** A widget button handled in the app's process by the player, like the in-app controls. */
abstract class PlayerAction(
    private val event: PlayerEvent,
) : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        GlobalContext.get().get<MediaPlayerHandler>().onPlayerEvent(event)
    }
}

class SkipNext : PlayerAction(PlayerEvent.Next)

class SkipPrevious : PlayerAction(PlayerEvent.Previous)

class ToggleLike : PlayerAction(PlayerEvent.ToggleLike)

/**
 * A widget session only lives a short while after each update, so the app pushes an update whenever the
 * track, play state or like changes.
 */
fun startWidgetUpdates(
    context: Context,
    scope: CoroutineScope,
) {
    val handler = GlobalContext.get().get<MediaPlayerHandler>()
    scope.launch {
        combine(handler.nowPlayingState, handler.controlState) { now, control ->
            Triple(now.mediaItem, control.isPlaying, control.isLiked)
        }.distinctUntilChanged().collect { runCatching { NexWidget().updateAll(context) } }
    }
}

// One receiver per size, so the widget picker lists Small, Medium, Wide and Large; all share the responsive layout.
class SmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexWidget()
}

class MediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexWidget()
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexWidget()
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NexWidget()
}
