package com.galpi.app.search

/**
 * multilingual CLIP에서 정렬이 약한 한국어 시각 단어의 영어 보조 사전.
 * 검색어에 이 단어들이 있으면 영어 치환본을 함께 인코딩해 평균낸다 (앙상블).
 * 정렬이 이미 좋은 단어(고양이, 바다 등)는 넣을 필요 없다.
 */
object QueryGloss {
    val GLOSS: Map<String, String> = mapOf(
        // 풍경/자연
        "노을" to "sunset",
        "일몰" to "sunset",
        "일출" to "sunrise",
        "밤하늘" to "night sky",
        "은하수" to "milky way",
        "단풍" to "autumn foliage",
        "벚꽃" to "cherry blossom",
        "불꽃놀이" to "fireworks",
        "폭포" to "waterfall",
        "계곡" to "valley stream",
        "바닷가" to "beach",
        "해변" to "beach",
        "야경" to "city night view",
        "눈사람" to "snowman",
        "첫눈" to "snow",
        // 한국 음식
        "삼겹살" to "grilled pork belly korean bbq",
        "고기굽기" to "korean bbq grill",
        "돈까스" to "fried pork cutlet",
        "국밥" to "korean hot soup with rice",
        "김치찌개" to "kimchi stew",
        "된장찌개" to "korean soybean stew",
        "떡볶이" to "tteokbokki spicy rice cakes",
        "치킨" to "fried chicken",
        "김밥" to "gimbap korean seaweed rice roll",
        "냉면" to "korean cold noodles",
        "칼국수" to "korean noodle soup",
        "라면" to "ramen noodles",
        "짜장면" to "black bean noodles",
        "짬뽕" to "spicy seafood noodle soup",
        "탕수육" to "sweet and sour pork",
        "갈비" to "grilled ribs",
        "족발" to "braised pork feet",
        "보쌈" to "boiled pork wraps",
        "파전" to "korean savory pancake",
        "전골" to "korean hot pot",
        "곱창" to "grilled intestines",
        "찜닭" to "braised chicken",
        "회" to "sashimi raw fish",
        "초밥" to "sushi",
        "마라탕" to "malatang spicy soup",
        "만두" to "dumplings",
        "순대" to "korean blood sausage",
        "붕어빵" to "fish shaped pastry",
        "빙수" to "shaved ice dessert",
        // 사물/기타
        "영수증" to "receipt paper",
        "명함" to "business card",
        "화이트보드" to "whiteboard",
        "칠판" to "blackboard",
        "필기" to "handwritten notes",
        "자격증" to "certificate document",
        "택배" to "delivery package box",
        "셀카" to "selfie",
        "단체사진" to "group photo",
    )

    /** 검색어 안의 사전 단어를 영어로 치환한 문자열. 치환이 없으면 null. */
    fun englishVariant(query: String): String? {
        var replaced = query
        for ((ko, en) in GLOSS) {
            if (replaced.contains(ko)) replaced = replaced.replace(ko, " $en ")
        }
        val cleaned = replaced.replace(Regex("\\s+"), " ").trim()
        return if (cleaned == query.trim()) null else cleaned
    }
}
