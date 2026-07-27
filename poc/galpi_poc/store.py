"""PoC용 인덱스 저장소: 임베딩(npy) + 메타데이터(jsonl).

앱에서는 Room(SQLite)이 이 역할을 한다.
"""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path

import numpy as np

from .exif_meta import PhotoMeta

INDEX_DIR_NAME = ".galpi_index"


class IndexStore:
    def __init__(self, root: Path):
        self.dir = root / INDEX_DIR_NAME
        self.emb_path = self.dir / "embeddings.npy"
        self.meta_path = self.dir / "meta.jsonl"

    def exists(self) -> bool:
        return self.emb_path.exists() and self.meta_path.exists()

    def save(self, embeddings: np.ndarray, metas: list[PhotoMeta]) -> None:
        assert len(embeddings) == len(metas)
        self.dir.mkdir(parents=True, exist_ok=True)
        np.save(self.emb_path, embeddings.astype(np.float32))
        with open(self.meta_path, "w", encoding="utf-8") as f:
            for m in metas:
                f.write(json.dumps(asdict(m), ensure_ascii=False) + "\n")

    def load(self) -> tuple[np.ndarray, list[PhotoMeta]]:
        embeddings = np.load(self.emb_path)
        metas = []
        with open(self.meta_path, encoding="utf-8") as f:
            for line in f:
                metas.append(PhotoMeta(**json.loads(line)))
        return embeddings, metas
