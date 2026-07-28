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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
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
                    onManageSelection = { permissionLauncher.launch(requiredPermissions()) },
                )
            }
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
private fun PhotoGrid(state: GalleryUiState, onManageSelection: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}
