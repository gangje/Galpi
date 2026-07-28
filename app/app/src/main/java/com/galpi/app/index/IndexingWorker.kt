package com.galpi.app.index

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.galpi.app.R
import com.galpi.app.data.GalpiDatabase
import com.galpi.app.data.PhotoIndexEntity
import com.galpi.app.gallery.PhotoRepository
import com.galpi.app.ml.ClipImageEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 갤러리 전체를 임베딩 인덱싱하는 장기 실행 워커.
 * 최신 사진부터 처리하고, 삭제된 사진의 인덱스는 정리한다.
 */
class IndexingWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val dao = GalpiDatabase.get(applicationContext).photoIndexDao()
        val photos = PhotoRepository(applicationContext).loadPhotos() // 최신순

        // 삭제된 사진 인덱스 정리
        val currentIds = photos.map { it.id }.toHashSet()
        val stale = dao.indexedIds().filter { it !in currentIds }
        if (stale.isNotEmpty()) stale.chunked(500).forEach { dao.deleteByIds(it) }

        val indexed = dao.indexedIds().toHashSet()
        val pending = photos.filter { it.id !in indexed }
        if (pending.isEmpty()) return Result.success()

        ClipImageEncoder(applicationContext).use { encoder ->
            for (photo in pending) {
                if (isStopped) return Result.retry()
                try {
                    val bitmap = PhotoLoader.loadBitmap(applicationContext, photo.uri)
                        ?: continue
                    val embedding = encoder.encode(bitmap)
                    bitmap.recycle()
                    val exif = PhotoLoader.loadExif(applicationContext, photo.uri)
                    dao.insert(
                        PhotoIndexEntity(
                            mediaId = photo.id,
                            uri = photo.uri.toString(),
                            takenAtMillis = exif.takenAtMillis ?: photo.takenAtMillis,
                            lat = exif.lat,
                            lng = exif.lng,
                            embedding = toBytes(embedding),
                            indexedAtMillis = System.currentTimeMillis(),
                        ),
                    )
                } catch (e: Exception) {
                    // 개별 사진 실패는 건너뛰고 계속
                }
            }
        }
        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.indexing_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification: Notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.indexing_notification))
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "indexing"
        private const val NOTIFICATION_ID = 1
        private const val WORK_NAME = "galpi-indexing"

        fun toBytes(v: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.asFloatBuffer().put(v)
            return buf.array()
        }

        fun toFloats(bytes: ByteArray): FloatArray {
            val fb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val out = FloatArray(fb.remaining())
            fb.get(out)
            return out
        }

        /** 인덱싱을 시작하거나 이미 실행 중이면 유지한다. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexingWorker>().build(),
            )
        }
    }
}
