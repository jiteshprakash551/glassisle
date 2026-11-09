package dev.jitesh.glassisle

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

class AppFilterActivity : Activity() {
    private class Row(val pkg: String, val label: String, val icon: android.graphics.drawable.Drawable)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val prefs = Prefs(this)
        val blocked = HashSet(prefs.blocked)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#0B0B0F")) }
        root.addView(TextView(this).apply {
            text = "Apps that can use the island"; setTextColor(Color.WHITE); textSize = 20f
            setPadding((20 * d).toInt(), (48 * d).toInt(), (20 * d).toInt(), (6 * d).toInt())
        })
        root.addView(TextView(this).apply {
            text = "Turn an app off to stop its notifications from showing."; setTextColor(0xFF8E8E96.toInt()); textSize = 13f
            setPadding((20 * d).toInt(), 0, (20 * d).toInt(), (12 * d).toInt())
        })
        val list = ListView(this).apply { divider = null }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        Thread {
            val pm = packageManager
            val li = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val rows = pm.queryIntentActivities(li, 0).map {
                Row(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm))
            }.filter { it.pkg != packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
            runOnUiThread {
                list.adapter = object : BaseAdapter() {
                    override fun getCount() = rows.size
                    override fun getItem(p: Int) = rows[p]
                    override fun getItemId(p: Int) = p.toLong()
                    override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                        val r = rows[p]
                        val row = LinearLayout(this@AppFilterActivity).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                            setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), (8 * d).toInt())
                        }
                        row.addView(ImageView(this@AppFilterActivity).apply { setImageDrawable(r.icon) },
                            LinearLayout.LayoutParams((36 * d).toInt(), (36 * d).toInt()))
                        row.addView(TextView(this@AppFilterActivity).apply {
                            text = r.label; setTextColor(Color.WHITE); textSize = 16f; setPadding((14 * d).toInt(), 0, 0, 0)
                        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                        val cb = CheckBox(this@AppFilterActivity).apply { isChecked = r.pkg !in blocked }
                        cb.setOnCheckedChangeListener { _, on ->
                            if (on) blocked.remove(r.pkg) else blocked.add(r.pkg)
                            prefs.blocked = blocked
                        }
                        row.addView(cb)
                        row.setOnClickListener { cb.toggle() }
                        return row
                    }
                }
            }
        }.start()
    }
}
