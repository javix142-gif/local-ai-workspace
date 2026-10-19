package com.localai.workspace.artifacts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Builds the SAF create request from the immutable artifact's validated name and format. */
data class ArtifactExportRequest(val fileName: String, val format: ArtifactFormat)

class ArtifactCreateDocumentContract : ActivityResultContract<ArtifactExportRequest, Uri?>() {
    override fun createIntent(context: Context, input: ArtifactExportRequest): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.format.mime)
            .putExtra(Intent.EXTRA_TITLE, input.fileName)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
