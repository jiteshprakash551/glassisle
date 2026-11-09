package dev.jitesh.glassisle

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private var d = 1f
    private lateinit var status: LinearLayout

    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this); d = resources.displayMetrics.density
        status = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(52), dp(18), dp(40)) }
        root.addView(txt("GlassIsle", 34f, Color.WHITE, true))
        root.addView(txt("A dynamic island for Android. Free, open source, no internet access.", 14f, 0xFF8E8E96.toInt()).apply { setPadding(0, dp(4), 0, dp(18)) })

        root.addView(card(status))
        root.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(switchRow("Island enabled", prefs.enabled) { prefs.enabled = it })
            addView(switchRow("Auto-align to camera cutout", prefs.autoAlign) { prefs.autoAlign = it })
            addView(switchRow("Hide in landscape", prefs.hideLandscape) { prefs.hideLandscape = it })
            addView(btnRow(
                btn("Test notification", true) { IslandHub.demo() },
                btn("30 s timer", false) { IslandHub.startTimer(30_000) }
            ))
        }))

        root.addView(section("Look and position"))
        root.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("Accent", 13f, 0xFF8E8E96.toInt()))
            addView(swatches())
            addView(txt("Manual position (when auto-align is off or no cutout is found)", 12f, 0xFF8E8E96.toInt()).apply { setPadding(0, dp(14), 0, dp(6)) })
            addView(btnRow(btn("Left", false) { prefs.position = 0 }, btn("Center", false) { prefs.position = 1 }, btn("Right", false) { prefs.position = 2 }))
            addView(slider("Pill width", 90, 260, prefs.widthDp, "dp") { prefs.widthDp = it })
            addView(slider("Pill height", 24, 56, prefs.heightDp, "dp") { prefs.heightDp = it })
            addView(slider("Vertical offset", 0, 80, prefs.yDp, "dp") { prefs.yDp = it })
            addView(slider("Horizontal fine-tune", -80, 80, prefs.xDp, "dp") { prefs.xDp = it })
            addView(slider("Peek time", 2, 12, prefs.peekSec, "s") { prefs.peekSec = it })
        }))

        root.addView(section("What shows up"))
        root.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(switchRow("Notifications, calls and navigation", prefs.fNotif) { prefs.fNotif = it })
            addView(switchRow("Music controls", prefs.fMusic) { prefs.fMusic = it })
            addView(switchRow("Charging and full-battery", prefs.fCharge) { prefs.fCharge = it })
            addView(switchRow("Low battery alert", prefs.fBattery) { prefs.fBattery = it })
            addView(switchRow("Bluetooth and sound-mode changes", prefs.fDevice) { prefs.fDevice = it })
            addView(switchRow("Timers", prefs.fTimer) { prefs.fTimer = it })
            addView(btnRow(btn("Choose apps", false) { startActivity(Intent(this@MainActivity, AppFilterActivity::class.java)) }))
        }))

        root.addView(section("Keep it alive"))
        root.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("Some phones (Realme, Oppo, Xiaomi) kill background services. Exempt GlassIsle from battery optimization and allow auto-start.", 13f, 0xFFB0B0B8.toInt()))
            addView(btnRow(
                btn("Battery settings", false) { try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Throwable) {} },
                btn("Bluetooth permission", false) { if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf("android.permission.BLUETOOTH_CONNECT"), 1) }
            ))
        }))

        root.addView(section("About"))
        root.addView(card(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(txt("Built by Jitesh", 16f, Color.WHITE, true))
            addView(txt("jiteshprakash.com.np", 14f, prefs.accentColor()).apply {
                setPadding(0, dp(4), 0, dp(8))
                setOnClickListener { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://jiteshprakash.com.np"))) } catch (_: Throwable) {} }
            })
            addView(txt("Open source (MIT). The app declares no INTERNET permission, so it cannot send anything off your phone. Notification text is only used to draw the island and is never stored.", 12f, 0xFF8E8E96.toInt()))
        }))
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0B0B0F")); addView(root) })
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    private fun enabledIn(setting: String, needle: String): Boolean =
        (Settings.Secure.getString(contentResolver, setting) ?: "").contains(needle)

    private fun refreshStatus() {
        status.removeAllViews()
        val a11y = enabledIn("enabled_accessibility_services", "$packageName/")
        val notif = enabledIn("enabled_notification_listeners", packageName)
        status.addView(txt("Setup", 13f, 0xFF8E8E96.toInt()))
        status.addView(statusRow("Accessibility (draws the island)", a11y) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        status.addView(statusRow("Notification access (notifications and music)", notif) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
    }

    private fun statusRow(label: String, ok: Boolean, onFix: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, 0)
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (ok) Color.parseColor("#30D158") else Color.parseColor("#FF453A")) }
        }, LinearLayout.LayoutParams(dp(10), dp(10)))
        addView(txt(label, 14f, Color.WHITE).apply { setPadding(dp(10), 0, 0, 0) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!ok) addView(btn("Enable", true, onFix))
        else addView(txt("On", 13f, 0xFF30D158.toInt()))
    }

    private fun txt(s: String, sp: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = sp; setTextColor(c); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun section(s: String) = txt(s, 13f, 0xFF8E8E96.toInt()).apply { setPadding(dp(4), dp(22), 0, dp(8)) }

    private fun card(inner: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF1C1C21.toInt(), 0xFF141418.toInt())).apply {
            cornerRadius = dpF(22f); setStroke(dp(1), 0x22FFFFFF)
        }
        addView(inner)
    }

    private fun dpF(v: Float) = v * d

    private fun switchRow(label: String, init: Boolean, on: (Boolean) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, dp(6))
        addView(txt(label, 15f, Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Switch(context).apply { isChecked = init; setOnCheckedChangeListener { _, v -> on(v) } })
    }

    private fun btn(label: String, primary: Boolean, click: () -> Unit) = txt(label, 14f, if (primary) Color.BLACK else Color.WHITE, true).apply {
        gravity = Gravity.CENTER; setPadding(dp(16), dp(10), dp(16), dp(10))
        background = GradientDrawable().apply { cornerRadius = dpF(20f); setColor(if (primary) prefs.accentColor() else 0x2EFFFFFF) }
        isClickable = true; setOnClickListener { click() }
    }

    private fun btnRow(vararg v: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0)
        v.forEachIndexed { i, x -> addView(x, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) marginStart = dp(8) }) }
    }

    private fun swatches() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0)
        Prefs.ACCENTS.forEachIndexed { i, c ->
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(2), if (i == prefs.accent) Color.WHITE else 0x00000000) }
                setOnClickListener { prefs.accent = i; recreate() }
            }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(10) })
        }
    }

    private fun slider(label: String, min: Int, max: Int, init: Int, unit: String, on: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0)
        val t = txt("$label: $init $unit", 13f, Color.WHITE)
        addView(t)
        addView(SeekBar(context).apply {
            this.max = max - min; progress = init - min
            progressTintList = ColorStateList.valueOf(prefs.accentColor()); thumbTintList = ColorStateList.valueOf(prefs.accentColor())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { val v = p + min; t.text = "$label: $v $unit"; if (u) on(v) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }
}
