package com.dahl404.penny937.ui

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.content.FileProvider
import java.io.File

object PennyHaptics {
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun select(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

object SharedFiles {
    fun share(context: Context, file: File, mime: String) {
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PeNNy export"))
        }
    }
}
