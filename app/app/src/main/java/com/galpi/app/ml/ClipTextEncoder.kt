package com.galpi.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.LongBuffer

/**
 * multilingual CLIP 텍스트 인코더 (INT8 ONNX).
 * 입력: 한국어 검색어 -> 출력: 이미지와 같은 공간의 L2 정규화 512차원 임베딩.
 */
class ClipTextEncoder(context: Context) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(
        ModelFiles.ensure(context, ModelFiles.TEXT_ENCODER).absolutePath,
        OrtSession.SessionOptions(),
    )
    private val tokenizer = WordPieceTokenizer(context)

    fun encode(text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val mask = LongArray(ids.size) { 1L }
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idsTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { maskTensor ->
                session.run(
                    mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor),
                ).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val out = (result[0].value as Array<FloatArray>)[0]
                    return ClipImageEncoder.l2Normalize(out)
                }
            }
        }
    }

    override fun close() = session.close()
}
