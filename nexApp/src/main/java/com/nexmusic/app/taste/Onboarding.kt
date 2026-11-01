package com.nexmusic.app.taste

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.maxrave.domain.repository.SearchRepository
import com.nexmusic.app.ui.components.Artwork
import com.nexmusic.app.ui.components.GlassButton
import com.nexmusic.app.ui.components.GlassCard
import com.nexmusic.app.ui.components.GlassTextField
import com.nexmusic.app.ui.components.ProvideGlassBackdrop
import com.nexmusic.app.ui.components.best
import com.nexmusic.app.ui.components.liquidGlass
import com.nexmusic.app.ui.icon.Check
import com.nexmusic.app.ui.icon.NexIcons
import com.nexmusic.app.ui.theme.Accent
import com.nexmusic.app.ui.theme.LocalNex
import com.nexmusic.app.ui.theme.NexType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(FlowPreview::class)
class OnboardingViewModel(
    private val store: TasteStore,
    private val search: SearchRepository,
) : ViewModel() {
    val languages = MutableStateFlow(store.languages.value.toSet())
    val picked = MutableStateFlow(store.artists.value)
    val theme = MutableStateFlow(store.theme.value)
    val suggestions = MutableStateFlow<List<PickedArtist>>(emptyList())
    val loading = MutableStateFlow(false)
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<PickedArtist>>(emptyList())
    private val resolved = mutableMapOf<String, PickedArtist?>()

    init {
        viewModelScope.launch {
            query.debounce(350).collectLatest { q -> results.value = if (q.isBlank()) emptyList() else find(q.trim(), 12) }
        }
    }

    fun toggleLanguage(l: String) = languages.update { if (l in it) it - l else it + l }

    fun toggleArtist(a: PickedArtist) = picked.update { list -> if (list.any { it.id == a.id }) list.filterNot { it.id == a.id } else list + a }

    /** Turns the hand-picked names for the chosen languages into real channels (photo and id) by searching each. */
    fun loadSuggestions() =
        viewModelScope.launch {
            val names = languages.value.ifEmpty { setOf("English") }.flatMap { LanguageArtists[it].orEmpty() }.distinct().take(32)
            loading.value = suggestions.value.isEmpty()
            suggestions.value =
                coroutineScope { names.map { n -> async { resolved.getOrPut(n) { find(n, 1).firstOrNull() } } }.awaitAll() }
                    .filterNotNull()
                    .distinctBy { it.id }
            loading.value = false
        }

    private suspend fun find(
        q: String,
        n: Int,
    ): List<PickedArtist> =
        runCatching { search.getSearchDataArtist(q).first().data.orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.browseId.startsWith("UC") }
            .take(n)
            .map { PickedArtist(it.browseId, it.artist, it.thumbnails.best()) }

    fun finish() = store.save(LanguageArtists.keys.filter { it in languages.value }, picked.value, theme.value)

    fun skip() = store.skip()
}

/** First launch (and Library › Your Taste): languages, then favourite artists, then appearance. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val vm: OnboardingViewModel = koinViewModel()
    val nex = LocalNex.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val languages by vm.languages.collectAsStateWithLifecycle()
    val picked by vm.picked.collectAsStateWithLifecycle()
    val bg = rememberLayerBackdrop()
    LaunchedEffect(step) { if (step == 1) vm.loadSuggestions() }

    Box(Modifier.fillMaxSize()) {
        Blobs(Modifier.fillMaxSize().layerBackdrop(bg))
        ProvideGlassBackdrop(bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { i ->
                        Box(Modifier.padding(end = 6.dp).size(if (i == step) 22.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i <= step) Accent else nex.tertiaryLabel))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Not Now",
                        style = NexType.Body,
                        color = nex.secondaryLabel,
                        modifier =
                            Modifier.clip(CircleShape).clickable {
                                vm.skip()
                                onDone()
                            }.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                AnimatedContent(step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, modifier = Modifier.weight(1f), label = "step") { s ->
                    when (s) {
                        0 -> LanguageStep(bg, languages, vm::toggleLanguage)
                        1 -> ArtistStep(vm, picked)
                        else -> ThemeStep(bg, vm)
                    }
                }
                GlassCard(Modifier.padding(16.dp).fillMaxWidth(), padding = PaddingValues(10.dp), corner = 34.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (step > 0) GlassButton("Back", Modifier.weight(1f)) { step-- }
                        val last = step == 2
                        GlassButton(
                            when {
                                last -> "Done"
                                step == 1 && picked.isNotEmpty() -> "Continue · ${picked.size}"
                                else -> "Continue"
                            },
                            Modifier.weight(1f),
                        ) {
                            if (last) {
                                vm.finish()
                                onDone()
                            } else {
                                step++
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Soft colour drifting behind the glass, so there is something for it to blur and bend. */
@Composable
private fun Blobs(modifier: Modifier) {
    val nex = LocalNex.current
    val t by rememberInfiniteTransition(label = "blobs").animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "t")
    val a = if (nex.isDark) 0.55f else 0.35f
    Box(
        modifier.background(nex.background).drawBehind {
            fun blob(
                c: Color,
                x: Float,
                y: Float,
                r: Float,
            ) = drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), Offset(size.width * x, size.height * y), size.minDimension * r), size.minDimension * r, Offset(size.width * x, size.height * y))
            blob(Accent, 0.15f + 0.2f * t, 0.2f, 0.75f)
            blob(Color(0xFF7B5CFF), 0.9f - 0.25f * t, 0.45f + 0.1f * t, 0.7f)
            blob(Color(0xFF00B7FF), 0.3f + 0.15f * t, 0.85f - 0.1f * t, 0.65f)
        },
    )
}

