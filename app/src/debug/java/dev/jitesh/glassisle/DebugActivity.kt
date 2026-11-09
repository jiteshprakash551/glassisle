package dev.jitesh.glassisle

import android.app.Activity
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
    companion object { var session: MediaSession? = null }

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
            "timer" -> IslandHub.startTimer(90_000)
        }
        finish()
    }
}
