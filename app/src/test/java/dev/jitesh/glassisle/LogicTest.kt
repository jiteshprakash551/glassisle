package dev.jitesh.glassisle

import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private fun item(id: String, k: Kind, t: Long, exp: Long? = null) =
        Item(id = id, kind = k, title = id, createdAt = t, expiresAt = exp)

    @Test fun highestPriorityWins() {
        val s = ItemStack()
        s.put(item("music", Kind.MUSIC, 1)); s.put(item("n", Kind.NOTIFICATION, 2)); s.put(item("t", Kind.TIMER, 0))
        assertEquals("t", s.top(10)?.id)
    }

    @Test fun tieGoesToNewest() {
        val s = ItemStack()
        s.put(item("a", Kind.NOTIFICATION, 1)); s.put(item("b", Kind.NOTIFICATION, 5))
        assertEquals("b", s.top(10)?.id)
    }

    @Test fun expiredItemsAreIgnoredAndPruned() {
        val s = ItemStack()
        s.put(item("a", Kind.NOTIFICATION, 1, exp = 100)); s.put(item("m", Kind.MUSIC, 1))
        assertEquals("m", s.top(150)?.id)
        assertEquals(listOf("a"), s.prune(150).map { it.id })
        assertEquals(1, s.size())
    }

    @Test fun pruneKeepsProtectedItem() {
        val s = ItemStack()
        s.put(item("a", Kind.NOTIFICATION, 1, exp = 100))
        assertTrue(s.prune(500, keep = "a").isEmpty())
        assertTrue(s.contains("a"))
    }

    @Test fun replaceSameId() {
        val s = ItemStack()
        s.put(item("a", Kind.MUSIC, 1)); s.put(item("a", Kind.CALL, 2))
        assertEquals(1, s.size()); assertEquals(Kind.CALL, s.get("a")?.kind)
    }

    @Test fun clockFormat() {
        assertEquals("0:00", Fmt.clock(-5))
        assertEquals("1:05", Fmt.clock(65_000))
        assertEquals("1:01:01", Fmt.clock(3_661_000))
    }

    @Test fun appFilter() {
        assertFalse(AppFilter.allow("x.y", setOf("x.y"), "own"))
        assertFalse(AppFilter.allow("own", emptySet(), "own"))
        assertTrue(AppFilter.allow("a.b", setOf("x.y"), "own"))
    }
}
