package com.galpi.app.search

import android.content.Context
import android.net.Uri
import com.galpi.app.data.GalpiDatabase
import com.galpi.app.index.IndexingWorker
import com.galpi.app.ml.ClipTextEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchResult(
    val mediaId: Long,
    val uri: Uri,
    val similarity: Float,
)

/**
 * 하이브리드 검색: 메타데이터 필터(기간/장소)로 후보를 좁힌 뒤
 * 텍스트 임베딩과 코사인 유사도로 랭킹한다.
 */
class SearchEngine(private val context: Context) {
    private var textEncoder: ClipTextEncoder? = null

    suspend fun search(query: String, topK: Int = 60): Pair<ParsedQuery, List<SearchResult>> =
        withContext(Dispatchers.Default) {
            val parsed = QueryParser.parse(query)
            val dao = GalpiDatabase.get(context).photoIndexDao()
            val all = dao.all()

            val candidates = all.filter { photo ->
                val timeOk = parsed.startMillis == null ||
                    (photo.takenAtMillis in parsed.startMillis!!..parsed.endMillis!!)
                val placeOk = parsed.bbox == null || run {
                    val b = parsed.bbox!!
                    photo.lat != null && photo.lng != null &&
                        photo.lat in b[0]..b[1] && photo.lng in b[2]..b[3]
                }
                timeOk && placeOk
            }
            if (candidates.isEmpty()) return@withContext parsed to emptyList()

            val encoder = textEncoder ?: ClipTextEncoder(context).also { textEncoder = it }
            val q = encoder.encode(parsed.visualText)

            val scored = candidates.map { photo ->
                val emb = IndexingWorker.toFloats(photo.embedding)
                var dot = 0f
                for (i in q.indices) dot += q[i] * emb[i]
                SearchResult(photo.mediaId, Uri.parse(photo.uri), dot)
            }
            val sorted = scored.sortedByDescending { it.similarity }
            val top = sorted.firstOrNull()?.similarity ?: 0f
            // 절대 하한선 + 1위 대비 상대 컷오프: 확실한 매치만 남기고 꼬리를 잘라낸다
            val cutoff = maxOf(SIMILARITY_FLOOR, top * RELATIVE_CUTOFF)
            parsed to sorted.filter { it.similarity >= cutoff }.take(topK)
        }

    companion object {
        /** 이보다 낮으면 관련 없는 사진으로 간주 (PoC에서 검증한 값). */
        const val SIMILARITY_FLOOR = 0.2f

        /** 1위 유사도의 이 비율 미만은 결과에서 제외. */
        const val RELATIVE_CUTOFF = 0.88f
    }
}
