package com.tracecam.ui.home

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tracecam.ui.hasCameraPermission

/**
 * Lets the user pick an image, then makes sure camera permission has been asked for
 * before handing the image to [onImageReady]. If permission is denied we still continue;
 * the trace screen explains the situation and offers a retry.
 */
@Composable
fun HomeScreen(onImageReady: (Uri) -> Unit) {
    val context = LocalContext.current
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingUri?.let(onImageReady)
        pendingUri = null
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        when {
            uri == null -> Unit
            context.hasCameraPermission() -> onImageReady(uri)
            else -> {
                pendingUri = uri
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                Text("Choose Image", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
