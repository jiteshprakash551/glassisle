package dev.jitesh.glassisle

/** Pure logic (no Android imports) so it can be unit tested on the JVM. */
enum class Kind(val priority: Int) {
    CALL(100), BATTERY_LOW(90), TIMER(80), NOTIFICATION(70), CHARGING(60), DEVICE(55), LIVE(52), MUSIC(50)
}

data class Item(
    val id: String,
    val kind: Kind,
    val title: String,
    val text: String = "",
    val pkg: String? = null,
    val createdAt: Long = 0L,
    val expiresAt: Long? = null,
    val progress: Float? = null,
    val payload: Any? = null
)

class ItemStack {
    private val items = LinkedHashMap<String, Item>()

    fun put(i: Item) { items[i.id] = i }
    fun remove(id: String): Item? = items.remove(id)
    fun get(id: String): Item? = items[id]
    fun size(): Int = items.size
    fun contains(id: String): Boolean = items.containsKey(id)

    /** Removes expired items (except [keep]) and returns them. */
    fun prune(now: Long, keep: String? = null): List<Item> {
        val dead = items.values.filter { it.id != keep && it.expiresAt != null && it.expiresAt <= now }
        dead.forEach { items.remove(it.id) }
        return dead
    }

    /** Highest priority wins; ties go to the most recent. */
    fun top(now: Long): Item? =
        items.values.filter { it.expiresAt == null || it.expiresAt > now }
            .maxWithOrNull(compareBy<Item>({ it.kind.priority }, { it.createdAt }))
}

object Fmt {
    fun clock(ms: Long): String {
        val s = (if (ms < 0) 0 else ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}

object AppFilter {
    fun allow(pkg: String, blocked: Set<String>, own: String): Boolean =
        pkg != own && pkg !in blocked
}
