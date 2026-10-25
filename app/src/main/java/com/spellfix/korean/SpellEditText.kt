package com.spellfix.korean

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * 입력 중 맞춤법 오류를 찾아 iOS 스타일(반투명 파랑 + 둥근 모서리)로 하이라이트하는 EditText.
 * 하이라이트 양 끝에는 더 짙은 파란색의 둥근 텍스트 커서가 붙습니다.
 */
class SpellEditText(context: Context) : EditText(context) {

    interface Listener {
        /** 오류 목록, 커서, 스크롤이 바뀌고 레이아웃이 확정된 뒤 호출됩니다. */
        fun onStateChanged()

        /** 하드웨어 Shift 키를 두 번 빠르게 눌렀을 때. */
        fun onShiftDoubleTap()
    }

    var engine: SpellEngine? = null
    var listener: Listener? = null

    /** 화면에 표시 중인 오류 (조합 중인 글자는 제외). */
    var visibleIssues: List<SpellIssue> = emptyList()
        private set

    // 슈퍼 클래스 생성자 도중 호출되는 콜백을 막기 위한 플래그
    private var ready = false

    private var allIssues: List<SpellIssue> = emptyList()
    private var batching = false
    private var dirty = false
    private var hlAlpha = 1f

    private val handler = Handler(Looper.getMainLooper())
    private val checkRunnable = Runnable { recheck() }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1f)
    }
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = context.dpf(2.2f)
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    // 하드웨어 Shift 더블탭 감지
    private var lastShiftUp = 0L
    private var otherKeyDuringShift = false

    init {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!batching) scheduleCheck()
            }
        })
        ready = true
    }

    // ------------------------------------------------------------------ 검사

    private fun scheduleCheck() {
        handler.removeCallbacks(checkRunnable)
        val len = text?.length ?: 0
        if (len <= SYNC_LIMIT) {
            recheck()
        } else {
            // 아주 긴 글은 입력이 멈춘 뒤에 검사하고, 그동안 낡은 하이라이트는 숨깁니다.
            allIssues = emptyList()
            visibleIssues = emptyList()
            markDirty()
            handler.postDelayed(checkRunnable, 160)
        }
    }

    fun recheck() {
        val e = engine ?: return
        val ed = text ?: return
        allIssues = e.check(ed.toString())
        refilter()
    }

    /** 지금 조합 중인(아직 완성되지 않은) 글자와 겹치는 오류는 잠시 숨깁니다. */
    private fun refilter() {
        val ed = text
        var cs = -1
        var ce = -1
        if (ed != null) {
            cs = BaseInputConnection.getComposingSpanStart(ed)
            ce = BaseInputConnection.getComposingSpanEnd(ed)
        }
        val vis = if (cs >= 0 && ce > cs) {
            allIssues.filter { it.end <= cs || it.start >= ce }
        } else {
            allIssues
        }
        val wasEmpty = visibleIssues.isEmpty()
        visibleIssues = vis
        if (wasEmpty && vis.isNotEmpty()) fadeIn()
        markDirty()
    }

    private fun fadeIn() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                hlAlpha = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun markDirty() {
        dirty = true
        invalidate()
    }

    // ------------------------------------------------------------------ 교정

    /** 한 건만 교정합니다. */
    fun applyIssue(issue: SpellIssue): Boolean {
        val ed = text ?: return false
        if (issue.end > ed.length || issue.start < 0) return false
        if (ed.subSequence(issue.start, issue.end).toString() != issue.original) return false
        replaceKeepingCursor(ed, issue.start, issue.end, issue.suggestion)
        restartIme()
        return true
    }

    /** 모든 오류를 한 번에 교정하고 교정한 건수를 돌려줍니다. */
    fun applyAll(): Int {
        val e = engine ?: return 0
        val ed = text ?: return 0
        val fresh = e.check(ed.toString())
        if (fresh.isEmpty()) return 0

        var cursor = selectionStart.coerceAtLeast(0)
        batching = true
        try {
            for (issue in fresh.sortedByDescending { it.start }) {
                val delta = issue.suggestion.length - (issue.end - issue.start)
                ed.replace(issue.start, issue.end, issue.suggestion)
                cursor = when {
                    cursor >= issue.end -> cursor + delta
                    cursor > issue.start -> issue.start + issue.suggestion.length
                    else -> cursor
                }
            }
        } finally {
            batching = false
        }
        setSelection(cursor.coerceIn(0, ed.length))
        restartIme()
        recheck()
        return fresh.size
    }

    private fun replaceKeepingCursor(ed: Editable, start: Int, end: Int, replacement: String) {
        val cursor = selectionStart.coerceAtLeast(0)
        val delta = replacement.length - (end - start)
        ed.replace(start, end, replacement)
        val next = when {
            cursor >= end -> cursor + delta
            cursor > start -> start + replacement.length
            else -> cursor
        }
        setSelection(next.coerceIn(0, ed.length))
    }

    /** 조합 중이던 글자 상태를 IME 와 다시 맞춥니다. */
    private fun restartIme() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
    }

    // ------------------------------------------------------------------ 이벤트

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (ready) refilter()
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        if (ready) markDirty()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isShiftKey(keyCode)) {
            if (event.repeatCount == 0) otherKeyDuringShift = false
        } else {
            otherKeyDuringShift = true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isShiftKey(keyCode)) {
            // 다른 키와 조합해서 쓴 Shift 는 무시하고, 단독으로 두 번 눌렀을 때만 반응합니다.
            if (!otherKeyDuringShift) {
                val now = event.eventTime
                if (lastShiftUp != 0L && now - lastShiftUp <= DOUBLE_TAP_MS) {
                    lastShiftUp = 0L
                    listener?.onShiftDoubleTap()
                    return true
                }
                lastShiftUp = now
            } else {
                lastShiftUp = 0L
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isShiftKey(code: Int) =
        code == KeyEvent.KEYCODE_SHIFT_LEFT || code == KeyEvent.KEYCODE_SHIFT_RIGHT

    // ------------------------------------------------------------------ 그리기

    override fun onDraw(canvas: Canvas) {
        if (ready) drawHighlights(canvas)
        super.onDraw(canvas)
        if (ready && dirty) {
            dirty = false
            // 레이아웃이 확정된 이 시점에야 글자 좌표가 정확하므로, 카드 위치 갱신은 여기서 알립니다.
            post { listener?.onStateChanged() }
        }
    }

    private fun drawHighlights(canvas: Canvas) {
        val l = layout ?: return
        val list = visibleIssues
        if (list.isEmpty()) return
        val len = text?.length ?: 0

        val a = hlAlpha
        fillPaint.color = Color.argb((0x38 * a).toInt(), 0, 122, 255)
        edgePaint.color = Color.argb((0x66 * a).toInt(), 0, 122, 255)
        caretPaint.color = Color.argb((0xFF * a).toInt(), 0x0A, 0x52, 0xCC)
        knobPaint.color = caretPaint.color

        val padX = context.dpf(2.5f)
        val padY = context.dpf(1.5f)
        val knobR = context.dpf(3f)
        val radiusDiv = 2.6f
        val ascent = paint.ascent()
        val descent = paint.descent()

        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())

        for (issue in list) {
            if (issue.start < 0 || issue.end > len || issue.start >= issue.end) continue

            val firstLine = l.getLineForOffset(issue.start)
            val lastLine = l.getLineForOffset(issue.end - 1)
            var startX = 0f
            var startTop = 0f
            var startBottom = 0f
            var endX = 0f
            var endTop = 0f
            var endBottom = 0f

            path.reset()
            for (line in firstLine..lastLine) {
                val ls = l.getLineStart(line)
                val le = l.getLineEnd(line)
                val s = maxOf(issue.start, ls)
                val e = minOf(issue.end, le)
                val left = l.getPrimaryHorizontal(s) - padX
                val right = (if (e >= le) l.getLineRight(line) else l.getPrimaryHorizontal(e)) + padX
                if (right <= left) continue

                val baseline = l.getLineBaseline(line).toFloat()
                val top = baseline + ascent - padY
                val bottom = baseline + descent + padY
                rect.set(left, top, right, bottom)
                val r = (bottom - top) / radiusDiv
                path.addRoundRect(rect, r, r, Path.Direction.CW)

                if (line == firstLine) {
                    startX = left; startTop = top; startBottom = bottom
                }
                endX = right; endTop = top; endBottom = bottom
            }
            if (path.isEmpty) continue

            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, edgePaint)

            // 시작 커서: 위쪽에 둥근 노브, 끝 커서: 아래쪽에 둥근 노브 (iOS 선택 핸들 느낌)
            canvas.drawLine(startX, startTop, startX, startBottom, caretPaint)
            canvas.drawCircle(startX, startTop, knobR, knobPaint)
            canvas.drawLine(endX, endTop, endX, endBottom, caretPaint)
            canvas.drawCircle(endX, endBottom, knobR, knobPaint)
        }
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(checkRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        private const val SYNC_LIMIT = 4000
        private const val DOUBLE_TAP_MS = 350L
    }
}
