package com.galpi.app.search

import java.util.Calendar

/**
 * 규칙 기반 한국어 쿼리 파서.
 * "작년 일본에서 먹은 돈까스" -> 기간(작년) + 장소(일본 bbox) + 시각 쿼리("먹은 돈까스")
 */
data class ParsedQuery(
    val visualText: String,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val bbox: DoubleArray? = null, // [latMin, latMax, lngMin, lngMax]
    val matchedTerms: List<String> = emptyList(),
)

object QueryParser {

    // 자주 쓰는 국가/도시 bbox. 추후 GeoNames 데이터로 확장.
    private val PLACES: Map<String, DoubleArray> = mapOf(
        "일본" to doubleArrayOf(24.0, 45.6, 122.9, 146.1),
        "도쿄" to doubleArrayOf(35.4, 35.9, 139.3, 140.0),
        "오사카" to doubleArrayOf(34.4, 34.9, 135.3, 135.7),
        "교토" to doubleArrayOf(34.9, 35.2, 135.6, 135.9),
        "후쿠오카" to doubleArrayOf(33.4, 33.8, 130.2, 130.6),
        "오키나와" to doubleArrayOf(26.0, 26.9, 127.6, 128.3),
        "한국" to doubleArrayOf(33.0, 38.7, 124.5, 131.9),
        "서울" to doubleArrayOf(37.4, 37.7, 126.7, 127.2),
        "부산" to doubleArrayOf(35.0, 35.4, 128.9, 129.3),
        "제주" to doubleArrayOf(33.1, 33.6, 126.1, 127.0),
        "제주도" to doubleArrayOf(33.1, 33.6, 126.1, 127.0),
        "강릉" to doubleArrayOf(37.6, 37.9, 128.7, 129.1),
        "경주" to doubleArrayOf(35.7, 36.0, 129.1, 129.4),
        "전주" to doubleArrayOf(35.7, 35.9, 127.0, 127.3),
        "여수" to doubleArrayOf(34.6, 34.9, 127.6, 127.8),
        "대구" to doubleArrayOf(35.7, 36.0, 128.4, 128.8),
        "대전" to doubleArrayOf(36.2, 36.5, 127.3, 127.6),
        "인천" to doubleArrayOf(37.3, 37.6, 126.4, 126.8),
        "광주" to doubleArrayOf(35.0, 35.3, 126.7, 127.0),
        "중국" to doubleArrayOf(18.0, 53.6, 73.5, 135.1),
        "대만" to doubleArrayOf(21.9, 25.4, 119.9, 122.1),
        "홍콩" to doubleArrayOf(22.1, 22.6, 113.8, 114.4),
        "태국" to doubleArrayOf(5.6, 20.5, 97.3, 105.7),
        "방콕" to doubleArrayOf(13.5, 14.0, 100.3, 100.9),
        "베트남" to doubleArrayOf(8.4, 23.4, 102.1, 109.5),
        "다낭" to doubleArrayOf(15.9, 16.2, 107.9, 108.4),
        "싱가포르" to doubleArrayOf(1.1, 1.5, 103.6, 104.1),
        "발리" to doubleArrayOf(-8.9, -8.0, 114.4, 115.7),
        "필리핀" to doubleArrayOf(4.6, 21.1, 116.9, 126.6),
        "괌" to doubleArrayOf(13.2, 13.7, 144.6, 145.0),
        "미국" to doubleArrayOf(24.5, 49.4, -125.0, -66.9),
        "하와이" to doubleArrayOf(18.9, 22.3, -160.3, -154.8),
        "유럽" to doubleArrayOf(36.0, 71.0, -10.0, 40.0),
        "프랑스" to doubleArrayOf(41.3, 51.1, -5.1, 9.6),
        "파리" to doubleArrayOf(48.7, 49.0, 2.1, 2.6),
        "영국" to doubleArrayOf(49.9, 60.9, -8.6, 1.8),
        "런던" to doubleArrayOf(51.3, 51.7, -0.5, 0.3),
        "이탈리아" to doubleArrayOf(36.6, 47.1, 6.6, 18.5),
        "스페인" to doubleArrayOf(36.0, 43.8, -9.3, 3.3),
        "호주" to doubleArrayOf(-43.6, -10.7, 113.3, 153.6),
    )

    private val SEASONS = mapOf(
        "봄" to (Calendar.MARCH to Calendar.MAY),
        "여름" to (Calendar.JUNE to Calendar.AUGUST),
        "가을" to (Calendar.SEPTEMBER to Calendar.NOVEMBER),
    )

    fun parse(query: String, now: Calendar = Calendar.getInstance()): ParsedQuery {
        var remaining = query
        val matched = mutableListOf<String>()
        var yearRange: IntRange? = null
        var startMillis: Long? = null
        var endMillis: Long? = null
        var bbox: DoubleArray? = null

        val thisYear = now.get(Calendar.YEAR)

        // 상대 연도
        for ((term, delta) in listOf("재작년" to 2, "작년" to 1, "올해" to 0)) {
            if (remaining.contains(term)) {
                yearRange = (thisYear - delta)..(thisYear - delta)
                remaining = remaining.replace(term, " ")
                matched += term
                break
            }
        }
        // 절대 연도 (2024년)
        Regex("(20\\d{2})년").find(remaining)?.let { m ->
            yearRange = m.groupValues[1].toInt()..m.groupValues[1].toInt()
            remaining = remaining.replace(m.value, " ")
            matched += m.value
        }

        // 월 (3월) — 연도 미지정이면 올해 기준
        val monthMatch = Regex("(1[0-2]|[1-9])월").find(remaining)
        if (monthMatch != null) {
            val month = monthMatch.groupValues[1].toInt() - 1
            val year = yearRange?.first ?: thisYear
            startMillis = calendarMillis(year, month, 1)
            endMillis = endOfMonth(year, month)
            remaining = remaining.replace(monthMatch.value, " ")
            matched += monthMatch.value
        } else {
            // 계절
            for ((season, months) in SEASONS) {
                if (remaining.contains(season)) {
                    val year = yearRange?.first ?: thisYear
                    startMillis = calendarMillis(year, months.first, 1)
                    endMillis = endOfMonth(year, months.second)
                    remaining = remaining.replace(season, " ")
                    matched += season
                    break
                }
            }
            if (remaining.contains("겨울")) {
                val year = yearRange?.first ?: thisYear
                startMillis = calendarMillis(year, Calendar.DECEMBER, 1)
                endMillis = endOfMonth(year + 1, Calendar.FEBRUARY)
                remaining = remaining.replace("겨울", " ")
                matched += "겨울"
            }
        }
        // 연도만 지정된 경우 (월/계절 없음)
        if (startMillis == null && yearRange != null) {
            startMillis = calendarMillis(yearRange.first, Calendar.JANUARY, 1)
            endMillis = endOfMonth(yearRange.last, Calendar.DECEMBER)
        }

        // 장소 (긴 이름 우선)
        for (name in PLACES.keys.sortedByDescending { it.length }) {
            if (remaining.contains(name)) {
                bbox = PLACES.getValue(name)
                remaining = remaining.replace(Regex("$name(에서|에|의)?"), " ")
                matched += name
                break
            }
        }

        val visual = remaining.replace(Regex("\\s+"), " ").trim()
        return ParsedQuery(
            visualText = visual.ifEmpty { query },
            startMillis = startMillis,
            endMillis = endMillis,
            bbox = bbox,
            matchedTerms = matched,
        )
    }

    private fun calendarMillis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }.timeInMillis

    private fun endOfMonth(year: Int, month: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }.timeInMillis
}
