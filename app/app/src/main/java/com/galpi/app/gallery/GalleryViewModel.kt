package com.galpi.app.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 사진 권한 상태. */
enum class MediaAccess { FULL, PARTIAL, DENIED }

data class GalleryUiState(
    val access: MediaAccess = MediaAccess.DENIED,
    val photos: List<Photo> = emptyList(),
    val loading: Boolean = false,
)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = PhotoRepository(app)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun onAccessChanged(access: MediaAccess) {
        _uiState.value = _uiState.value.copy(access = access)
        if (access != MediaAccess.DENIED) refreshPhotos()
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val photos = repository.loadPhotos()
            _uiState.value = _uiState.value.copy(photos = photos, loading = false)
        }
    }
}
