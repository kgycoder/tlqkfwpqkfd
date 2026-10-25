package com.spellfix.korean

import android.content.Context

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** 앱 전체에서 쓰는 색상 (iOS 시스템 블루 계열). */
object Palette {
    const val BLUE = 0xFF007AFF.toInt()
    const val BLUE_DARK = 0xFF0A52CC.toInt()
    const val TEXT = 0xFF1C1C1E.toInt()
    const val GRAY = 0xFF8E8E93.toInt()
    const val BG = 0xFFF2F2F7.toInt()
}
