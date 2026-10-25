package com.spellfix.korean

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/** 오류 위에 떠서 교정 내용을 보여주는 반투명 파란색 카드. 탭하면 해당 오류를 교정합니다. */
class CorrectionCard(context: Context) : LinearLayout(context) {

    var onApply: (() -> Unit)? = null

    private val reasonTv = TextView(context)
    private val diffTv = TextView(context)
    private val applyTv = TextView(context)
    private val hideRunnable = Runnable { hideAnimated() }

    private var shownState = false
    private var messageMode = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(12), context.dp(12))
        visibility = GONE
        alpha = 0f
        elevation = context.dpf(10f)

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpf(18f)
            setColor(0xEBEAF3FF.toInt())            // 반투명 연한 파랑
            setStroke(context.dp(1), 0x40007AFF)    // 파란 헤어라인
        }

        val col = LinearLayout(context).apply { orientation = VERTICAL }

        reasonTv.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0xFF5F6F8F.toInt())
        }
        diffTv.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(0, context.dp(3), 0, 0)
        }
        col.addView(reasonTv)
        col.addView(diffTv)
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        applyTv.apply {
            text = "교정"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(context.dp(14), context.dp(8), context.dp(14), context.dp(8))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dpf(20f)
                setColor(Palette.BLUE)
            }
        }
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        lp.leftMargin = context.dp(14)
        addView(applyTv, lp)

        setOnClickListener { if (!messageMode) onApply?.invoke() }
        // 카드를 눌러도 입력창의 포커스(키보드)가 사라지지 않도록
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = minOf(
            context.dp(340),
            context.resources.displayMetrics.widthPixels - context.dp(32)
        )
        val w = minOf(MeasureSpec.getSize(widthMeasureSpec), maxW)
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.AT_MOST), heightMeasureSpec)
    }

    fun bindIssue(issue: SpellIssue, others: Int) {
        removeCallbacks(hideRunnable)
        messageMode = false
        reasonTv.visibility = VISIBLE
        applyTv.visibility = VISIBLE
        reasonTv.text = if (others > 0) issue.reason + "  ·  외 " + others + "건" else issue.reason

        val gray = 0xFF8E8E93.toInt()
        val sb = SpannableStringBuilder()
        sb.append(issue.original)
        sb.setSpan(StrikethroughSpan(), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(gray), 0, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val arrowStart = sb.length
        sb.append("  →  ")
        sb.setSpan(ForegroundColorSpan(gray), arrowStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val sugStart = sb.length
        sb.append(issue.suggestion)
        sb.setSpan(StyleSpan(Typeface.BOLD), sugStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(ForegroundColorSpan(Palette.BLUE_DARK), sugStart, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        diffTv.text = sb
    }

    /** "3곳을 교정했어요" 같은 잠깐 떴다 사라지는 알림. */
    fun bindMessage(message: String) {
        removeCallbacks(hideRunnable)
        messageMode = true
        reasonTv.visibility = GONE
        applyTv.visibility = GONE
        diffTv.setPadding(0, 0, 0, 0)
        diffTv.setTextColor(Palette.BLUE_DARK)
        diffTv.text = "✓  $message"
        postDelayed(hideRunnable, MESSAGE_MS)
    }

    fun showAnimated() {
        if (shownState) return
        shownState = true
        visibility = VISIBLE
        alpha = 0f
        scaleX = 0.94f
        scaleY = 0.94f
        animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hideAnimated() {
        if (!shownState) return
        shownState = false
        animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
            .setDuration(120)
            .withEndAction { if (!shownState) visibility = GONE }
            .start()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        const val MESSAGE_MS = 1500L
    }
}
