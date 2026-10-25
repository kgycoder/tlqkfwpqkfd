import com.spellfix.korean.SpellEngine
import java.io.File

fun fix(engine: SpellEngine, text: String): String {
    val issues = engine.check(text)
    val sb = StringBuilder(text)
    for (i in issues.sortedByDescending { it.start }) sb.replace(i.start, i.end, i.suggestion)
    return sb.toString()
}

fun main(args: Array<String>) {
    val engine = SpellEngine.load(File(args[0]).inputStream())
    println("rules loaded: ${engine.ruleCount}")
    var failed = 0

    val positives = listOf(
        "내일이 몇일인지 몰라" to "내일이 며칠인지 몰라",
        "오늘 몇 월 몇 일이야" to "오늘 몇 월 며칠이야",
        "숙제 다 했는데 됬어요?" to "숙제 다 했는데 됐어요?",
        "그렇게 하면 안되요" to "그렇게 하면 안 돼요",
        "그건 안돼!" to "그건 안 돼!",
        "그러면 안되서 다시 했어" to "그러면 안 돼서 다시 했어",
        "웬지 기분이 좋와" to "왠지 기분이 좋아",
        "내일 갈께요" to "내일 갈게요",
        "나도 할수있어" to "나도 할 수 있어",
        "그럴수없다" to "그럴 수 없다",
        "금새 끝날꺼야" to "금세 끝날 거야",
        "내일 할껀데 괜찮아?" to "내일 할 건데 괜찮아?",
        "그냥 할껄 그랬어" to "그냥 할걸 그랬어",
        "설레임이 가득, 깨끗히 치웠다, 일일히 확인" to "설렘이 가득, 깨끗이 치웠다, 일일이 확인",
        "열심이 해야되" to "열심히 해야 되",
        "하지않았다" to "하지 않았다",
        "그렇지 안았다" to "그렇지 않았다",
        "내꺼야 니꺼야" to "내 거야 네 거야",
        "괜찬아 괜찮어" to "괜찮아 괜찮아",
        "머리속에 있슴니다" to "머릿속에 있습니다",
        "어제 잠을 못잤써" to "어제 잠을 못잤어",
        "시험을 치뤘다" to "시험을 치렀다",
        "김치를 담궜어" to "김치를 담갔어",
        "문을 잠궈줘" to "문을 잠가줘",
        "사과를 깍아줘" to "사과를 깎아줘",
        "찌게가 맛 있는데" to "찌개가 맛있는데",
        "그럴 수 밖에 없어" to "그럴 수밖에 없어",
        "언제 오랫만에 볼까" to "언제 오랜만에 볼까",
        "어떻해 어떡게 하지" to "어떡해 어떻게 하지",
        "안절부절하다" to "안절부절못하다",
        "제작년에 갔어" to "재작년에 갔어",
        "나는 학생이였다" to "나는 학생이었다",
        "할려고 했는데" to "하려고 했는데",
        "먹을려고" to "먹으려고",
    )

    val negatives = listOf(
        "깍두기를 먹었다",
        "오랫동안 기다렸다",
        "머릿속이 복잡해요",
        "알맞은 크기예요",
        "이렇게 하면 돼요",
        "안 돼요, 하지 마세요",
        "할 수 있어요",
        "깨끗이 씻어요",
        "열심히 합니다",
        "며칠 뒤에 봐요",
        "왠지 슬퍼요",
        "웬만하면 가요",
        "되어요 그리고 되었어요",
        "안녕하세요 반갑습니다",
        "껄껄 웃었다",
        "선생님께 말씀드릴게요",
        "어머니께서 오셨다",
        "제작년도를 확인하세요",
        "별이 수없이 많다",
        "그렇게 하지 않았다",
        "맛있는 김치찌개",
        "우리 집 강아지가 짖는다",
        "내일은 일찍 일어날 거야",
        "할 수 없이 포기했다",
        "깎아주세요",
        "깍다귀가 있다",
        "수업이 끝났다",
        "잘 지내고 있어요?",
        "오늘은 날씨가 좋아서 산책을 했다",
    )

    for ((input, expected) in positives) {
        val actual = fix(engine, input)
        if (actual != expected) {
            failed++
            println("FAIL(pos): '$input' -> '$actual' (expected '$expected')")
        }
    }
    for (input in negatives) {
        val issues = engine.check(input)
        if (issues.isNotEmpty()) {
            failed++
            println("FAIL(neg): '$input' flagged: " + issues.joinToString { "${it.original}->${it.suggestion}" })
        }
    }

    // 성능: 긴 글(약 20만 자)에서도 빠르게 끝나는지
    val big = (1..4000).joinToString(" ") { "오늘은 날씨가 좋아서 산책을 했다. 몇일 전에 갈께요" }
    val t0 = System.nanoTime()
    val n = engine.check(big).size
    val ms = (System.nanoTime() - t0) / 1_000_000
    println("big text: ${big.length} chars, $n issues, $ms ms")

    println(if (failed == 0) "ALL OK (${positives.size} positive, ${negatives.size} negative)" else "$failed FAILED")
}
