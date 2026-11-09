package dev.jitesh.glassisle

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle

class DebugActivity : Activity() {
    companion object { var session: MediaSession? = null

        fun postNotif(c: android.content.Context) {
            IslandHub.debugAllowOwn = true
                val nm = c.getSystemService(android.app.NotificationManager::class.java)
                nm.createNotificationChannel(android.app.NotificationChannel("gi_test", "GlassIsle test", android.app.NotificationManager.IMPORTANCE_HIGH))
                val mute = android.app.PendingIntent.getBroadcast(c, 1, Intent(c, DebugReceiver::class.java).setAction("mute"), android.app.PendingIntent.FLAG_IMMUTABLE)
                val rpi = android.app.PendingIntent.getBroadcast(c, 2, Intent(c, DebugReceiver::class.java).setAction("reply"), android.app.PendingIntent.FLAG_MUTABLE)
                val ic = android.graphics.drawable.Icon.createWithResource(c, android.R.drawable.ic_menu_send)
                val ri = android.app.RemoteInput.Builder("reply").setLabel("Reply").build()
                val txt = "Hey, are you free tonight? Let's grab dinner at 8."
                val n = android.app.Notification.Builder(c, "gi_test").setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("Alex").setContentText(txt)
                    .setStyle(android.app.Notification.BigTextStyle().bigText(txt))
                    .setCategory(android.app.Notification.CATEGORY_MESSAGE)
                    .addAction(android.app.Notification.Action.Builder(ic, "Mute", mute).build())
                    .addAction(android.app.Notification.Action.Builder(ic, "Reply", rpi).addRemoteInput(ri).build()).build()
                nm.notify(7, n)
        }
    }

    private fun art(): Bitmap {
        val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 256f, 256f, Color.parseColor("#BF5AF2"), Color.parseColor("#0A84FF"), Shader.TileMode.CLAMP) }
        Canvas(b).drawRect(0f, 0f, 256f, 256f, p)
        return b
    }

    private fun state(s: MediaSession, st: Int, pos: Long) {
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO)
            .setState(st, pos, 1f).build())
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        when (intent.getStringExtra("mode")) {
            "media" -> {
                val s = MediaSession(this, "glassisle-test")
                var n = 0
                fun meta(t: String) = s.setMetadata(MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, t).putString(MediaMetadata.METADATA_KEY_ARTIST, "M83")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, 243000).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art()).build())
                meta("Midnight City")
                state(s, PlaybackState.STATE_PLAYING, 30000)
                s.setCallback(object : MediaSession.Callback() {
                    override fun onPause() { state(s, PlaybackState.STATE_PAUSED, 31000); android.util.Log.i("GI_TEST", "media: onPause") }
                    override fun onPlay() { state(s, PlaybackState.STATE_PLAYING, 31000); android.util.Log.i("GI_TEST", "media: onPlay") }
                    override fun onSkipToNext() { n++; meta("Track " + (n + 1)); state(s, PlaybackState.STATE_PLAYING, 0); android.util.Log.i("GI_TEST", "media: onSkipToNext") }
                    override fun onSeekTo(pos: Long) { state(s, PlaybackState.STATE_PLAYING, pos); android.util.Log.i("GI_TEST", "media: seek " + pos) }
                })
                s.isActive = true
                session = s
            }
            "notif" -> {
                IslandHub.debugAllowOwn = true
                postNotif(this)
            }
            "timer" -> IslandHub.startTimer(90_000)
        }
        finish()
    }
}
