"""INT8 ONNX 모델이 원본과 같은 검색 결과를 내는지 검증한다.

인덱싱된 폴더의 사진들을 ONNX 이미지 인코더로 다시 임베딩하고,
ONNX 텍스트 인코더로 쿼리를 임베딩해 top-k를 원본(PyTorch) 결과와 비교.

사용법:
    python verify_onnx.py <사진_폴더> [--models models]
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image
from transformers import AutoTokenizer, CLIPImageProcessor

from galpi_poc.models import text_model
from galpi_poc.store import IndexStore
from index import find_images

QUERIES = ["산", "바다", "꽃", "밤하늘", "사막", "숲과 나무", "파란색 추상 그림", "도시 야경"]
TOP_K = 3


def normalize(x: np.ndarray) -> np.ndarray:
    return x / np.linalg.norm(x, axis=-1, keepdims=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("folder", type=Path)
    ap.add_argument("--models", type=Path, default=Path(__file__).parent / "models")
    args = ap.parse_args()

    img_sess = ort.InferenceSession(str(args.models / "image_encoder.int8.onnx"))
    txt_sess = ort.InferenceSession(str(args.models / "text_encoder.int8.onnx"))
    processor = CLIPImageProcessor.from_pretrained("openai/clip-vit-base-patch32")
    tokenizer = AutoTokenizer.from_pretrained(args.models / "tokenizer")

    # ONNX로 전체 이미지 재임베딩
    paths = find_images(args.folder)
    embs = []
    for p in paths:
        with Image.open(p) as im:
            px = processor(images=im.convert("RGB"), return_tensors="np")["pixel_values"]
        embs.append(img_sess.run(None, {"pixel_values": px})[0][0])
    onnx_embs = normalize(np.array(embs))

    # 원본(PyTorch) 인덱스 로드
    ref_embs, metas = IndexStore(args.folder).load()
    ref_names = [Path(m.path).name for m in metas]
    st_txt = text_model()

    agree = total = 0
    for q in QUERIES:
        enc = tokenizer(q, return_tensors="np")
        qv = txt_sess.run(
            None,
            {"input_ids": enc["input_ids"], "attention_mask": enc["attention_mask"]},
        )[0][0]
        qv = qv / np.linalg.norm(qv)
        onnx_top = np.argsort(-(onnx_embs @ qv))[:TOP_K]
        onnx_names = [paths[i].name for i in onnx_top]

        ref_qv = st_txt.encode(q, normalize_embeddings=True)
        ref_top = np.argsort(-(ref_embs @ ref_qv))[:TOP_K]
        ref_top_names = [ref_names[i] for i in ref_top]

        overlap = len(set(onnx_names) & set(ref_top_names))
        top1_same = onnx_names[0] == ref_top_names[0]
        agree += top1_same
        total += 1
        print(
            f"[{q}] top1 {'일치' if top1_same else '불일치'}, "
            f"top{TOP_K} 겹침 {overlap}/{TOP_K}"
            + ("" if top1_same else f"  (onnx: {onnx_names[0]} / ref: {ref_top_names[0]})")
        )

    print(f"\ntop-1 일치율: {agree}/{total}")


if __name__ == "__main__":
    main()
