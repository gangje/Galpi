package com.galpi.app.ml

import android.content.Context
import java.io.File

/** assets의 모델 파일을 filesDir로 1회 복사한다 (ONNX Runtime은 파일 경로 로드가 메모리에 유리). */
object ModelFiles {
    const val IMAGE_ENCODER = "image_encoder.int8.onnx"
    const val TEXT_ENCODER = "text_encoder.int8.onnx"
    const val VOCAB = "vocab.txt"

    fun ensure(context: Context, name: String): File {
        val out = File(context.filesDir, name)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(name).use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
        }
        return out
    }
}
