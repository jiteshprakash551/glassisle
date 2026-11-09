package dev.jitesh.glassisle

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.app.RemoteInput

class IslandController(private val svc: AccessibilityService) : IslandHub.Sink {
    enum class Mode { HIDDEN, PILL, PEEK, EXPANDED }

    private val ctx: Context = svc
    private val wm = svc.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs(ctx)
    private val h = Handler(Looper.getMainLooper())
    private val d = ctx.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private fun dpf(v: Float) = v * d

    private var mode = Mode.HIDDEN
    private var shownId: String? = null
    private var attached = false
    private var peekEnd = 0L
    private var expandEnd = 0L
    private var replying = false
    private var anim: ValueAnimator? = null
    private var tickOn = false
    private var radius = 0f

    private val bg = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE }
    private val gestures: GestureDetector
    private val card: FrameLayout
    private lateinit var lp: WindowManager.LayoutParams

    // dynamic refs
    private var timerTv: TextView? = null
    private var slider: Slider? = null
    private var posTv: TextView? = null
    private var durTv: TextView? = null
    private var playBtn: GlyphButton? = null
    private var timerSlider: Slider? = null
    private var chronoTv: TextView? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> h.post { onPrefsChanged() } }

    init {
        gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { onCardTap(); return true }
            override fun onLongPress(e: MotionEvent) { current()?.let { openItem(it) } }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -800f) { dismiss(); return true }
                return false
            }
        })
        card = object : FrameLayout(ctx) {
            override fun onTouchEvent(e: MotionEvent): Boolean = gestures.onTouchEvent(e) || super.onTouchEvent(e)
        }
        card.background = bg
        card.clipToOutline = true
        prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
    }

    // ---------- Sink ----------
    override fun onPost(item: Item, peek: Boolean) {
        IslandHub.log("onPost kind=${item.kind} peek=$peek mode=$mode")
        if (!prefs.enabled) return
        val now = IslandHub.now()
        if (locked()) { IslandHub.log("locked: island stays hidden"); if (mode != Mode.HIDDEN) hide(); return }
        if (mode == Mode.EXPANDED && shownId == item.id) { rerender(item, Mode.EXPANDED); return }
        val top = IslandHub.stack.top(now)
        if (peek && mode != Mode.EXPANDED && !landscapeHidden() && (top == null || top.id == item.id)) {
            peekEnd = now + prefs.peekSec * 1000L
            render(item, Mode.PEEK, animate = !(mode == Mode.PEEK && shownId == item.id))
        } else if (shownId == item.id && mode != Mode.HIDDEN) {
            rerender(item, mode)
        } else refresh()
    }

    override fun onRemove(id: String) {
        if (shownId == id) { if (mode == Mode.EXPANDED) collapse() else refresh() }
    }

    // ---------- state ----------
    private fun current(): Item? = shownId?.let { IslandHub.stack.get(it) }

    fun refreshNow() = refresh()

    private fun locked(): Boolean =
        try { ctx.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked } catch (_: Throwable) { false }

    private fun landscapeHidden() =
        prefs.hideLandscape && ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun refresh() {
        if (mode == Mode.EXPANDED) return
        val now = IslandHub.now()
        if (!prefs.enabled || landscapeHidden() || locked()) { hide(); return }
        val top = IslandHub.stack.top(now)
        if (top == null) { hide(); return }
        if (mode == Mode.PEEK && now < peekEnd && shownId == top.id) return
        if (mode == Mode.PILL && shownId == top.id) return
        render(top, Mode.PILL, animate = true)
    }

    private fun collapse() {
        replying = false; setFocusable(false)
        mode = Mode.PEEK; peekEnd = 0L
        refresh()
    }

    private fun dismiss() {
        val id = shownId ?: return
        replying = false; setFocusable(false)
        IslandHub.stack.remove(id)
        mode = Mode.PEEK; peekEnd = 0L
        refresh()
    }

    fun onScreenOff() { hide() }
    fun onConfigChanged() { if (landscapeHidden()) hide() else refresh() }
    private fun onPrefsChanged() {
        if (!prefs.enabled) { hide(); return }
        val it = current()
        if (it != null && mode != Mode.HIDDEN) rerender(it, mode) else refresh()
    }

    fun destroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        h.removeCallbacksAndMessages(null)
        anim?.cancel()
        if (attached) try { wm.removeView(card) } catch (_: Throwable) {}
        attached = false
    }

    // ---------- geometry ----------
    private fun screenW() = ctx.resources.displayMetrics.widthPixels

    private fun cutout(): Rect? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            val cut = wm.currentWindowMetrics.windowInsets.displayCutout ?: return null
            val sh = ctx.resources.displayMetrics.heightPixels
            cut.boundingRects.firstOrNull { it.top <= dp(4) && it.centerY() < sh / 3 }
        } catch (_: Throwable) { null }
    }

    private fun pillH(): Int {
        val c = if (prefs.autoAlign) cutout() else null
        val base = dp(prefs.heightDp)
        return if (c != null) maxOf(base, c.height() + dp(6)) else base
    }

    private fun pillTop(hh: Int): Int {
        val c = if (prefs.autoAlign) cutout() else null
        return if (c != null) maxOf(dp(2), c.centerY() - hh / 2) else dp(prefs.yDp)
    }

    private fun xFor(width: Int, pill: Boolean = true): Int {
        if (!pill) return 0 // peek / expanded cards are centered on the screen
        val sw = screenW()
        val c = if (prefs.autoAlign) cutout() else null
        val base = if (c != null) c.centerX() - sw / 2 else when (prefs.position) {
            0 -> -(sw / 2 - width / 2 - dp(16))
            2 -> (sw / 2 - width / 2 - dp(16))
            else -> 0
        } + dp(prefs.xDp)
        val maxOff = maxOf(0, (sw - width) / 2 - dp(8))
        return base.coerceIn(-maxOff, maxOff)
    }

    private fun widthFor(m: Mode): Int {
        val sw = screenW()
        return when (m) {
            Mode.PILL -> dp(prefs.widthDp)
            Mode.PEEK -> minOf(sw - dp(24), dp(340))
            else -> minOf(sw - dp(24), dp(372))
        }
    }

    // ---------- window ----------
    private fun baseParams(): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(
            dp(prefs.widthDp), dp(prefs.heightDp),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.title = "GlassIsle"
        if (Build.VERSION.SDK_INT >= 30) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        else if (Build.VERSION.SDK_INT >= 28) p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        return p
    }

    private fun setFocusable(on: Boolean) {
        if (!attached) return
        lp.flags = if (on) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try { wm.updateViewLayout(card, lp) } catch (_: Throwable) {}
    }

    private fun applyBlur(on: Boolean) {
        if (Build.VERSION.SDK_INT < 31 || !attached) return
        try {
            if (on) { lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND; lp.blurBehindRadius = dp(24) }
            else { lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv(); lp.blurBehindRadius = 0 }
        } catch (_: Throwable) {}
    }

    private fun hide() {
        IslandHub.log("hide (mode was $mode)")
        anim?.cancel()
        mode = Mode.HIDDEN; shownId = null
        if (!attached) return
        card.animate().cancel()
        card.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f).setDuration(180).withEndAction {
            if (mode == Mode.HIDDEN && attached) {
                try { wm.removeView(card) } catch (_: Throwable) {}
                attached = false
                card.removeAllViews()
            }
        }.start()
    }

    // ---------- rendering ----------
    private fun rerender(item: Item, m: Mode) = render(item, m, animate = false)

    private fun render(item: Item, newMode: Mode, animate: Boolean) {
        IslandHub.log("render ${item.kind} -> $newMode animate=$animate")
        val wasHidden = mode == Mode.HIDDEN
        mode = newMode; shownId = item.id
        if (newMode == Mode.EXPANDED && expandEnd < IslandHub.now()) expandEnd = IslandHub.now() + 12_000
        val w = widthFor(newMode)
        val content = build(item, newMode)
        val hh: Int
        if (newMode == Mode.PILL) {
            hh = pillH()
        } else {
            content.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            hh = content.measuredHeight
        }
        card.removeAllViews()
        card.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            if (newMode == Mode.PILL) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT))
        styleBg(newMode)
        val toR = if (newMode == Mode.PILL) hh / 2f else minOf(dpf(30f), hh / 2f)
        val toTop = pillTop(pillH())
        val toX = xFor(w, newMode == Mode.PILL)
        if (!attached) {
            lp = baseParams()
            lp.width = widthFor(Mode.PILL); lp.height = pillH(); lp.x = xFor(lp.width); lp.y = toTop
            radius = lp.height / 2f; bg.cornerRadius = radius
            card.alpha = 0f; card.scaleX = 0.6f; card.scaleY = 0.6f
            applyBlur(newMode != Mode.PILL)
            try { wm.addView(card, lp); attached = true } catch (e: Throwable) { mode = Mode.HIDDEN; return }
        } else {
            applyBlur(newMode != Mode.PILL)
        }
        card.animate().cancel()
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(1.1f)).start()
        lp.y = toTop
        anim?.cancel()
        if (!animate && !wasHidden) {
            lp.width = w; lp.height = hh; lp.x = toX; radius = toR; bg.cornerRadius = toR
            try { wm.updateViewLayout(card, lp) } catch (_: Throwable) {}
            card.invalidateOutline()
        } else {
            val fw = lp.width; val fh = lp.height; val fx = lp.x; val fr = radius
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 360
                interpolator = if (newMode == Mode.PILL) DecelerateInterpolator(1.5f) else OvershootInterpolator(0.85f)
                addUpdateListener {
                    val f = it.animatedValue as Float
                    lp.width = (fw + (w - fw) * f).toInt().coerceAtLeast(dp(20))
                    lp.height = (fh + (hh - fh) * f).toInt().coerceAtLeast(dp(20))
                    lp.x = (fx + (toX - fx) * f).toInt()
                    radius = fr + (toR - fr) * f
                    bg.cornerRadius = minOf(radius, minOf(lp.width, lp.height) / 2f)
                    try { wm.updateViewLayout(card, lp) } catch (_: Throwable) {}
                    card.invalidateOutline()
                }
                start()
            }
            content.alpha = 0f
            content.animate().alpha(1f).setStartDelay(90).setDuration(200).start()
        }
        startTick()
    }

    private fun styleBg(m: Mode) {
        if (m == Mode.PILL) {
            bg.colors = intArrayOf(Color.BLACK, Color.BLACK)
            bg.setStroke(dp(1), 0x1FFFFFFF)
        } else {
            val a = if (Build.VERSION.SDK_INT >= 31) 0xEE else 0xF6
            bg.orientation = GradientDrawable.Orientation.TOP_BOTTOM
            bg.colors = intArrayOf((a shl 24) or 0x2A2A30, (a shl 24) or 0x0E0E12)
            bg.setStroke(dp(1), 0x33FFFFFF)
        }
    }

    // ---------- tick ----------
    private val tick = object : Runnable {
        override fun run() {
            if (mode == Mode.HIDDEN) { tickOn = false; return }
            if (locked()) { hide(); tickOn = false; return }
            val now = IslandHub.now()
            IslandHub.stack.get("timer:builtin")?.let { t ->
                val p = t.payload as TimerPayload
                if (now >= p.endAt) {
                    IslandHub.stack.remove("timer:builtin")
                    buzz()
                    IslandHub.post(Item("timer:done", Kind.TIMER, "Timer done", "", null, now, now + 10_000))
                }
            }
            IslandHub.stack.prune(now, keep = if (mode == Mode.EXPANDED) shownId else null)
            when (mode) {
                Mode.PEEK -> if (now >= peekEnd) refresh()
                Mode.EXPANDED -> if (now >= expandEnd && !replying) collapse()
                Mode.PILL -> refresh()
                else -> {}
            }
            if (mode != Mode.HIDDEN) {
                val cur = current()
                if (cur == null && mode != Mode.EXPANDED) refresh() else if (cur == null) collapse()
                updateDynamic()
            }
            h.postDelayed(this, 500)
        }
    }

    private fun startTick() { if (!tickOn) { tickOn = true; h.postDelayed(tick, 500) } }

    private fun buzz() {
        try {
            val v = ctx.getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
        } catch (_: Throwable) {}
    }

    private fun chronoText(p: NotifPayload): String {
        val now = System.currentTimeMillis()
        return Fmt.clock(if (p.countDown) p.chronoWhen - now else now - p.chronoWhen)
    }

    private fun updateDynamic() {
        val it = current() ?: return
        val now = IslandHub.now()
        (it.payload as? TimerPayload)?.let { p ->
            val rem = p.endAt - now
            timerTv?.text = Fmt.clock(rem)
            timerSlider?.value = if (p.totalMs > 0) rem.toFloat() / p.totalMs else 0f
        }
        (it.payload as? NotifPayload)?.let { p -> if (p.hasChrono) { val s = chronoText(p); chronoTv?.text = s; timerTv?.text = s } }
        (it.payload as? MusicPayload)?.let { p ->
            val pos = p.positionNow(now)
            if (p.duration > 0) slider?.value = pos.toFloat() / p.duration
            posTv?.text = Fmt.clock(pos)
            durTv?.text = Fmt.clock(p.duration)
            playBtn?.glyph = if (p.playing) Glyph.PAUSE else Glyph.PLAY
            playBtn?.invalidate()
        }
    }

    // ---------- interactions ----------
    private fun bumpExpand() { expandEnd = IslandHub.now() + 12_000 }

    private fun onCardTap() {
        val it = current() ?: return
        when (mode) {
            Mode.PILL, Mode.PEEK -> { bumpExpand(); render(it, Mode.EXPANDED, animate = true) }
            Mode.EXPANDED -> collapse()
            else -> {}
        }
    }

    private fun openItem(it: Item) {
        val pi = (it.payload as? NotifPayload)?.content
        var done = false
        if (pi != null) try { pi.send(); done = true } catch (_: Throwable) {}
        if (!done) {
            val pkg = it.pkg
            val i = if (pkg != null) ctx.packageManager.getLaunchIntentForPackage(pkg) else null
            if (i != null) try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); done = true } catch (_: Throwable) {}
        }
        (it.payload as? MusicPayload)?.let { mp ->
            try { mp.controller.sessionActivity?.send(); done = true } catch (_: Throwable) {}
        }
        if (done) { if (mode == Mode.EXPANDED) collapse() else refresh() }
    }

    private fun sendReply(a: NAction, text: String) {
        val ri = a.remote ?: return
        try {
            val intent = Intent()
            val b = Bundle(); b.putCharSequence(ri.resultKey, text)
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, b)
            a.pi?.send(ctx, 0, intent)
        } catch (_: Throwable) {}
        replying = false; setFocusable(false); collapse()
    }

    // ---------- view building ----------
    private fun tv(text: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(ctx).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        includeFontPadding = false
    }

    private fun roundedBg(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

    private fun glyphEmoji(k: Kind) = when (k) {
        Kind.CALL -> "\uD83D\uDCDE"; Kind.CHARGING -> "\u26A1"; Kind.BATTERY_LOW -> "\uD83D\uDD0B"
        Kind.TIMER -> "\u23F1"; Kind.DEVICE -> "\uD83C\uDFA7"; Kind.LIVE -> "\uD83E\uDDED"
        Kind.MUSIC -> "\u266B"; Kind.NOTIFICATION -> "\uD83D\uDD14"
    }

    private fun iconView(item: Item, size: Int): View {
        val accent = prefs.accentColor()
        (item.payload as? MusicPayload)?.art?.let { b ->
            return ImageView(ctx).apply {
                setImageBitmap(b); scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBg(0x22FFFFFF, size / 4f); clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        }
        val np = item.payload as? NotifPayload
        val dr = np?.largeIcon ?: np?.icon
        if (dr != null) {
            return ImageView(ctx).apply {
                setImageDrawable(dr.constantState?.newDrawable()?.mutate() ?: dr)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = roundedBg(0x22FFFFFF, size / 4f); clipToOutline = true
                layoutParams = LinearLayout.LayoutParams(size, size)
            }
        }
        return TextView(ctx).apply {
            text = glyphEmoji(item.kind); gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.55f)
            setTextColor(accent)
            background = roundedBg(0x22FFFFFF, size / 4f)
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun trailing(item: Item, compact: Boolean): View {
        val accent = prefs.accentColor()
        val sp = if (compact) 12f else 15f
        when (item.kind) {
            Kind.MUSIC -> return BarsView(ctx, accent, (item.payload as? MusicPayload)?.playing == true)
                .apply { layoutParams = LinearLayout.LayoutParams(dp(if (compact) 18 else 24), dp(if (compact) 14 else 20)) }
            Kind.TIMER -> {
                val t = tv("", sp, accent, true); timerTv = t; return t
            }
            Kind.CHARGING, Kind.BATTERY_LOW -> {
                val pct = ((item.progress ?: 0f) * 100).toInt()
                val col = if (item.kind == Kind.BATTERY_LOW) Color.parseColor("#FF453A") else Color.parseColor("#30D158")
                return LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(RingView(ctx, item.progress ?: 0f, col), LinearLayout.LayoutParams(dp(if (compact) 16 else 22), dp(if (compact) 16 else 22)))
                    if (compact) addView(tv("$pct%", sp, col, true).apply { setPadding(dp(5), 0, 0, 0) })
                }
            }
            else -> {
                val np = item.payload as? NotifPayload
                if (np != null && np.hasChrono) { val t = tv("", sp, accent, true); timerTv = t; return t }
                return View(ctx).apply {
                    background = roundedBg(accent, dpf(10f))
                    layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
                }
            }
        }
    }

    private fun build(item: Item, m: Mode): View {
        timerTv = null; slider = null; posTv = null; durTv = null; playBtn = null; timerSlider = null; chronoTv = null
        return when (m) {
            Mode.PILL -> buildPill(item)
            Mode.PEEK -> buildPeek(item)
            else -> buildExpanded(item)
        }
    }

    private fun buildPill(item: Item): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(14), 0)
        addView(iconView(item, dp(20)))
        addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        addView(trailing(item, true))
        updateDynamicLater()
    }

    private fun updateDynamicLater() { h.post { updateDynamic() } }

    private fun buildPeek(item: Item): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(18), dp(14))
        addView(iconView(item, dp(42)))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        col.addView(tv(item.title, 15f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        if (item.text.isNotEmpty()) col.addView(tv(item.text, 13f, 0xFFB0B0B8.toInt()).apply {
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0)
        })
        addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(trailing(item, false))
        updateDynamicLater()
    }

    private fun pillButton(label: String, primary: Boolean, onClick: () -> Unit): TextView =
        tv(label, 14f, if (primary) Color.BLACK else Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = roundedBg(if (primary) prefs.accentColor() else 0x2EFFFFFF, dpf(20f))
            isClickable = true
            setOnClickListener { bumpExpand(); onClick() }
        }

    private fun buildExpanded(item: Item): View {
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
        val accent = prefs.accentColor()
        when (item.kind) {
            Kind.MUSIC -> {
                val mp = item.payload as MusicPayload
                val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(iconView(item, dp(64)).apply { setOnClickListener { openItem(item) } }, LinearLayout.LayoutParams(dp(64), dp(64)))
                val tc = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
                tc.addView(tv(item.title, 17f, Color.WHITE, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                tc.addView(tv(item.text, 14f, 0xFFB0B0B8.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, 0) })
                top.addView(tc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                top.addView(BarsView(ctx, accent, mp.playing), LinearLayout.LayoutParams(dp(26), dp(20)))
                col.addView(top)
                val sl = Slider(ctx).apply {
                    this.accent = accent
                    onSeek = { f -> if (mp.duration > 0) try { mp.controller.transportControls.seekTo((f * mp.duration).toLong()) } catch (_: Throwable) {}; bumpExpand() }
                }
                slider = sl
                col.addView(sl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)).apply { topMargin = dp(10) })
                val times = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                posTv = tv("0:00", 11f, 0xFF8E8E96.toInt()); durTv = tv("0:00", 11f, 0xFF8E8E96.toInt())
                times.addView(posTv); times.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f)); times.addView(durTv)
                col.addView(times)
                val ctl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                val tc2 = mp.controller.transportControls
                val prev = GlyphButton(ctx, Glyph.PREV).apply { setOnClickListener { bumpExpand(); try { tc2.skipToPrevious() } catch (_: Throwable) {} } }
                val play = GlyphButton(ctx, if (mp.playing) Glyph.PAUSE else Glyph.PLAY, Color.BLACK, accent).apply {
                    setOnClickListener { bumpExpand(); try { if (mp.playing) tc2.pause() else tc2.play() } catch (_: Throwable) {} }
                }
                playBtn = play
                val next = GlyphButton(ctx, Glyph.NEXT).apply { setOnClickListener { bumpExpand(); try { tc2.skipToNext() } catch (_: Throwable) {} } }
                ctl.addView(prev, LinearLayout.LayoutParams(dp(46), dp(46)))
                ctl.addView(play, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(22); marginEnd = dp(22) })
                ctl.addView(next, LinearLayout.LayoutParams(dp(46), dp(46)))
                col.addView(ctl, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
            }
            Kind.TIMER -> {
                val tp = item.payload as? TimerPayload
                col.addView(tv(item.title, 14f, 0xFFB0B0B8.toInt()))
                val big = tv("", 44f, Color.WHITE, true).apply { setPadding(0, dp(6), 0, dp(8)) }
                timerTv = big; col.addView(big)
                if (tp != null) {
                    val ts = Slider(ctx).apply { this.accent = accent; interactive = false }
                    timerSlider = ts
                    col.addView(ts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))
                    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
                    row.addView(pillButton("+1 min", false) { IslandHub.addTimer(60_000) })
                    row.addView(View(ctx), LinearLayout.LayoutParams(dp(10), 1))
                    row.addView(pillButton("Cancel", true) { IslandHub.remove("timer:builtin"); collapse() })
                    col.addView(row)
                } else if (item.text.isNotEmpty()) {
                    col.addView(tv(item.text, 14f, 0xFFB0B0B8.toInt()))
                }
            }
            Kind.CHARGING, Kind.BATTERY_LOW, Kind.DEVICE -> {
                col.addView(tv(item.title, 22f, Color.WHITE, true))
                if (item.text.isNotEmpty()) col.addView(tv(item.text, 15f, 0xFFB0B0B8.toInt()).apply { setPadding(0, dp(4), 0, 0) })
                item.progress?.let { pr ->
                    val s = Slider(ctx).apply {
                        this.accent = if (item.kind == Kind.BATTERY_LOW) Color.parseColor("#FF453A") else Color.parseColor("#30D158")
                        interactive = false; value = pr
                    }
                    col.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)).apply { topMargin = dp(10) })
                }
            }
            else -> notifExpanded(col, item)
        }
        updateDynamicLater()
        return col
    }

    private fun notifExpanded(col: LinearLayout, item: Item) {
        val np = item.payload as? NotifPayload
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(iconView(item, dp(22)))
        head.addView(tv(np?.appName ?: "", 12f, 0xFF8E8E96.toInt()).apply { setPadding(dp(8), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (np?.hasChrono == true) { val t = tv("", 13f, prefs.accentColor(), true); timerTv = t; head.addView(t) }
        col.addView(head)
        val title = tv(item.title, 17f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0); isClickable = true; setOnClickListener { _ -> openItem(item) } }
        col.addView(title)
        if (item.text.isNotEmpty()) col.addView(tv(item.text, 14f, 0xFFC7C7CC.toInt()).apply {
            maxLines = 6; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(5), 0, 0)
            isClickable = true; setOnClickListener { _ -> openItem(item) }
        })
        if (np == null) return
        val replyAct = np.actions.firstOrNull { a -> a.remote != null }
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        var n = 0
        row.addView(pillButton("Open", true) { openItem(item) }); n++
        for (a in np.actions) {
            if (a.remote != null || a.title.isEmpty()) continue
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(pillButton(a.title, false) { try { a.pi?.send() } catch (_: Throwable) {}; collapse() }); n++
        }
        if (replyAct != null) {
            row.addView(View(ctx), LinearLayout.LayoutParams(dp(8), 1))
            row.addView(pillButton("Reply", false) { startReply(col, replyAct) })
        }
        col.addView(row)
    }

    private fun startReply(col: LinearLayout, a: NAction) {
        replying = true; bumpExpand(); expandEnd = IslandHub.now() + 60_000
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, 0) }
        val et = EditText(ctx).apply {
            hint = "Reply"; setHintTextColor(0xFF8E8E96.toInt()); setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setSingleLine(true)
            background = roundedBg(0x26FFFFFF, dpf(20f)); setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        box.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(GlyphButton(ctx, Glyph.SEND, Color.BLACK, prefs.accentColor()).apply {
            setOnClickListener { val t = et.text.toString().trim(); if (t.isNotEmpty()) sendReply(a, t) }
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8) })
        col.addView(box)
        col.measure(View.MeasureSpec.makeMeasureSpec(lp.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        lp.height = col.measuredHeight
        setFocusable(true)
        et.requestFocus()
        h.postDelayed({ try { (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, 0) } catch (_: Throwable) {} }, 150)
    }
}
