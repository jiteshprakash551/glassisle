package dev.jitesh.glassisle

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GlassIsleListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var cn: ComponentName? = null
    private val bound = ArrayList<Pair<MediaController, MediaController.Callback>>()
    private var lastTrackKey: String? = null
    private var lastPlaying = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        bindSessions(list ?: emptyList())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        dbg("listener connected")
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            val c = ComponentName(this, GlassIsleListener::class.java)
            msm = m; cn = c
            m.addOnActiveSessionsChangedListener(sessionsListener, c, main)
            bindSessions(m.getActiveSessions(c))
        } catch (e: SecurityException) { /* notification access not granted */ }
    }

    override fun onListenerDisconnected() {
        unbind()
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
        super.onListenerDisconnected()
    }

    private fun unbind() {
        for ((c, cb) in bound) try { c.unregisterCallback(cb) } catch (_: Throwable) {}
        bound.clear()
    }

    private fun bindSessions(list: List<MediaController>) {
        unbind()
        for (c in list) {
            val cb = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) { pushMusic() }
                override fun onMetadataChanged(metadata: MediaMetadata?) { pushMusic() }
                override fun onSessionDestroyed() { pushMusic() }
            }
            try { c.registerCallback(cb, main); bound.add(c to cb) } catch (_: Throwable) {}
        }
        pushMusic()
    }

    private fun pushMusic() {
        val prefs = Prefs(this)
        val live = bound.map { it.first }
        val pick = live.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: live.firstOrNull { it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED }
        if (pick == null || !prefs.fMusic) { IslandHub.remove("music"); lastPlaying = false; return }
        val md = pick.metadata ?: run { IslandHub.remove("music"); return }
        val st = pick.playbackState ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = st.state == PlaybackState.STATE_PLAYING
        val now = IslandHub.now()
        val trackKey = pick.packageName + "|" + title + "|" + artist
        val peek = trackKey != lastTrackKey || (playing && !lastPlaying)
        lastTrackKey = trackKey; lastPlaying = playing
        val payload = MusicPayload(pick, pick.packageName, art, playing, st.position, now,
            if (st.playbackSpeed == 0f) 1f else st.playbackSpeed, md.getLong(MediaMetadata.METADATA_KEY_DURATION))
        IslandHub.post(
            Item("music", Kind.MUSIC, title, artist, pick.packageName, now,
                if (playing) null else now + 15_000, null, payload),
            peek = peek
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { dbg("raw posted from " + sbn.packageName); main.post { handle(sbn) } }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { main.post { IslandHub.remove("n:" + sbn.key) } }

    /** Debug-build-only breadcrumbs: reasons only, never notification content. */
    private fun dbg(m: String) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) android.util.Log.i("GI", m)
    }

    private fun handle(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.enabled) return
        val pkg = sbn.packageName
        dbg("posted")
        IslandHub.debug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!AppFilter.allow(pkg, prefs.blocked, packageName)) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.contains("MediaStyle")) return
        val cat = n.category
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val kind = when {
            cat == Notification.CATEGORY_CALL -> Kind.CALL
            ongoing && chrono -> Kind.TIMER
            ongoing && cat == Notification.CATEGORY_NAVIGATION -> Kind.LIVE
            ongoing -> return
            else -> Kind.NOTIFICATION
        }
        if (!prefs.featureOn(kind)) return
        try {
            val r = Ranking()
            if (currentRanking.getRanking(sbn.key, r) && r.importance <= android.app.NotificationManager.IMPORTANCE_MIN
                && kind == Kind.NOTIFICATION) return
        } catch (_: Throwable) {}
        val title = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()
        if (title.isEmpty() && text.isEmpty()) return
        val pm = packageManager
        val appName = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Throwable) { pkg }
        val icon = try { pm.getApplicationIcon(pkg) } catch (_: Throwable) { null }
        val large = try { n.getLargeIcon()?.loadDrawable(this) } catch (_: Throwable) { null }
        val actions = (n.actions ?: emptyArray()).take(3).map {
            NAction(it.title?.toString() ?: "", it.actionIntent, it.remoteInputs?.firstOrNull())
        }
        val payload = NotifPayload(sbn.key, appName, icon, large, n.contentIntent, actions, chrono, n.`when`,
            ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        val now = IslandHub.now()
        if (kind == Kind.NOTIFICATION) {
            val pmgr = getSystemService(PowerManager::class.java)
            if (!pmgr.isInteractive) { dbg("skip: screen off"); return }
            // Lock-screen privacy: never draw message text over the lock screen.
            if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) { dbg("skip: keyguard"); return }
            if (IslandHub.foregroundPkg == pkg) { dbg("skip: foreground app"); return } // you're already looking at that app
            IslandHub.post(Item("n:" + sbn.key, kind, title.ifEmpty { appName }, text, pkg, now,
                now + prefs.peekSec * 1000L + 500L, null, payload), peek = true)
        } else {
            if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
            val t = if (kind == Kind.TIMER && chrono) {
                val elapsedBase = n.`when`
                if (elapsedBase > 0) title.ifEmpty { appName } else title.ifEmpty { appName }
            } else title.ifEmpty { appName }
            IslandHub.post(Item("n:" + sbn.key, kind, t, text, pkg, now, null, null, payload), peek = true)
        }
    }
}
