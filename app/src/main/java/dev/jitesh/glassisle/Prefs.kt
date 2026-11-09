package dev.jitesh.glassisle

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Prefs(ctx: Context) {
    val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("glassisle", Context.MODE_PRIVATE)

    private fun gb(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun pb(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun gi(k: String, d: Int) = sp.getInt(k, d)
    private fun pi(k: String, v: Int) = sp.edit().putInt(k, v).apply()

    var enabled: Boolean get() = gb("enabled", true); set(v) = pb("enabled", v)
    var autoAlign: Boolean get() = gb("autoAlign", true); set(v) = pb("autoAlign", v)
    var position: Int get() = gi("position", 1); set(v) = pi("position", v) // 0 left, 1 center, 2 right
    var xDp: Int get() = gi("xDp", 0); set(v) = pi("xDp", v)
    var yDp: Int get() = gi("yDp", 8); set(v) = pi("yDp", v)
    var widthDp: Int get() = gi("widthDp", 150); set(v) = pi("widthDp", v)
    var heightDp: Int get() = gi("heightDp", 34); set(v) = pi("heightDp", v)
    var peekSec: Int get() = gi("peekSec", 5); set(v) = pi("peekSec", v)
    var accent: Int get() = gi("accent", 0); set(v) = pi("accent", v)
    var hideLandscape: Boolean get() = gb("hideLandscape", true); set(v) = pb("hideLandscape", v)

    var fNotif: Boolean get() = gb("fNotif", true); set(v) = pb("fNotif", v)
    var fMusic: Boolean get() = gb("fMusic", true); set(v) = pb("fMusic", v)
    var fCharge: Boolean get() = gb("fCharge", true); set(v) = pb("fCharge", v)
    var fDevice: Boolean get() = gb("fDevice", true); set(v) = pb("fDevice", v)
    var fTimer: Boolean get() = gb("fTimer", true); set(v) = pb("fTimer", v)
    var fBattery: Boolean get() = gb("fBattery", true); set(v) = pb("fBattery", v)

    var blocked: Set<String>
        get() = sp.getStringSet("blocked", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("blocked", HashSet(v)).apply()

    fun accentColor(): Int = ACCENTS[accent.coerceIn(0, ACCENTS.size - 1)]

    fun featureOn(k: Kind): Boolean = when (k) {
        Kind.CALL, Kind.NOTIFICATION, Kind.LIVE -> fNotif
        Kind.MUSIC -> fMusic
        Kind.CHARGING -> fCharge
        Kind.DEVICE -> fDevice
        Kind.TIMER -> fTimer
        Kind.BATTERY_LOW -> fBattery
    }

    companion object {
        val ACCENTS = intArrayOf(
            Color.parseColor("#0A84FF"), Color.parseColor("#30D158"), Color.parseColor("#FF375F"),
            Color.parseColor("#FF9F0A"), Color.parseColor("#BF5AF2"), Color.parseColor("#F2F2F7")
        )
    }
}
