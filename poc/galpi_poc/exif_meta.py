"""EXIF에서 촬영 일시와 GPS 좌표를 추출한다.

안드로이드 앱에서는 ExifInterface가 같은 역할을 한다.
여기서는 하이브리드 검색(시간/장소 필터) 검증용으로만 쓴다.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from PIL import Image, ExifTags

_TAG_DATETIME_ORIGINAL = 36867  # DateTimeOriginal
_TAG_DATETIME = 306  # 파일 수정 기준 fallback
_TAG_GPSINFO = 34853


@dataclass
class PhotoMeta:
    path: str
    taken_at: str | None  # ISO 8601
    lat: float | None
    lng: float | None


def _to_degrees(value) -> float:
    d, m, s = (float(x) for x in value)
    return d + m / 60.0 + s / 3600.0


def extract_meta(path: Path) -> PhotoMeta:
    taken_at = lat = lng = None
    try:
        with Image.open(path) as im:
            exif = im.getexif()
            raw_dt = exif.get(_TAG_DATETIME_ORIGINAL) or exif.get(_TAG_DATETIME)
            if raw_dt:
                try:
                    taken_at = datetime.strptime(
                        str(raw_dt), "%Y:%m:%d %H:%M:%S"
                    ).isoformat()
                except ValueError:
                    pass

            gps = exif.get_ifd(_TAG_GPSINFO)
            if gps:
                lat_ref = gps.get(ExifTags.GPS.GPSLatitudeRef)
                lat_val = gps.get(ExifTags.GPS.GPSLatitude)
                lng_ref = gps.get(ExifTags.GPS.GPSLongitudeRef)
                lng_val = gps.get(ExifTags.GPS.GPSLongitude)
                if lat_val and lng_val:
                    lat = _to_degrees(lat_val) * (-1 if lat_ref == "S" else 1)
                    lng = _to_degrees(lng_val) * (-1 if lng_ref == "W" else 1)
    except Exception:
        pass  # EXIF가 없거나 깨진 파일은 메타 없이 진행
    return PhotoMeta(path=str(path), taken_at=taken_at, lat=lat, lng=lng)
