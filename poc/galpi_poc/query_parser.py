"""규칙 기반 한국어 쿼리 파서 (PoC 축소판).

"작년 일본에서 먹은 돈까스"
 -> 시간 범위(작년) + 장소 필터(일본 bbox) + 시각 쿼리("먹은 돈까스")

앱(Phase 3)에서는 Kotlin으로 확장 구현하며, 장소는 bbox 대신
오프라인 역지오코딩 결과(국가/도시명)와 매칭한다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from datetime import date


@dataclass
class ParsedQuery:
    visual_text: str
    date_range: tuple[date, date] | None = None
    place_bbox: tuple[float, float, float, float] | None = None  # (lat1, lat2, lng1, lng2)
    matched_terms: list[str] = field(default_factory=list)


# PoC용 최소 장소 사전. 앱에서는 GeoNames 기반으로 대체.
PLACE_BBOXES: dict[str, tuple[float, float, float, float]] = {
    "일본": (24.0, 45.6, 122.9, 146.1),
    "한국": (33.0, 38.7, 124.5, 131.9),
    "서울": (37.4, 37.7, 126.7, 127.2),
    "부산": (35.0, 35.4, 128.9, 129.3),
    "제주": (33.1, 33.6, 126.1, 127.0),
    "제주도": (33.1, 33.6, 126.1, 127.0),
}

_SEASONS = {"봄": (3, 5), "여름": (6, 8), "가을": (9, 11), "겨울": (12, 2)}


def _year_range(year: int) -> tuple[date, date]:
    return date(year, 1, 1), date(year, 12, 31)


def parse(query: str, today: date | None = None) -> ParsedQuery:
    today = today or date.today()
    remaining = query
    matched: list[str] = []
    date_range = None
    bbox = None

    # 연도 표현
    rel_years = {"올해": 0, "작년": 1, "재작년": 2}
    for term, delta in rel_years.items():
        if term in remaining:
            date_range = _year_range(today.year - delta)
            remaining = remaining.replace(term, " ")
            matched.append(term)
            break
    m = re.search(r"(20\d{2})년", remaining)
    if m:
        date_range = _year_range(int(m.group(1)))
        remaining = remaining.replace(m.group(0), " ")
        matched.append(m.group(0))

    # 월/계절 표현 — 연도 범위가 있으면 그 안으로 좁힌다
    m = re.search(r"(1[0-2]|[1-9])월", remaining)
    if m:
        month = int(m.group(1))
        year = date_range[0].year if date_range else today.year
        last_day = (date(year + (month == 12), month % 12 + 1, 1)).toordinal() - 1
        date_range = (date(year, month, 1), date.fromordinal(last_day))
        remaining = remaining.replace(m.group(0), " ")
        matched.append(m.group(0))
    else:
        for season, (m1, m2) in _SEASONS.items():
            if season in remaining:
                year = date_range[0].year if date_range else today.year
                if season == "겨울":  # 12월~이듬해 2월
                    date_range = (date(year, 12, 1), date(year + 1, 2, 28))
                else:
                    last = date(year, m2 + 1, 1).toordinal() - 1
                    date_range = (date(year, m1, 1), date.fromordinal(last))
                remaining = remaining.replace(season, " ")
                matched.append(season)
                break

    # 장소 표현 (긴 이름 우선 매칭)
    for name in sorted(PLACE_BBOXES, key=len, reverse=True):
        if name in remaining:
            bbox = PLACE_BBOXES[name]
            remaining = re.sub(rf"{name}(에서|의|에)?", " ", remaining)
            matched.append(name)
            break

    visual = re.sub(r"\s+", " ", remaining).strip()
    return ParsedQuery(
        visual_text=visual or query,
        date_range=date_range,
        place_bbox=bbox,
        matched_terms=matched,
    )