@Composable
private fun StepTitle(
    title: String,
    subtitle: String,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, style = NexType.LargeTitle)
        Text(subtitle, style = NexType.Body, color = LocalNex.current.secondaryLabel, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageStep(
    bg: Backdrop,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val nex = LocalNex.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StepTitle("What do you listen to?", "Pick the languages of the music you love. We'll tune Home and New to them.")
        FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LanguageArtists.keys.forEach { l ->
                val on = l in selected
                // Liquid glass chips; a chosen one fills with the accent.
                Box(
                    Modifier
                        .liquidGlass(bg, Capsule(), if (on) Accent.copy(alpha = 0.9f) else nex.elevated.copy(alpha = 0.4f))
                        .clip(CircleShape)
                        .clickable { onToggle(l) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) { Text(l, style = NexType.Headline, color = if (on) Color.White else nex.label) }
            }
        }
    }
}

@Composable
private fun ArtistStep(
    vm: OnboardingViewModel,
    picked: List<PickedArtist>,
) {
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    // Search results first while searching; otherwise what's picked, then the suggestions.
    val shown = (if (query.isNotBlank()) results else picked + suggestions).distinctBy { it.id }
    Column(Modifier.fillMaxSize()) {
        StepTitle("Choose your artists", "Tap the ones you love. You can change this later in Library.")
        Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
            GlassTextField(query, { vm.query.value = it }, "Search artists", autoFocus = false, capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search)
        }
        if (loading && query.isBlank()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent, strokeWidth = 2.dp) }
        }
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            items(shown, key = { it.id }) { a ->
                ArtistBubble(a, picked.any { it.id == a.id }) { vm.toggleArtist(a) }
            }
        }
    }
}

@Composable
private fun ArtistBubble(
    artist: PickedArtist,
    on: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp).clip(RoundedRectangle(16.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Artwork(artist.thumb, Modifier.size(88.dp).then(if (on) Modifier.border(3.dp, Accent, CircleShape) else Modifier), CircleShape)
            if (on) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                    Icon(NexIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(artist.name, style = NexType.Footnote, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ThemeStep(
    bg: Backdrop,
    vm: OnboardingViewModel,
) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val nex = LocalNex.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StepTitle("Pick a look", "Automatic follows your phone's dark mode.")
        ThemeMode.entries.forEach { mode ->
            val on = mode == theme
            Row(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .liquidGlass(bg, RoundedRectangle(22.dp), nex.elevated.copy(alpha = 0.45f))
                    .then(if (on) Modifier.border(2.dp, Accent, RoundedRectangle(22.dp)) else Modifier)
                    .clip(RoundedRectangle(22.dp))
                    .clickable { vm.theme.value = mode }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A tiny phone in that look.
                val (paper, ink) =
                    when (mode) {
                        ThemeMode.Light -> Color.White to Color(0xFFD1D1D6)
                        ThemeMode.Dark -> Color.Black to Color(0xFF3A3A3C)
                        ThemeMode.System -> Color(0xFF8E8E93) to Color(0xFFE5E5EA)
                    }
                Box(Modifier.size(44.dp, 72.dp).clip(RoundedRectangle(10.dp)).background(paper).border(1.dp, nex.separator, RoundedRectangle(10.dp)).padding(6.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedRectangle(4.dp)).background(Accent))
                        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(ink))
                        Box(Modifier.width(20.dp).height(5.dp).clip(CircleShape).background(ink))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(mode.label, style = NexType.Headline, modifier = Modifier.weight(1f))
                if (on) Icon(NexIcons.Check, null, tint = Accent, modifier = Modifier.size(24.dp))
            }
        }
    }
}
