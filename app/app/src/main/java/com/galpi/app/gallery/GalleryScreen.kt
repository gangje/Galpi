package com.galpi.app.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.galpi.app.R

private fun requiredPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    else -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
}

private fun currentAccess(context: Context): MediaAccess {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_IMAGES) ->
            MediaAccess.FULL
        Build.VERSION.SDK_INT >= 34 &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
            MediaAccess.PARTIAL
        Build.VERSION.SDK_INT in 33..33 && granted(Manifest.permission.READ_MEDIA_IMAGES) ->
            MediaAccess.FULL
        Build.VERSION.SDK_INT < 33 && granted(Manifest.permission.READ_EXTERNAL_STORAGE) ->
            MediaAccess.FULL
        else -> MediaAccess.DENIED
    }
}

@Composable
fun GalleryScreen(viewModel: GalleryViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val indexedCount by viewModel.indexedCount.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onAccessChanged(currentAccess(context)) }

    LaunchedEffect(Unit) {
        viewModel.onAccessChanged(currentAccess(context))
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.access == MediaAccess.DENIED ->
                    PermissionRequest(onRequest = { permissionLauncher.launch(requiredPermissions()) })
                state.loading && state.photos.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.photos.isEmpty() ->
                    Text(
                        stringResource(R.string.no_photos),
                        Modifier.align(Alignment.Center),
                    )
                else -> PhotoGrid(
                    state = state,
                    indexedCount = indexedCount,
                    onQueryChanged = viewModel::onQueryChanged,
                    onSearch = viewModel::search,
                    onClearSearch = viewModel::clearSearch,
                    onManageSelection = { permissionLauncher.launch(requiredPermissions()) },
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    searching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(
                    Modifier.padding(8.dp).aspectRatio(1f),
                    strokeWidth = 2.dp,
                )
                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun IndexingBanner(indexed: Int, total: Int) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (indexed < total) {
            Text(
                text = stringResource(R.string.indexing_progress, indexed, total),
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else indexed / total.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            Text(
                text = stringResource(R.string.indexing_done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun PhotoGrid(
    state: GalleryUiState,
    indexedCount: Int,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onManageSelection: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SearchBar(
            query = state.query,
            searching = state.searching,
            onQueryChanged = onQueryChanged,
            onSearch = onSearch,
            onClear = onClearSearch,
        )
        IndexingBanner(indexed = indexedCount, total = state.photos.size)
        if (state.access == MediaAccess.PARTIAL) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.partial_access_notice),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onManageSelection) {
                    Text(stringResource(R.string.manage_selection))
                }
            }
        }

        val results = state.results
        if (results != null) {
            // 寃??寃곌낵 紐⑤뱶
            Text(
                text = if (state.matchedTerms.isEmpty()) {
                    stringResource(R.string.search_result_count, results.size)
                } else {
                    stringResource(R.string.search_result_count, results.size) +
                        "  쨌  " + stringResource(
                            R.string.search_filters,
                            state.matchedTerms.joinToString(", "),
                        )
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.search_no_results),
                        Modifier.align(Alignment.Center),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.mediaId }) { result ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(result.uri)
                                .crossfade(true)
                                .size(256)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                }
            }
        } else {
            // ?꾩껜 媛ㅻ윭由?紐⑤뱶
            Text(
                text = stringResource(R.string.photo_count, state.photos.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .size(256)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
    }
}
