package com.localai.workspace.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.localai.workspace.artifacts.ArtifactFormat
import com.localai.workspace.artifacts.ArtifactSpec
import com.localai.workspace.artifacts.PptxImagePayloadValidator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

internal data class PptxImagePreviewItem(
    val slideNumber: Int,
    val slideTitle: String,
    val altText: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val bitmap: Bitmap
)

/** Decode only caller-supplied, already bounded PNG assets. Call from Dispatchers.Default. */
internal suspend fun decodePptxImagePreviews(spec: ArtifactSpec): List<PptxImagePreviewItem> {
    if (spec.format != ArtifactFormat.PPTX) return emptyList()
    val slides = spec.json().getJSONArray("slides")
    val result = mutableListOf<PptxImagePreviewItem>()
    var remainingPixels = MAX_TOTAL_PREVIEW_PIXELS
    try {
        for (index in 0 until slides.length()) {
            currentCoroutineContext().ensureActive()
            val slide = slides.getJSONObject(index)
            if (!slide.has("imagePngBase64")) continue
            check(remainingPixels > 0) { "PPTX_IMAGE_PREVIEW_PIXEL_BUDGET_EXCEEDED" }
            val payload = PptxImagePayloadValidator.decodeAndValidate(slide.getString("imagePngBase64"))
            val sampleSize = sampleSizeFor(payload.width, payload.height, minOf(MAX_IMAGE_PREVIEW_PIXELS, remainingPixels))
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(
                payload.bytes, 0, payload.bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
            )) { "PPTX_IMAGE_PREVIEW_DECODE_FAILED" }
            val decodedPixels = bitmap.width.toLong() * bitmap.height
            if (decodedPixels > remainingPixels || decodedPixels > MAX_IMAGE_PREVIEW_PIXELS) {
                bitmap.recycle()
                error("PPTX_IMAGE_PREVIEW_PIXEL_BUDGET_EXCEEDED")
            }
            val coroutineContext = currentCoroutineContext()
            if (!coroutineContext.isActive) {
                bitmap.recycle()
                coroutineContext.ensureActive()
            }
            result += PptxImagePreviewItem(
                slideNumber = index + 1,
                slideTitle = slide.getString("title"),
                altText = slide.getString("imageAltText"),
                sourceWidth = payload.width,
                sourceHeight = payload.height,
                bitmap = bitmap
            )
            remainingPixels -= decodedPixels
        }
        return result
    } catch (failure: Throwable) {
        result.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        throw failure
    }
}

@Composable
internal fun PptxImageVisualPreview(items: List<PptxImagePreviewItem>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Supplied image assets", style = MaterialTheme.typography.titleSmall)
        Text(
            "Image asset preview only · slide layout is not rendered",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("pptx-image-preview-disclaimer")
        )
        items.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Slide ${item.slideNumber} · ${item.slideTitle}", style = MaterialTheme.typography.titleSmall)
                    Image(
                        bitmap = item.bitmap.asImageBitmap(),
                        contentDescription = "Image on slide ${item.slideNumber}: ${item.altText}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .testTag("artifact-image-preview-${item.slideNumber}")
                    )
                    Text("Alternative text: ${item.altText}", style = MaterialTheme.typography.bodySmall)
                    Text("PNG source: ${item.sourceWidth} × ${item.sourceHeight} px", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private const val MAX_IMAGE_PREVIEW_PIXELS = 65_536L
private const val MAX_TOTAL_PREVIEW_PIXELS = 1_000_000L

private fun sampleSizeFor(width: Int, height: Int, pixelBudget: Long): Int {
    var sample = 1
    while (ceilDiv(width, sample).toLong() * ceilDiv(height, sample) > pixelBudget) sample *= 2
    return sample
}

private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
