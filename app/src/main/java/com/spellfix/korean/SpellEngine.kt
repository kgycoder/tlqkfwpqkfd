package com.spellfix.korean

import java.io.InputStream
import java.util.regex.Matcher
import java.util.regex.Pattern

/** 맞춤법 오류 한 건. [start, end) 는 입력 텍스트 안의 위치입니다. */
class SpellIssue(
    val start: Int,
    val end: Int,
    val original: String,
    val suggestion: String,
    val reason: String
)

/**
 * 외부 API 없이 기기 안에서만 동작하는 규칙 기반 맞춤법 검사기.
 * 규칙은 assets/rules.txt (정규식 => 교정 => 이유) 에서 읽고,
 * 받침 조건이 필요한 몇 가지(-ㄹ게, -ㄹ 거, -ㄹ 수 있다)는 코드로 처리합니다.
 */
class SpellEngine(private val rules: List<Rule>) {

    class Rule(val pattern: Pattern, val replacement: String, val reason: String)

    val ruleCount: Int get() = rules.size

    fun check(text: String): List<SpellIssue> {
        if (text.isEmpty()) return emptyList()
        val found = ArrayList<SpellIssue>()

        for (rule in rules) {
            val m = rule.pattern.matcher(text)
            while (m.find()) {
                if (m.end() <= m.start()) continue
                val original = m.group()
                val suggestion = expand(rule.replacement, m)
                if (suggestion == original) continue
                found.add(SpellIssue(m.start(), m.end(), original, suggestion, rule.reason))
            }
        }
        findRieulIssues(text, found)

        // 겹치는 결과는 먼저 시작하고 더 긴 것만 남깁니다.
        found.sortWith(Comparator { a, b ->
            if (a.start != b.start) a.start - b.start
            else (b.end - b.start) - (a.end - a.start)
        })
        val result = ArrayList<SpellIssue>()
        var lastEnd = -1
        for (issue in found) {
            if (issue.start >= lastEnd) {
                result.add(issue)
                lastEnd = issue.end
            }
        }
        return result
    }

    /** 앞 음절의 받침이 ㄹ 인지에 따라 달라지는 규칙들. */
    private fun findRieulIssues(text: String, out: MutableList<SpellIssue>) {
        // 할께 -> 할게
        var m = P_GE.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (!endsWithRieul(prev[0])) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), m.group(), prev + "게",
                    "'-ㄹ게'는 '게'로 적어요 (-ㄹ께는 틀린 표기)"
                )
            )
        }

        // 할꺼야 -> 할 거야
        m = P_GEO.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (!endsWithRieul(prev[0])) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), m.group(), prev + " 거" + g(m, 2),
                    "'-ㄹ 거'로 적고 '거'는 띄어 써요 ('꺼'는 틀린 표기)"
                )
            )
        }

        // 할껀데 -> 할 건데
        m = P_GEON.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (!endsWithRieul(prev[0])) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), m.group(), prev + " 건" + g(m, 2),
                    "'-ㄹ 건데'로 적고 '건'은 띄어 써요 ('껀'은 틀린 표기)"
                )
            )
        }

        // 할껄 -> 할걸 ('껄껄' 같은 웃음소리는 제외)
        m = P_GEOL.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (prev[0] == '껄' || !endsWithRieul(prev[0])) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), m.group(), prev + "걸",
                    "'-ㄹ걸'은 '걸'로 적어요 (-ㄹ껄은 틀린 표기)"
                )
            )
        }

        // 했써 -> 했어 (과거형 받침 'ㅆ' 뒤의 '써')
        m = P_SSEO.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (!endsWithSsangSiot(prev[0])) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), m.group(), prev + "어",
                    "'-었어'처럼 '어'로 적어요 ('써'는 틀린 표기)"
                )
            )
        }

        // 할수있어 -> 할 수 있어
        m = P_SU.matcher(text)
        while (m.find()) {
            val prev = g(m, 1)
            if (!endsWithRieul(prev[0])) continue
            val original = m.group()
            val suggestion = prev + " 수 " + g(m, 2)
            if (suggestion == original) continue
            out.add(
                SpellIssue(
                    m.start(), m.end(), original, suggestion,
                    "'수'는 의존 명사라 앞뒤를 띄어 써요"
                )
            )
        }
    }

    private fun g(m: Matcher, index: Int): String = m.group(index) ?: ""

    private fun expand(template: String, m: Matcher): String {
        if (template.indexOf('$') < 0) return template
        val sb = StringBuilder()
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '$' && i + 1 < template.length && template[i + 1].isDigit()) {
                val group = template[i + 1] - '0'
                if (group <= m.groupCount()) sb.append(m.group(group) ?: "")
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    companion object {
        private const val END = "(?:(?=요)|(?![가-힣]))"

        private val P_GE: Pattern = Pattern.compile("([가-힣])께" + END)
        private val P_GEO: Pattern =
            Pattern.compile("([가-힣]) ?꺼(야지|야|예요|에요|임|니까|라고|라|다|지|래|니|네)" + END)
        private val P_GEON: Pattern = Pattern.compile("([가-힣]) ?껀(데|가|지)" + END)
        private val P_GEOL: Pattern = Pattern.compile("([가-힣])껄" + END)
        private val P_SSEO: Pattern = Pattern.compile("([가-힣])써" + END)
        private val P_SU: Pattern = Pattern.compile("([가-힣]) ?수 ?(있|없)")

        /** 한글 음절의 종성이 ㄹ 인지 검사합니다. */
        fun endsWithRieul(c: Char): Boolean {
            if (c < '가' || c > '힣') return false
            return (c - '가') % 28 == 8
        }

        /** 한글 음절의 종성이 ㅆ 인지 검사합니다 (했, 갔, 있, 없 ...). */
        fun endsWithSsangSiot(c: Char): Boolean {
            if (c < '가' || c > '힣') return false
            return (c - '가') % 28 == 20
        }

        /** rules.txt 형식: `정규식 => 교정 => 이유` (# 로 시작하면 주석). */
        fun load(input: InputStream): SpellEngine {
            val lines = input.bufferedReader(Charsets.UTF_8).use { it.readLines() }
            val rules = ArrayList<Rule>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(" => ")
                if (parts.size < 3) continue
                try {
                    val pattern = Pattern.compile(parts[0].trim())
                    rules.add(Rule(pattern, parts[1].trim(), parts[2].trim()))
                } catch (e: Exception) {
                    // 잘못된 규칙 한 줄 때문에 앱 전체가 멈추지 않도록 건너뜁니다.
                }
            }
            return SpellEngine(rules)
        }
    }
}
