package com.nexmusic.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.nexmusic.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release on GitHub, with the APK built for this phone's CPU. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data object UpToDate : UpdateState

    data class Available(val release: Release) : UpdateState

    /** [progress] 0..1, or -1 while the size is unknown. */
    data class Downloading(val release: Release, val progress: Float) : UpdateState

    /** Handed to Android's installer; it shows its own confirmation. */
    data class Installing(val release: Release) : UpdateState

    /** Android won't let this app install updates until the user allows it in Settings. */
    data class NeedsPermission(val release: Release) : UpdateState

    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Updates the app from its GitHub releases: checks the latest one, downloads the APK for this CPU and installs it with
 * PackageInstaller. A release must be signed with the same key as the installed app, or Android refuses it.
 */
class AppUpdater(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String = BuildConfig.VERSION_NAME

    fun check() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                _state.value = UpdateState.Checking
                _state.value =
                    try {
                        latest()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                    } catch (e: Exception) {
                        Log.w(TAG, "update check", e)
                        UpdateState.Failed("Couldn't check for updates. Check your connection.")
                    }
            }
    }

    /** Downloads and installs [release]. If installing isn't allowed yet, asks for that first. */
    fun update(release: Release) {
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(release)
            return
        }
        job =
            scope.launch {
                try {
                    val apk = download(release)
                    _state.value = UpdateState.Installing(release)
                    install(apk)
                } catch (e: Exception) {
                    Log.w(TAG, "update", e)
                    _state.value = UpdateState.Failed("The download failed. Try again.", release)
                }
            }
    }

    /** Settings › Install unknown apps, for this app. The user comes back and taps Update again. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    internal fun installFinished(
        status: Int,
        message: String?,
    ) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value =
            when (status) {
                // On success the app is replaced and restarted, so this is rarely seen.
                PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate
                PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
                PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    UpdateState.Failed("This update can't replace the installed app (it was signed differently). Install it from GitHub instead.", release)
                PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Not enough storage to install the update.", release)
                else -> UpdateState.Failed(message ?: "The update couldn't be installed.", release)
            }
    }

    private suspend fun latest(): Release? {
        val json = JSONObject(get("https://api.github.com/repos/$REPO/releases/latest"))
        val version = json.getString("tag_name").removePrefix("v")
        if (!isNewer(version, currentVersion)) return null
        val assets = json.getJSONArray("assets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { it.getString("name").endsWith(".apk") }
        // The build for this CPU is a third the size of the universal one.
        val asset =
            Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.getString("name").endsWith("-$abi.apk") } }
                ?: assets.firstOrNull { it.getString("name").endsWith("-universal.apk") }
                ?: return null
        return Release(version, plain(json.optString("body")), asset.getString("browser_download_url"), asset.optLong("size"))
    }

    /** Release notes are Markdown; drop the heading marks, bold stars and backticks a Text would show literally. */
    private fun plain(md: String): String =
        md
            .lines()
            .filterNot { it.trim().matches(Regex("#+\\s*what'?s new", RegexOption.IGNORE_CASE)) }
            .joinToString("\n") { it.replace(Regex("^#+\\s*"), "").replace(Regex("^\\s*[-*]\\s+"), "• ").replace("**", "").replace("`", "") }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

    private suspend fun download(release: Release): File =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "update.apk")
            _state.value = UpdateState.Downloading(release, -1f)
            val c = URL(release.apkUrl).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                val total = c.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                c.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastShown = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val p = if (total > 0) done.toFloat() / total else -1f
                            // A state change per percent, not per 64 KB.
                            if (p < 0 || p - lastShown >= 0.01f) {
                                lastShown = p
                                _state.value = UpdateState.Downloading(release, p)
                            }
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            file
        }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        // Once this app installed itself, Android 12+ can apply later updates without asking each time.
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback =
                PendingIntent.getBroadcast(
                    context,
                    id,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            session.commit(callback.intentSender)
        }
    }

    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 15_000
                c.readTimeout = 15_000
                if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
                c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }

    companion object {
        private const val TAG = "AppUpdater"
        const val REPO = "abhikr1436/NexMusic"

        /** "1.2.0" > "1.1.10" > "1.1.9": compared number by number; anything after a "-" is ignored. */
        fun isNewer(
            candidate: String,
            current: String,
        ): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}

/** Where PackageInstaller reports back: either it needs the user to confirm, or it's done. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }
        org.koin.core.context.GlobalContext.get().get<AppUpdater>().installFinished(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
