package com.galpi.app.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(
    val id: Long,
    val uri: Uri,
    val takenAtMillis: Long,
)

class PhotoRepository(private val context: Context) {

    /** 갤러리의 모든 사진을 최신순으로 조회한다. */
    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val taken = cursor.getLong(takenCol)
                    .takeIf { it > 0 }
                    ?: (cursor.getLong(addedCol) * 1000)
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    takenAtMillis = taken,
                )
            }
        }
        photos
    }
}
