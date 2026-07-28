package com.galpi.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * CLIP ViT-B/32 이미지 인코더 (INT8 ONNX).
 * 입력: 224x224 RGB 비트맵 -> 출력: L2 정규화된 512차원 임베딩.
 */
class ClipImageEncoder(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        ModelFiles.ensure(context, ModelFiles.IMAGE_ENCODER).absolutePath,
        OrtSession.SessionOptions(),
    )

    fun encode(bitmap: Bitmap): FloatArray {
        val input = preprocess(bitmap)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), SHAPE).use { tensor ->
            session.run(mapOf("pixel_values" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<FloatArray>)[0]
                return l2Normalize(out)
            }
        }
    }

    /** CLIPImageProcessor와 동일: 짧은 변 224 리사이즈 -> 중앙 크롭 -> mean/std 정규화 (CHW). */
    private fun preprocess(src: Bitmap): FloatArray {
        val scale = SIZE.toFloat() / minOf(src.width, src.height)
        val w = (src.width * scale + 0.5f).toInt().coerceAtLeast(SIZE)
        val h = (src.height * scale + 0.5f).toInt().coerceAtLeast(SIZE)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val x0 = (w - SIZE) / 2
        val y0 = (h - SIZE) / 2
        val pixels = IntArray(SIZE * SIZE)
        scaled.getPixels(pixels, 0, SIZE, x0, y0, SIZE, SIZE)
        if (scaled !== src) scaled.recycle()

        val out = FloatArray(3 * SIZE * SIZE)
        val area = SIZE * SIZE
        for (i in 0 until area) {
            val p = pixels[i]
            out[i] = ((p shr 16 and 0xFF) / 255f - MEAN_R) / STD_R
            out[area + i] = ((p shr 8 and 0xFF) / 255f - MEAN_G) / STD_G
            out[2 * area + i] = ((p and 0xFF) / 255f - MEAN_B) / STD_B
        }
        return out
    }

    override fun close() = session.close()

    companion object {
        const val SIZE = 224
        private val SHAPE = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        private const val MEAN_R = 0.48145466f
        private const val MEAN_G = 0.4578275f
        private const val MEAN_B = 0.40821073f
        private const val STD_R = 0.26862954f
        private const val STD_G = 0.26130258f
        private const val STD_B = 0.27577711f

        fun l2Normalize(v: FloatArray): FloatArray {
            var sum = 0f
            for (x in v) sum += x * x
            val norm = sqrt(sum).coerceAtLeast(1e-12f)
            return FloatArray(v.size) { v[it] / norm }
        }
    }
}
