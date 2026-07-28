package com.galpi.app.ml

import android.content.Context

/**
 * multilingual DistilBERT용 WordPiece 토크나이저 (BertNormalizer: lowercase=false).
 * PoC의 HuggingFace tokenizer와 같은 input_ids를 만드는 것이 목표.
 */
class WordPieceTokenizer(context: Context) {
    private val vocab: HashMap<String, Int> = HashMap(140_000)
    private val clsId: Int
    private val sepId: Int
    private val unkId: Int

    init {
        ModelFiles.ensure(context, ModelFiles.VOCAB).bufferedReader().useLines { lines ->
            lines.forEachIndexed { i, token -> vocab[token] = i }
        }
        clsId = vocab.getValue("[CLS]")
        sepId = vocab.getValue("[SEP]")
        unkId = vocab.getValue("[UNK]")
    }

    /** [CLS] ... [SEP] 포함 input_ids. */
    fun encode(text: String, maxLen: Int = 128): LongArray {
        val ids = mutableListOf(clsId)
        for (word in basicTokenize(text)) {
            ids += wordPiece(word)
            if (ids.size >= maxLen - 1) break
        }
        val trimmed = ids.take(maxLen - 1) + sepId
        return LongArray(trimmed.size) { trimmed[it].toLong() }
    }

    /** BertNormalizer + 공백/문장부호 분리, 한중일 표의문자 개별 분리. */
    private fun basicTokenize(text: String): List<String> {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch.code == 0 || ch.code == 0xFFFD || ch.isISOControl() && ch != '\t' && ch != '\n' && ch != '\r' -> {}
                isCjkIdeograph(ch) -> sb.append(' ').append(ch).append(' ')
                ch.isWhitespace() -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        val words = mutableListOf<String>()
        for (chunk in sb.split(' ')) {
            if (chunk.isEmpty()) continue
            // 문장부호를 독립 토큰으로 분리
            var start = 0
            for (i in chunk.indices) {
                if (isPunctuation(chunk[i])) {
                    if (i > start) words += chunk.substring(start, i)
                    words += chunk[i].toString()
                    start = i + 1
                }
            }
            if (start < chunk.length) words += chunk.substring(start)
        }
        return words
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.length > 100) return listOf(unkId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var id: Int? = null
            while (start < end) {
                val piece = (if (start > 0) "##" else "") + word.substring(start, end)
                val found = vocab[piece]
                if (found != null) {
                    id = found
                    break
                }
                end--
            }
            if (id == null) return listOf(unkId) // 일부라도 실패하면 단어 전체가 [UNK]
            pieces += id
            start = end
        }
        return pieces
    }

    private fun isCjkIdeograph(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x4E00..0x9FFF) || (c in 0x3400..0x4DBF) ||
            (c in 0xF900..0xFAFF) || (c in 0x2E80..0x2EFF)
    }

    private fun isPunctuation(ch: Char): Boolean {
        val c = ch.code
        if ((c in 33..47) || (c in 58..64) || (c in 91..96) || (c in 123..126)) return true
        val type = Character.getType(ch)
        return type in intArrayOf(
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
        )
    }
}
