"""사진 폴더를 스캔해 임베딩 인덱스를 만든다.

사용법:
    python index.py <사진_폴더> [--batch 32]

결과는 <사진_폴더>/.galpi_index/ 에 저장된다.
"""

from __future__ import annotations

import argparse
import time
from pathlib import Path

import numpy as np
from PIL import Image

from galpi_poc.exif_meta import extract_meta
from galpi_poc.models import image_model
from galpi_poc.store import IndexStore

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def find_images(root: Path) -> list[Path]:
    return sorted(
        p
        for p in root.rglob("*")
        if p.suffix.lower() in IMAGE_EXTS and IndexStore(root).dir not in p.parents
    )


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", type=Path)
    ap.add_argument("--batch", type=int, default=32)
    args = ap.parse_args()

    paths = find_images(args.folder)
    if not paths:
        print(f"이미지 없음: {args.folder}")
        return
    print(f"{len(paths)}장 인덱싱 시작")

    model = image_model()
    embeddings: list[np.ndarray] = []
    metas = []
    t0 = time.perf_counter()

    for i in range(0, len(paths), args.batch):
        batch_paths = paths[i : i + args.batch]
        images, ok_paths = [], []
        for p in batch_paths:
            try:
                im = Image.open(p)
                im.load()
                images.append(im.convert("RGB"))
                ok_paths.append(p)
            except Exception as e:
                print(f"  건너뜀 (열기 실패): {p} ({e})")
        if not images:
            continue
        embs = model.encode(
            images, batch_size=len(images), normalize_embeddings=True
        )
        for im in images:
            im.close()
        embeddings.append(embs)
        metas.extend(extract_meta(p) for p in ok_paths)
        done = min(i + args.batch, len(paths))
        print(f"  {done}/{len(paths)}")

    all_embs = np.vstack(embeddings)
    IndexStore(args.folder).save(all_embs, metas)
    dt = time.perf_counter() - t0
    print(
        f"완료: {len(metas)}장, {dt:.1f}s (장당 {dt / len(metas) * 1000:.0f}ms), "
        f"인덱스 크기 {all_embs.nbytes / 1024:.0f}KB"
    )


if __name__ == "__main__":
    main()
