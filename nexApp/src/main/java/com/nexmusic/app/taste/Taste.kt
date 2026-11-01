package com.nexmusic.app.taste

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nexmusic.app.ui.theme.Accents
import com.nexmusic.app.ui.theme.Backdrop
import com.nexmusic.app.ui.theme.setAccentColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { System("Automatic"), Light("Light"), Dark("Dark") }

data class PickedArtist(
    val id: String,
    val name: String,
    val thumb: String?,
)

/** What the user told us on first launch: languages, favourite artists, appearance. Kept on the device only. */
class TasteStore(context: Context) {
    private val prefs = context.getSharedPreferences("taste", Context.MODE_PRIVATE)

    private val _onboarded = MutableStateFlow(prefs.getBoolean(ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _languages = MutableStateFlow(prefs.getString(LANGUAGES, null)?.split(SEP)?.filter { it.isNotEmpty() }.orEmpty())
    val languages: StateFlow<List<String>> = _languages.asStateFlow()

    private val _artists = MutableStateFlow(decode(prefs.getString(ARTISTS, null)))
    val artists: StateFlow<List<PickedArtist>> = _artists.asStateFlow()

    private val _theme = MutableStateFlow(runCatching { ThemeMode.valueOf(prefs.getString(THEME, null)!!) }.getOrDefault(ThemeMode.System))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    /** Appearance › Accent Color, as ARGB. */
    private val _accent = MutableStateFlow(prefs.getLong(ACCENT, Accents.first().second.toArgb().toLong()))
    val accent: StateFlow<Long> = _accent.asStateFlow()

    private val _backdrop = MutableStateFlow(runCatching { Backdrop.valueOf(prefs.getString(BACKDROP, null)!!) }.getOrDefault(Backdrop.Classic))
    val backdrop: StateFlow<Backdrop> = _backdrop.asStateFlow()

    init {
        // Before the first frame, so the app never flashes the default red.
        setAccentColor(Color(_accent.value.toInt()))
    }

    fun setTheme(mode: ThemeMode) {
        prefs.edit().putString(THEME, mode.name).apply()
        _theme.value = mode
    }

    fun setAccent(argb: Long) {
        prefs.edit().putLong(ACCENT, argb).apply()
        _accent.value = argb
        setAccentColor(Color(argb.toInt()))
    }

    fun setBackdrop(b: Backdrop) {
        prefs.edit().putString(BACKDROP, b.name).apply()
        _backdrop.value = b
    }

    fun save(
        languages: List<String>,
        artists: List<PickedArtist>,
        theme: ThemeMode,
    ) {
        prefs
            .edit()
            .putBoolean(ONBOARDED, true)
            .putString(LANGUAGES, languages.joinToString(SEP))
            .putString(ARTISTS, encode(artists))
            .putString(THEME, theme.name)
            .apply()
        _languages.value = languages
        _artists.value = artists
        _theme.value = theme
        _onboarded.value = true
    }

    /** "Not now" on first launch: don't ask again, keep the defaults. */
    fun skip() {
        prefs.edit().putBoolean(ONBOARDED, true).apply()
        _onboarded.value = true
    }

    private companion object {
        const val ONBOARDED = "onboarded"
        const val LANGUAGES = "languages"
        const val ARTISTS = "artists"
        const val THEME = "theme"
        const val ACCENT = "accent"
        const val BACKDROP = "backdrop"

        // Control characters can't appear in names or URLs, so they split safely.
        const val SEP = "\u001E"
        const val FIELD = "\u001F"

        fun encode(list: List<PickedArtist>) = list.joinToString(SEP) { listOf(it.id, it.name, it.thumb.orEmpty()).joinToString(FIELD) }

        fun decode(s: String?): List<PickedArtist> =
            s
                ?.split(SEP)
                ?.mapNotNull { row -> row.split(FIELD).takeIf { it.size == 3 }?.let { (id, name, thumb) -> PickedArtist(id, name, thumb.ifEmpty { null }) } }
                .orEmpty()
    }
}

/** The languages offered on first launch, with well-known artists to suggest for each. */
// shortcut: hand-picked names, resolved to channels by search at run time; swap for a charts API if one is ever wired in.
val LanguageArtists: Map<String, List<String>> =
    linkedMapOf(
        "English" to listOf("Taylor Swift", "Ed Sheeran", "The Weeknd", "Billie Eilish", "Drake", "Dua Lipa", "Coldplay", "Bruno Mars"),
        "Hindi" to listOf("Arijit Singh", "Shreya Ghoshal", "Pritam", "A.R. Rahman", "Atif Aslam", "Anuv Jain", "Jubin Nautiyal", "Badshah"),
        "Punjabi" to listOf("Diljit Dosanjh", "Sidhu Moose Wala", "AP Dhillon", "Karan Aujla", "Arjan Dhillon", "Shubh", "Guru Randhawa", "Jasmine Sandlas"),
        "Tamil" to listOf("Anirudh Ravichander", "Sid Sriram", "Yuvan Shankar Raja", "Ilaiyaraaja", "Harris Jayaraj", "G. V. Prakash Kumar", "Dhanush"),
        "Telugu" to listOf("Devi Sri Prasad", "Thaman S", "M. M. Keeravani", "Anurag Kulkarni", "Mangli", "Kaala Bhairava"),
        "Bengali" to listOf("Anupam Roy", "Nachiketa", "Rupam Islam", "Iman Chakraborty", "Arijit Singh"),
        "Marathi" to listOf("Ajay-Atul", "Avadhoot Gupte", "Bela Shende", "Swapnil Bandodkar"),
        "Malayalam" to listOf("K. S. Chithra", "Vineeth Sreenivasan", "Sushin Shyam", "K. J. Yesudas", "Shaan Rahman"),
        "Kannada" to listOf("Vijay Prakash", "Arjun Janya", "Sanjith Hegde", "Rajesh Krishnan"),
        "Spanish" to listOf("Bad Bunny", "Shakira", "Karol G", "Rosalía", "J Balvin", "Peso Pluma", "Feid"),
        "Korean" to listOf("BTS", "BLACKPINK", "NewJeans", "Stray Kids", "TWICE", "IU", "SEVENTEEN", "aespa"),
        "Japanese" to listOf("YOASOBI", "Kenshi Yonezu", "Ado", "Official HIGE DANdism", "King Gnu", "LiSA"),
        "Arabic" to listOf("Amr Diab", "Nancy Ajram", "Fairuz", "Elissa", "Wegz"),
        "French" to listOf("Stromae", "Aya Nakamura", "Indila", "Angèle", "Ninho"),
        "Portuguese" to listOf("Anitta", "Marília Mendonça", "Ludmilla", "Henrique & Juliano"),
        "German" to listOf("Rammstein", "Apache 207", "Helene Fischer", "Cro", "Nina Chuba"),
    )
