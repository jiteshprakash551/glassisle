package dev.jitesh.glassisle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

enum class Glyph { PLAY, PAUSE, NEXT, PREV, CLOSE, CHECK, SEND }

class GlyphButton(ctx: Context, var glyph: Glyph, var tint: Int = Color.WHITE, var fill: Int = 0x26FFFFFF) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    init { isClickable = true }

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        alpha = if (pressed) 0.6f else 1f
    }

    private fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, c: Canvas) {
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close(); c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2; val cy = h / 2
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawCircle(cx, cy, minOf(cx, cy), p)
        p.color = tint
        val s = minOf(w, h) * 0.16f
        when (glyph) {
            Glyph.PLAY -> tri(cx - s * 0.8f, cy - s * 1.4f, cx - s * 0.8f, cy + s * 1.4f, cx + s * 1.4f, cy, c)
            Glyph.PAUSE -> {
                r.set(cx - s * 1.1f, cy - s * 1.3f, cx - s * 0.3f, cy + s * 1.3f); c.drawRoundRect(r, 3f, 3f, p)
                r.set(cx + s * 0.3f, cy - s * 1.3f, cx + s * 1.1f, cy + s * 1.3f); c.drawRoundRect(r, 3f, 3f, p)
            }
            Glyph.NEXT -> {
                tri(cx - s * 1.3f, cy - s * 1.2f, cx - s * 1.3f, cy + s * 1.2f, cx + s * 0.5f, cy, c)
                r.set(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.3f, cy + s * 1.2f); c.drawRoundRect(r, 2f, 2f, p)
            }
            Glyph.PREV -> {
                tri(cx + s * 1.3f, cy - s * 1.2f, cx + s * 1.3f, cy + s * 1.2f, cx - s * 0.5f, cy, c)
                r.set(cx - s * 1.3f, cy - s * 1.2f, cx - s * 0.7f, cy + s * 1.2f); c.drawRoundRect(r, 2f, 2f, p)
            }
            Glyph.CLOSE -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.55f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - s, cy - s, cx + s, cy + s, p); c.drawLine(cx + s, cy - s, cx - s, cy + s, p)
            }
            Glyph.CHECK -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.6f; p.strokeCap = Paint.Cap.ROUND
                path.reset(); path.moveTo(cx - s * 1.1f, cy); path.lineTo(cx - s * 0.2f, cy + s * 0.9f); path.lineTo(cx + s * 1.2f, cy - s * 0.9f)
                c.drawPath(path, p)
            }
            Glyph.SEND -> tri(cx - s * 1.2f, cy - s * 1.2f, cx - s * 1.2f, cy + s * 1.2f, cx + s * 1.4f, cy, c)
        }
    }
}

class Slider(ctx: Context) : View(ctx) {
    var value = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    var accent = Color.WHITE
    var interactive = true
    var onSeek: ((Float) -> Unit)? = null
    private var dragging = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat()
        val th = h * 0.28f
        val top = (h - th) / 2
        p.color = 0x33FFFFFF
        r.set(0f, top, w, top + th); c.drawRoundRect(r, th / 2, th / 2, p)
        p.color = accent
        r.set(0f, top, w * value, top + th); c.drawRoundRect(r, th / 2, th / 2, p)
        if (interactive && dragging) { p.color = Color.WHITE; c.drawCircle(w * value, h / 2, h * 0.4f, p) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!interactive) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { dragging = true; parent?.requestDisallowInterceptTouchEvent(true); value = e.x / width }
            MotionEvent.ACTION_MOVE -> value = e.x / width
            MotionEvent.ACTION_UP -> { value = e.x / width; dragging = false; onSeek?.invoke(value); invalidate() }
            MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
        }
        return true
    }
}

/** Animated playing indicator. NOT audio-reactive, it only animates while music plays. */
class BarsView(ctx: Context, var color: Int, var playing: Boolean) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        p.color = color
        val n = 4
        val bw = width / (n * 2f - 1f)
        val t = System.currentTimeMillis() / 140.0
        for (i in 0 until n) {
            val f = if (playing) 0.3f + 0.7f * (0.5f + 0.5f * Math.sin(t + i * 1.7).toFloat()) else 0.25f
            val bh = height * f
            val x = i * bw * 2
            r.set(x, (height - bh) / 2, x + bw, (height + bh) / 2)
            c.drawRoundRect(r, bw / 2, bw / 2, p)
        }
        if (playing) postInvalidateOnAnimation()
    }
}

class RingView(ctx: Context, var progress: Float, var color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val sw = width * 0.14f
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.strokeCap = Paint.Cap.ROUND
        r.set(sw, sw, width - sw, height - sw)
        p.color = 0x33FFFFFF; c.drawArc(r, 0f, 360f, false, p)
        p.color = color; c.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
    }
}
