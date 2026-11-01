package com.nexmusic.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.mediaservice.handler.DownloadHandler
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class AccountState(
    val email: String? = null,
    val name: String? = null,
    val photo: String? = null,
    val syncing: Boolean = false,
    val lastSynced: Long = 0,
    val error: String? = null,
    /** Set after sign-up when the account must confirm its email before signing in. */
    val notice: String? = null,
)

/**
 * Optional NexMusic account (Supabase, email + password). Signed in, the library lives in the account's row
 * and is merged into every device using the same account: on sign-in, when the app opens or goes to the background, and on Sync Now.
 */
class AccountSync(
    private val context: Context,
    private val library: LocalLibrary,
    private val songs: SongRepository,
    private val downloads: DownloadHandler,
) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = Mutex()
    private val baseFile = File(context.filesDir, "sync_base.json")

    private val _state =
        MutableStateFlow(
            // An email saved by the old Google sign-in has no Supabase session; start signed out.
            AccountState(prefs.getString(EMAIL, null)?.takeIf { prefs.contains(REFRESH) }, lastSynced = prefs.getLong(LAST, 0)),
        )
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Hooks syncing to the app opening and closing, and starts fetching pending downloads on Wi-Fi. Call once. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = syncInBackground()

                override fun onStop(owner: LifecycleOwner) = syncInBackground()
            },
        )
        scope.launch { downloadOnWifi() }
    }

    /** Signs in, or with [create] makes the account first; then syncs. */
    fun signIn(
        email: String,
        password: String,
        create: Boolean,
    ) = scope.launch {
        _state.update { it.copy(syncing = true, error = null, notice = null) }
        try {
            val session = if (create) Supabase.signUp(email.trim(), password) else Supabase.signIn(email.trim(), password)
            if (session == null) {
                _state.update { it.copy(syncing = false, notice = "Check your email to confirm your account, then sign in.") }
                return@launch
            }
            // A different account than last time must not inherit the old account's last-synced copy.
            if (session.userId != prefs.getString(USER, null)) baseFile.delete()
            save(session)
            _state.update { it.copy(email = session.email, syncing = false) }
            sync()
        } catch (e: Exception) {
            _state.update { it.copy(syncing = false) }
            fail(e)
        }
    }

    /** Signs out of this device. The library stays here and in the account. */
    fun signOut() =
        scope.launch {
            running.withLock {
                prefs.edit().clear().apply()
                baseFile.delete()
                library.forget()
                _state.value = AccountState()
            }
        }

    fun syncNow() = syncInBackground()

    private fun syncInBackground() {
        if (_state.value.email == null) return
        scope.launch { sync() }
    }

    private fun save(s: Session) =
        prefs
            .edit()
            .putString(EMAIL, s.email)
            .putString(USER, s.userId)
            .putString(ACCESS, s.accessToken)
            .putString(REFRESH, s.refreshToken)
            .putLong(EXPIRES, s.expiresAt)
            .apply()

    /** A valid access token, refreshed when it is within a minute of expiring (they last an hour). Null = signed out. */
    private suspend fun token(): String? {
        val refresh = prefs.getString(REFRESH, null) ?: return null
        val access = prefs.getString(ACCESS, null)
        if (access != null && prefs.getLong(EXPIRES, 0) - 60 > System.currentTimeMillis() / 1000) return access
        return try {
            Supabase.refresh(refresh).also(::save).accessToken
        } catch (e: SupabaseException) {
            // 400/401: the refresh token was revoked or expired; anything else (5xx) is worth retrying later.
            if (e.code in 400..401) null else throw e
        }
    }

    private suspend fun sync() {
        // A sync already running covers this request.
        if (!running.tryLock()) return
        try {
            _state.update { it.copy(syncing = true, error = null) }
            val token = token()
            val userId = prefs.getString(USER, null)
            if (token == null || userId == null) {
                _state.update { it.copy(error = "Sign in again to keep syncing.") }
                return
            }
            val remote = Supabase.read(token)?.let(SnapshotJson::decode) ?: Snapshot()
            val base = baseFile.takeIf { it.exists() }?.let { runCatching { SnapshotJson.decode(it.readText()) }.getOrNull() } ?: Snapshot()
            val local = library.read()
            val merged = merge(base, local, remote)
            library.apply(merged, local)
            if (merged != remote) Supabase.write(token, userId, SnapshotJson.encode(merged))
            baseFile.writeText(SnapshotJson.encode(merged))
            val now = System.currentTimeMillis()
            prefs.edit().putLong(LAST, now).apply()
            _state.update { it.copy(lastSynced = now) }
            if (library.pendingDownloads.isNotEmpty()) pendingChanged.update { it + 1 }
        } catch (e: Exception) {
            fail(e)
        } finally {
            _state.update { it.copy(syncing = false) }
            running.unlock()
        }
    }

    private fun fail(e: Exception) {
        Log.w(TAG, "account sync", e)
        val message =
            when (e) {
                is SupabaseException -> e.message
                is java.io.IOException -> "Couldn't reach the server. Check your connection."
                else -> e.message ?: "Sync failed."
            }
        _state.update { it.copy(error = message) }
    }

    /** Bumped after a sync leaves downloads pending, so the Wi-Fi loop looks again. */
    private val pendingChanged = MutableStateFlow(0)

    /**
     * Fetches the account's downloads this device lacks whenever an unmetered network (Wi-Fi) is up.
     * shortcut: the check is made when each download is queued, not while it runs; one queued on Wi-Fi finishes even if the phone moves to mobile data.
     */
    private suspend fun downloadOnWifi() {
        unmetered().collect { onWifi ->
            if (!onWifi) return@collect
            for (id in library.pendingDownloads) {
                val song = songs.getSongById(id).first()
                if (song != null) {
                    songs.updateDownloadState(id, DownloadState.STATE_PREPARING)
                    downloads.downloadTrack(id, song.title, song.thumbnails.orEmpty())
                }
                library.pendingDownloads = library.pendingDownloads - id
            }
        }
    }

    /** True while the default network is unmetered; re-emits after each sync so new pending downloads start. */
    private fun unmetered() =
        callbackFlow {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            fun now() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        caps: NetworkCapabilities,
                    ) {
                        trySend(now())
                    }

                    override fun onLost(network: Network) {
                        trySend(now())
                    }
                }
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            val nudges = launch { pendingChanged.collect { trySend(now()) } }
            awaitClose {
                nudges.cancel()
                cm.unregisterNetworkCallback(callback)
            }
        }

    private companion object {
        const val TAG = "AccountSync"
        const val EMAIL = "email"
        const val USER = "user_id"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES = "expires_at"
        const val LAST = "last_synced"
    }
}
