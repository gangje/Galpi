"""인덱싱된 폴더에서 한국어 자연어로 사진을 검색한다.

사용법:
    python search.py <사진_폴더> "작년 일본에서 먹은 돈까스" [-k 5] [--open]

--open 을 주면 1위 결과를 기본 이미지 뷰어로 연다.
"""

from __future__ import annotations

import argparse
import os
from datetime import date
from pathlib import Path

import numpy as np

from galpi_poc.models import text_model
from galpi_poc.query_parser import parse
from galpi_poc.store import IndexStore

SIMILARITY_FLOOR = 0.2  # 이보다 낮으면 "관련 사진 없음"으로 간주


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", type=Path)
    ap.add_argument("query")
    ap.add_argument("-k", type=int, default=5)
    ap.add_argument("--open", action="store_true")
    args = ap.parse_args()

    store = IndexStore(args.folder)
    if not store.exists():
        print(f"인덱스가 없습니다. 먼저 실행: python index.py {args.folder}")
        return
    embeddings, metas = store.load()

    parsed = parse(args.query)
    print(f"쿼리 분해: 시각='{parsed.visual_text}'", end="")
    if parsed.date_range:
        print(f", 기간={parsed.date_range[0]}~{parsed.date_range[1]}", end="")
    if parsed.place_bbox:
        print(f", 장소필터=on({parsed.matched_terms[-1]})", end="")
    print()

    # 1) 메타데이터 필터로 후보 축소
    mask = np.ones(len(metas), dtype=bool)
    for i, m in enumerate(metas):
        if parsed.date_range:
            if not m.taken_at:
                mask[i] = False
                continue
            d = date.fromisoformat(m.taken_at[:10])
            if not (parsed.date_range[0] <= d <= parsed.date_range[1]):
                mask[i] = False
                continue
        if parsed.place_bbox:
            lat1, lat2, lng1, lng2 = parsed.place_bbox
            if m.lat is None or not (lat1 <= m.lat <= lat2 and lng1 <= m.lng <= lng2):
                mask[i] = False

    candidates = np.flatnonzero(mask)
    if len(candidates) == 0:
        print("필터 조건(기간/장소)에 맞는 사진이 없습니다.")
        return

    # 2) 시각 쿼리 코사인 유사도 랭킹 (임베딩은 정규화되어 있어 내적 = 코사인)
    q = text_model().encode(parsed.visual_text, normalize_embeddings=True)
    sims = embeddings[candidates] @ q
    order = np.argsort(-sims)[: args.k]

    print(f"\n후보 {len(candidates)}장 중 상위 {len(order)}개:")
    shown = 0
    for rank, oi in enumerate(order, 1):
        idx, sim = candidates[oi], sims[oi]
        marker = "" if sim >= SIMILARITY_FLOOR else "  (유사도 낮음)"
        print(f"{rank}. [{sim:.3f}] {metas[idx].path}{marker}")
        shown += 1
    if shown and args.open:
        best = candidates[order[0]]
        os.startfile(metas[best].path)  # noqa: S606 — Windows 기본 뷰어


if __name__ == "__main__":
    main()
