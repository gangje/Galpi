package com.galpi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 인덱싱된 사진 1장 = 임베딩 + 검색 필터용 메타데이터. */
@Entity(tableName = "photo_index")
data class PhotoIndexEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val takenAtMillis: Long,
    val lat: Double?,
    val lng: Double?,
    /** float32 512개를 little-endian으로 담은 2048바이트. */
    val embedding: ByteArray,
    val indexedAtMillis: Long,
)
