package com.ssafy.s309.notification

/**
 * TTS 발화 전 텍스트를 자연스럽게 다듬는다. AI agent 메시지에 섞이는 마크다운 기호
 * (*, _, ~, `, #, |, [], {}), 불릿/화살표 (•, ›, ★, → 등), 이모지/픽토그램이
 * Android TTS 엔진에서 그대로 글자 이름으로 읽혀 어색해지는 문제 회피용.
 *
 * 보존: 숫자, 한글, 영문, 일반 구두점(.,!?:;), 괄호(), 슬래시/, 퍼센트%, 도°, 비교 < >.
 */
internal fun sanitizeForTts(text: String): String {
    if (text.isBlank()) return text
    return text
        .replace(MARKDOWN_NOISE, " ")
        .replace(BULLET_NOISE, " ")
        .replace(EMOJI_NOISE, "")
        .replace(MULTI_SPACE, " ")
        .trim()
}

// 마크다운 강조/구조 기호 — TTS 가 "별표/언더바/해시" 로 읽지 않도록 제거
private val MARKDOWN_NOISE = Regex("[*_~`#|\\[\\]{}]")

// 자주 등장하는 불릿/구분 기호. 이모지 블록 밖이라 별도로 처리.
private val BULLET_NOISE = Regex("[•‧·›»◆◇★☆■□●○→←↑↓⇒⇐↔]")

// 이모지/픽토그램/딩벳/기술 기호 — Unicode 블록 단위 제거.
// 1F000-1FFFF emoticons & pictographs, 2600-26FF misc symbols, 2700-27BF dingbats,
// 2300-23FF misc technical, FE0F variation selector, 200D zero width joiner.
private val EMOJI_NOISE =
    Regex(
        "[\\x{1F000}-\\x{1FFFF}]" +
            "|[\\x{2600}-\\x{26FF}]" +
            "|[\\x{2700}-\\x{27BF}]" +
            "|[\\x{2300}-\\x{23FF}]" +
            "|\\x{FE0F}" +
            "|\\x{200D}",
    )

private val MULTI_SPACE = Regex("\\s+")
