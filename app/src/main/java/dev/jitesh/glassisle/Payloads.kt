package dev.jitesh.glassisle

import android.app.PendingIntent
import android.app.RemoteInput
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.session.MediaController

class NAction(val title: String, val pi: PendingIntent?, val remote: RemoteInput?)

class NotifPayload(
    val key: String,
    val appName: String,
    val icon: Drawable?,
    val largeIcon: Drawable?,
    val content: PendingIntent?,
    val actions: List<NAction>,
    val hasChrono: Boolean = false,
    val chronoWhen: Long = 0L,
    val countDown: Boolean = false
)

class MusicPayload(
    val controller: MediaController,
    val pkg: String,
    val art: Bitmap?,
    val playing: Boolean,
    val posAtUpdate: Long,
    val updateTime: Long,
    val speed: Float,
    val duration: Long
) {
    fun positionNow(now: Long): Long {
        val p = if (playing) posAtUpdate + ((now - updateTime) * speed).toLong() else posAtUpdate
        return if (duration > 0) p.coerceIn(0L, duration) else p.coerceAtLeast(0L)
    }
}

class TimerPayload(val endAt: Long, val totalMs: Long)
