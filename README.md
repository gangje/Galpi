# Galpi (갈피)

휴대폰 갤러리를 한국어 자연어로 검색하는 안드로이드 앱.

> "작년 일본에서 먹은 돈까스" → 관련 사진을 바로 찾아줍니다.

모든 처리는 **완전 온디바이스**로 동작하며, 사진은 기기 밖으로 나가지 않습니다.

## 동작 원리

쿼리를 세 요소로 분해해 하이브리드 검색:

```
"작년 일본에서 먹은 돈까스"
 ├─ 시간 필터: 작년 → 촬영일 범위 (EXIF)
 ├─ 장소 필터: 일본 → GPS 좌표 필터 (EXIF + 오프라인 역지오코딩)
 └─ 시각 쿼리: "돈까스" → CLIP 임베딩 유사도 검색
```

- 이미지 인코더: CLIP ViT-B/32 → 사진마다 512차원 벡터를 미리 인덱싱
- 텍스트 인코더: multilingual CLIP (한국어 지원) → 검색어를 같은 공간의 벡터로 변환
- 온디바이스 추론: ONNX Runtime Mobile / 저장: Room(SQLite)

## 저장소 구조

```
poc/    PC에서 모델·검색 품질을 검증하는 Python PoC
app/    Android 앱 (Kotlin + Jetpack Compose)
```

## 앱 빌드 준비

모델 파일(.onnx)은 용량 문제로 git에 포함되지 않는다. 빌드 전에 PoC로 생성해 assets에 복사:

```
cd poc
.venv\Scripts\python export_onnx.py
copy models\image_encoder.int8.onnx ..\app\app\src\main\assets\
copy models\text_encoder.int8.onnx ..\app\app\src\main\assets\
```

(`vocab.txt`는 git에 포함되어 있음)

## PoC 사용법

```
cd poc
.venv\Scripts\pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv\Scripts\pip install sentence-transformers pillow onnx onnxruntime

# 1) 사진 폴더 인덱싱 (1회)
.venv\Scripts\python index.py <사진_폴더>

# 2) 검색
.venv\Scripts\python search.py <사진_폴더> "작년 일본에서 먹은 돈까스" --open
```
