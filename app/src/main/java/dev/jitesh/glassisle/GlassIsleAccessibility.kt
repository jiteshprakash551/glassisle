package dev.jitesh.glassisle

import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class GlassIsleAccessibility : AccessibilityService() {
    private var controller: IslandController? = null
    private var lastPlugged = -1
    private var lastLevel = -1

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val prefs = Prefs(c)
            val now = IslandHub.now()
            when (i.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    if (level < 0) return
                    val pct = level * 100 / scale
                    if (lastPlugged != -1) {
                        if (plugged != 0 && lastPlugged == 0 && prefs.fCharge) {
                            IslandHub.post(Item("charging", Kind.CHARGING, "Charging", "$pct%", null, now, now + 6000, pct / 100f))
                        } else if (plugged != 0 && pct == 100 && lastLevel < 100 && prefs.fCharge) {
                            IslandHub.post(Item("charging", Kind.CHARGING, "Fully charged", "100%", null, now, now + 6000, 1f))
                        } else if (plugged != 0 && IslandHub.stack.contains("charging") && pct != lastLevel) {
                            val cur = IslandHub.stack.get("charging")
                            if (cur != null) IslandHub.post(cur.copy(text = "$pct%", progress = pct / 100f), peek = false)
                        }
                        if (plugged == 0 && lastLevel > 15 && pct <= 15 && prefs.fBattery) {
                            IslandHub.post(Item("battery_low", Kind.BATTERY_LOW, "Battery low", "$pct% remaining", null, now, now + 9000, pct / 100f))
                        }
                    }
                    lastPlugged = plugged; lastLevel = pct
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> if (prefs.fDevice) {
                    val am = c.getSystemService(AudioManager::class.java)
                    val name = when (am.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> "Silent"
                        AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
                        else -> "Ring"
                    }
                    IslandHub.post(Item("ringer", Kind.DEVICE, name, "Sound mode", null, now, now + 3000))
                }
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (prefs.fDevice) {
                    val connected = i.action == BluetoothDevice.ACTION_ACL_CONNECTED
                    val dev: BluetoothDevice? = btDevice(i)
                    val name = try {
                        if (Build.VERSION.SDK_INT < 31 ||
                            c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED)
                            dev?.name else null
                    } catch (_: SecurityException) { null } ?: "Bluetooth device"
                    IslandHub.post(Item("bt", Kind.DEVICE, if (connected) "Connected" else "Disconnected", name, null, now, now + 4000))
                }
                Intent.ACTION_SCREEN_OFF -> controller?.onScreenOff()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun btDevice(i: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val ctl = IslandController(this)
        controller = ctl
        IslandHub.sink = ctl
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, f)
        ctl.refreshNow()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p == packageName || p == "com.android.systemui" || p.contains("inputmethod") || p.contains("keyboard")) return
        IslandHub.foregroundPkg = p
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigChanged()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Throwable) {}
        if (IslandHub.sink === controller) IslandHub.sink = null
        controller?.destroy()
        IslandHub.stack.prune(Long.MAX_VALUE)
        for (k in listOf("demo","charging","ringer","bt","battery_low","timer:builtin","timer:done")) IslandHub.stack.remove(k)
        controller = null
        super.onDestroy()
    }
}
