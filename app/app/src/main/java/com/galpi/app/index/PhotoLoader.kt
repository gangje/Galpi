package com.galpi.app.index

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface

data class ExifMeta(val takenAtMillis: Long?, val lat: Double?, val lng: Double?)

object PhotoLoader {

    /** 임베딩용 비트맵. 썸네일 API를 우선 사용(빠르고 EXIF 회전 반영). */
    fun loadBitmap(context: Context, uri: Uri, targetSize: Int = 384): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
        } else {
            decodeSampled(context, uri, targetSize)
        }
    } catch (e: Exception) {
        try {
            decodeSampled(context, uri, targetSize)
        } catch (e2: Exception) {
            null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, targetSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val sample = maxOf(1, minOf(info.size.width, info.size.height) / targetSize)
                decoder.setTargetSampleSize(sample)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** EXIF에서 촬영 시각과 GPS를 읽는다 (GPS는 ACCESS_MEDIA_LOCATION 필요). */
    fun loadExif(context: Context, uri: Uri): ExifMeta = try {
        val target = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }
        context.contentResolver.openInputStream(target)?.use { stream ->
            val exif = ExifInterface(stream)
            val latLng = exif.latLong
            ExifMeta(
                takenAtMillis = exif.dateTimeOriginal ?: exif.dateTime,
                lat = latLng?.get(0),
                lng = latLng?.get(1),
            )
        } ?: ExifMeta(null, null, null)
    } catch (e: Exception) {
        ExifMeta(null, null, null)
    }
}
