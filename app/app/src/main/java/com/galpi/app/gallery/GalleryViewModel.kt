package com.galpi.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galpi.app.data.GalpiDatabase
import com.galpi.app.index.IndexingWorker
import com.galpi.app.search.SearchEngine
import com.galpi.app.search.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 사진 권한 상태. */
enum class MediaAccess { FULL, PARTIAL, DENIED }

data class GalleryUiState(
    val access: MediaAccess = MediaAccess.DENIED,
    val photos: List<Photo> = emptyList(),
    val loading: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<SearchResult>? = null, // null = 검색 모드 아님
    val matchedTerms: List<String> = emptyList(),
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PhotoRepository(app)
    private val searchEngine = SearchEngine(app)
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /** 인덱싱 완료된 사진 수 (Room을 실시간 관찰). */
    val indexedCount: StateFlow<Int> =
        GalpiDatabase.get(app).photoIndexDao().countFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun onAccessChanged(access: MediaAccess) {
        _uiState.value = _uiState.value.copy(access = access)
        if (access != MediaAccess.DENIED) {
            refreshPhotos()
            IndexingWorker.enqueue(getApplication())
        }
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val photos = repository.loadPhotos()
            _uiState.value = _uiState.value.copy(photos = photos, loading = false)
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.isBlank()) clearSearch()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(searching = true)
            val (parsed, results) = searchEngine.search(query)
            _uiState.value = _uiState.value.copy(
                searching = false,
                results = results,
                matchedTerms = parsed.matchedTerms,
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            query = "",
            searching = false,
            results = null,
            matchedTerms = emptyList(),
        )
    }
}
