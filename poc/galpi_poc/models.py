"""임베딩 모델 로딩.

이미지 인코더: CLIP ViT-B/32 (openai 원본 가중치, sentence-transformers 포장)
텍스트 인코더: clip-ViT-B-32-multilingual-v1 (한국어 포함 50+개 언어를
              CLIP ViT-B/32 이미지 임베딩 공간에 정렬시킨 DistilBERT)

두 모델의 출력은 같은 512차원 공간을 공유하므로
한국어 텍스트 <-> 이미지 코사인 유사도 비교가 가능하다.
"""

from functools import lru_cache

from sentence_transformers import SentenceTransformer

IMAGE_MODEL_ID = "clip-ViT-B-32"
TEXT_MODEL_ID = "sentence-transformers/clip-ViT-B-32-multilingual-v1"
EMBED_DIM = 512


@lru_cache(maxsize=1)
def image_model() -> SentenceTransformer:
    return SentenceTransformer(IMAGE_MODEL_ID)


@lru_cache(maxsize=1)
def text_model() -> SentenceTransformer:
    return SentenceTransformer(TEXT_MODEL_ID)
