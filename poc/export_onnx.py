"""이미지/텍스트 인코더를 ONNX로 변환하고 INT8 동적 양자화한다.

사용법:
    python export_onnx.py [--out models]

산출물 (Android assets에 탑재할 파일):
    models/image_encoder.int8.onnx   픽셀(1,3,224,224) -> 512차원 임베딩
    models/text_encoder.int8.onnx    토큰 id/mask      -> 512차원 임베딩
    models/tokenizer/                텍스트 토크나이저 (DistilBERT sentencepiece/wordpiece)

변환 후 sentence-transformers 원본과 코사인 유사도로 일치 여부를 검증한다.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from galpi_poc.models import image_model, text_model

OPSET = 18


class ImageEncoder(torch.nn.Module):
    """CLIP ViT-B/32 vision tower + projection -> 512차원."""

    def __init__(self, st_model):
        super().__init__()
        clip = st_model[0].model  # transformers CLIPModel
        self.vision = clip.vision_model
        self.proj = clip.visual_projection

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        out = self.vision(pixel_values=pixel_values)
        return self.proj(out.pooler_output)


class TextEncoder(torch.nn.Module):
    """multilingual DistilBERT + mean pooling + Dense(768->512)."""

    def __init__(self, st_model):
        super().__init__()
        self.bert = st_model[0].auto_model
        self.dense = st_model[2].linear

    def forward(self, input_ids: torch.Tensor, attention_mask: torch.Tensor) -> torch.Tensor:
        hidden = self.bert(input_ids=input_ids, attention_mask=attention_mask)[0]
        mask = attention_mask.unsqueeze(-1).to(hidden.dtype)
        pooled = (hidden * mask).sum(1) / mask.sum(1).clamp(min=1e-9)
        return self.dense(pooled)


def export_and_quantize(module, args, input_names, dynamic_axes, out_base: Path) -> Path:
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    fp32 = out_base.with_suffix(".onnx")
    int8 = out_base.with_suffix(".int8.onnx")
    torch.onnx.export(
        module.eval(),
        args,
        str(fp32),
        input_names=input_names,
        output_names=["embedding"],
        dynamic_axes=dynamic_axes,
        opset_version=OPSET,
        dynamo=False,  # dynamo 익스포터는 양자화기와 호환 문제(이름 중복/타입 추론 실패)가 있어 구형 사용
    )
    # 익스포터가 남긴 중간 value_info가 양자화기의 shape 추론과 충돌할 수 있어 제거
    model = onnx.load(str(fp32))
    del model.graph.value_info[:]
    onnx.save(model, str(fp32))
    quantize_dynamic(
        str(fp32),
        str(int8),
        weight_type=QuantType.QUInt8,
        # dynamo 익스포터 그래프는 일부 중간 텐서의 타입 추론이 안 되므로 기본 타입 지정
        extra_options={"DefaultTensorType": onnx.TensorProto.FLOAT},
    )
    print(
        f"{out_base.name}: fp32 {fp32.stat().st_size / 1e6:.0f}MB -> "
        f"int8 {int8.stat().st_size / 1e6:.0f}MB"
    )
    return int8


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    a, b = a.ravel(), b.ravel()
    return float(a @ b / (np.linalg.norm(a) * np.linalg.norm(b)))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=Path(__file__).parent / "models")
    args = ap.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    import onnxruntime as ort

    # ---- 이미지 인코더 ----
    st_img = image_model()
    dummy_px = torch.randn(1, 3, 224, 224)
    img_onnx = export_and_quantize(
        ImageEncoder(st_img),
        (dummy_px,),
        ["pixel_values"],
        {"pixel_values": {0: "batch"}},
        args.out / "image_encoder",
    )

    # 검증: 실제 이미지 1장으로 원본 vs ONNX 비교
    test_img = Image.new("RGB", (640, 480), (180, 120, 60))
    ref = st_img.encode(test_img)
    processor = st_img[0].processor
    px = processor(images=test_img, return_tensors="np")["pixel_values"]
    sess = ort.InferenceSession(str(img_onnx))
    out = sess.run(None, {"pixel_values": px})[0]
    print(f"이미지 인코더 int8 vs 원본 코사인: {cosine(ref, out):.4f}")

    # ---- 텍스트 인코더 ----
    st_txt = text_model()
    tokenizer = st_txt.tokenizer
    enc = tokenizer("작년 일본에서 먹은 돈까스", return_tensors="pt")
    txt_onnx = export_and_quantize(
        TextEncoder(st_txt),
        (enc["input_ids"], enc["attention_mask"]),
        ["input_ids", "attention_mask"],
        {
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
        },
        args.out / "text_encoder",
    )

    ref_t = st_txt.encode("작년 일본에서 먹은 돈까스")
    sess_t = ort.InferenceSession(str(txt_onnx))
    out_t = sess_t.run(
        None,
        {
            "input_ids": enc["input_ids"].numpy(),
            "attention_mask": enc["attention_mask"].numpy(),
        },
    )[0]
    print(f"텍스트 인코더 int8 vs 원본 코사인: {cosine(ref_t, out_t):.4f}")

    tokenizer.save_pretrained(args.out / "tokenizer")
    print(f"토크나이저 저장: {args.out / 'tokenizer'}")


if __name__ == "__main__":
    main()
