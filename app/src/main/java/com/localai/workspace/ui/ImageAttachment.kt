package com.localai.workspace.ui

import android.graphics.BitmapFactory
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ImageAttachment(path: String, compact: Boolean = false) {
    val context = LocalContext.current
    var bitmap by remember(path) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val root = File(context.filesDir, "attachments").canonicalFile
                val file = File(path).canonicalFile
                require(file.path.startsWith(root.path + File.separator) && file.isFile)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                val options = BitmapFactory.Options().apply {
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
                    inSampleSize = sample
                }
                BitmapFactory.decodeFile(file.path, options)
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), "Attached image", Modifier.sizeIn(maxWidth = if (compact) 56.dp else 240.dp,
            maxHeight = if (compact) 56.dp else 180.dp), contentScale = ContentScale.Fit)
    } ?: Text("Image attachment", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
}

@Composable
fun PendingImageAttachment(path: String, enabled: Boolean, onRemove: () -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        ImageAttachment(path, compact = true)
        Text("Image attached", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        androidx.compose.material3.IconButton(onClick = onRemove, enabled = enabled) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, "Remove image")
        }
    }
}
