package com.spellfix.korean

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider

/**
 * ⇧ (Shift, 위쪽 화살표) 모양의 키. 키보드 바로 위에 놓여 있고,
 * 두 번 빠르게 누르면 [onDoubleTap] 이 호출됩니다. 입력창 포커스는 빼앗지 않습니다.
 */
class ShiftKeyView(context: Context) : View(context) {

    var onDoubleTap: (() -> Unit)? = null

    private var lastTap = 0L
    private var armed = false
    private var flash = false

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1f)
        color = 0x14000000
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = context.dpf(1.8f)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val arrow = Path()
    private val rect = RectF()

    private val disarm = Runnable {
        armed = false
        invalidate()
    }
    private val unflash = Runnable {
        flash = false
        invalidate()
    }

    init {
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false
        elevation = context.dpf(4f)
        contentDescription = "Shift 키. 두 번 빠르게 누르면 맞춤법 오류를 자동으로 교정합니다"
        val radius = context.dpf(15f)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = context.dp(58)
        setMeasuredDimension(s, s)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start()
                return true
            }
            MotionEvent.ACTION_UP -> {
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                if (event.x >= 0f && event.x <= width && event.y >= 0f && event.y <= height) {
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (lastTap != 0L && now - lastTap <= DOUBLE_TAP_MS) {
            lastTap = 0L
            armed = false
            removeCallbacks(disarm)
            flash = true
            removeCallbacks(unflash)
            postDelayed(unflash, 260)
            invalidate()
            onDoubleTap?.invoke()
        } else {
            lastTap = now
            armed = true
            removeCallbacks(disarm)
            postDelayed(disarm, DOUBLE_TAP_MS)
            invalidate()
        }
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = context.dpf(15f)

        keyPaint.color = if (flash) Palette.BLUE else Color.WHITE
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, r, r, keyPaint)
        if (!flash) canvas.drawRoundRect(rect, r, r, borderPaint)

        // ⇧ 화살표
        val u = w * 0.46f / 24f
        val ox = (w - 24f * u) / 2f
        val oy = (h - 24f * u) / 2f
        arrow.reset()
        arrow.moveTo(ox + 12f * u, oy + 3f * u)
        arrow.lineTo(ox + 21f * u, oy + 12.5f * u)
        arrow.lineTo(ox + 15.5f * u, oy + 12.5f * u)
        arrow.lineTo(ox + 15.5f * u, oy + 20f * u)
        arrow.lineTo(ox + 8.5f * u, oy + 20f * u)
        arrow.lineTo(ox + 8.5f * u, oy + 12.5f * u)
        arrow.lineTo(ox + 3f * u, oy + 12.5f * u)
        arrow.close()

        when {
            flash -> {
                arrowPaint.color = Color.WHITE
                arrowPaint.style = Paint.Style.FILL_AND_STROKE
            }
            armed -> {
                arrowPaint.color = Palette.BLUE
                arrowPaint.style = Paint.Style.FILL_AND_STROKE
            }
            else -> {
                arrowPaint.color = Palette.TEXT
                arrowPaint.style = Paint.Style.STROKE
            }
        }
        canvas.drawPath(arrow, arrowPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(disarm)
        removeCallbacks(unflash)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DOUBLE_TAP_MS = 350L
    }
}
