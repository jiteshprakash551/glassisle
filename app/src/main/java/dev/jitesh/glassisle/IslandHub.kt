package dev.jitesh.glassisle

import android.os.SystemClock

/** Process-wide hub between the data sources (listener, receivers) and the overlay. Main thread only. */
object IslandHub {
    interface Sink {
        fun onPost(item: Item, peek: Boolean)
        fun onRemove(id: String)
    }

    val stack = ItemStack()
    var sink: Sink? = null
    var foregroundPkg: String? = null

    var debug = false
    /** Debug-build breadcrumbs only (reasons and kinds, never notification content). */
    fun log(m: String) { if (debug) android.util.Log.i("GI", m) }

    fun now(): Long = SystemClock.elapsedRealtime()

    fun post(item: Item, peek: Boolean = true) {
        stack.put(item)
        sink?.onPost(item, peek)
    }

    fun remove(id: String) {
        if (stack.remove(id) != null) sink?.onRemove(id)
    }

    fun startTimer(ms: Long) {
        val t = now()
        post(
            Item("timer:builtin", Kind.TIMER, "Timer", "", null, t, null, 1f, TimerPayload(t + ms, ms)),
            peek = true
        )
    }

    fun addTimer(ms: Long) {
        val cur = stack.get("timer:builtin") ?: return
        val p = cur.payload as? TimerPayload ?: return
        post(cur.copy(payload = TimerPayload(p.endAt + ms, p.totalMs + ms)), peek = false)
    }

    fun demo() {
        val t = now()
        post(Item("demo", Kind.NOTIFICATION, "GlassIsle", "This is a test notification. Tap to expand.", null, t, t + 8000,
            null, NotifPayload("demo", "GlassIsle", null, null, null, emptyList())))
    }
}
