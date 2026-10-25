package com.spellfix.korean

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity(), SpellEditText.Listener {

    private lateinit var root: FrameLayout
    private lateinit var wrap: FrameLayout
    private lateinit var editor: SpellEditText
    private lateinit var card: CorrectionCard
    private lateinit var status: TextView

    private var cardIssue: SpellIssue? = null
    private var messageUntil = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val engine = try {
            assets.open("rules.txt").use { SpellEngine.load(it) }
        } catch (e: Exception) {
            SpellEngine(emptyList())
        }

        root = FrameLayout(this).apply { setBackgroundColor(Palette.BG) }

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val title = TextView(this).apply {
            text = "맞춤법 교정"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Palette.TEXT)
            setPadding(dp(22), dp(20), dp(22), 0)
        }
        status = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Palette.GRAY)
            setPadding(dp(22), dp(2), dp(22), dp(10))
            text = "입력을 시작해 보세요"
        }
        column.addView(title)
        column.addView(status)

        wrap = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpf(22f)
                setColor(0xFFFFFFFF.toInt())
            }
        }
        editor = SpellEditText(this).apply {
            this.engine = engine
            listener = this@MainActivity
            background = null
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Palette.TEXT)
            setHintTextColor(0xFFC7C7CC.toInt())
            hint = "여기에 입력해 보세요…  예) 내일 갈께요, 몇일 걸려요"
            setLineSpacing(dpf(3f), 1f)
            // 시스템 빨간 밑줄/자동 제안 대신 이 앱의 하이라이트만 보이도록
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }
        wrap.addView(
            editor,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val wrapLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        wrapLp.setMargins(dp(16), 0, dp(16), dp(10))
        column.addView(wrap, wrapLp)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(2), dp(22), dp(14))
        }
        val hint = TextView(this).apply {
            text = "⇧ 키를 두 번 빠르게 누르면\n오류를 한 번에 교정해요"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Palette.GRAY)
        }
        bar.addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val shiftKey = ShiftKeyView(this).apply { onDoubleTap = { runCorrectAll() } }
        bar.addView(shiftKey)
        column.addView(bar)

        root.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        card = CorrectionCard(this).apply {
            onApply = {
                val issue = cardIssue
                if (issue != null && editor.applyIssue(issue)) haptic(editor)
            }
        }
        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
        editor.requestFocus()
    }

    // ------------------------------------------------------------------ SpellEditText.Listener

    override fun onStateChanged() {
        updateStatus()
        updateCard()
    }

    override fun onShiftDoubleTap() = runCorrectAll()

    // ------------------------------------------------------------------ 동작

    private fun runCorrectAll() {
        messageUntil = SystemClock.uptimeMillis() + CorrectionCard.MESSAGE_MS + 60
        val n = editor.applyAll()
        haptic(editor)

        card.bindMessage(if (n > 0) n.toString() + "곳을 교정했어요" else "교정할 내용이 없어요")
        card.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val wrapPos = offsetInRoot(wrap)
        card.x = ((root.width - card.measuredWidth) / 2).toFloat()
        card.y = (wrapPos[1] + dp(14)).toFloat()
        card.showAnimated()

        card.postDelayed({ updateCard() }, CorrectionCard.MESSAGE_MS + 90)
    }

    private fun updateStatus() {
        val empty = editor.text.isNullOrEmpty()
        val n = editor.visibleIssues.size
        status.text = when {
            empty -> "입력을 시작해 보세요"
            n == 0 -> "맞춤법 오류 없음  ✓"
            else -> "맞춤법 오류 " + n + "건  ·  카드를 눌러 교정"
        }
        status.setTextColor(if (n > 0) Palette.BLUE_DARK else Palette.GRAY)
    }

    private fun updateCard() {
        if (SystemClock.uptimeMillis() < messageUntil) return

        val issues = editor.visibleIssues
        val layout = editor.layout
        if (issues.isEmpty() || layout == null) {
            cardIssue = null
            card.hideAnimated()
            return
        }

        // 커서와 가장 가까운 오류를 보여줍니다.
        val cursor = editor.selectionStart.coerceAtLeast(0)
        val issue = issues.minByOrNull {
            when {
                cursor < it.start -> it.start - cursor
                cursor > it.end -> cursor - it.end
                else -> 0
            }
        } ?: return
        if (issue.start > layout.text.length) return

        val line = layout.getLineForOffset(issue.start)
        val lineTop = layout.getLineTop(line) + editor.extendedPaddingTop - editor.scrollY
        val lineBottom = layout.getLineBottom(line) + editor.extendedPaddingTop - editor.scrollY
        if (lineBottom < 0 || lineTop > editor.height) {
            // 스크롤로 화면 밖으로 나간 오류
            cardIssue = null
            card.hideAnimated()
            return
        }

        cardIssue = issue
        card.bindIssue(issue, issues.size - 1)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val w = card.measuredWidth
        val h = card.measuredHeight

        val ed = offsetInRoot(editor)
        val textX = layout.getPrimaryHorizontal(issue.start) + editor.compoundPaddingLeft - editor.scrollX
        val margin = dp(12)

        val x = (ed[0] + textX - dp(14)).toInt().coerceIn(margin, maxOf(margin, root.width - w - margin))
        var y = ed[1] + lineBottom + dp(10)
        if (y + h > root.height - dp(8)) {
            y = ed[1] + lineTop - h - dp(10)
        }
        y = y.coerceAtLeast(dp(8))

        card.x = x.toFloat()
        card.y = y.toFloat()
        card.showAnimated()
    }

    private fun offsetInRoot(v: View): IntArray {
        val a = IntArray(2)
        val b = IntArray(2)
        v.getLocationInWindow(a)
        root.getLocationInWindow(b)
        return intArrayOf(a[0] - b[0], a[1] - b[1])
    }

    private fun haptic(v: View) {
        val type = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        v.performHapticFeedback(type)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density
}
