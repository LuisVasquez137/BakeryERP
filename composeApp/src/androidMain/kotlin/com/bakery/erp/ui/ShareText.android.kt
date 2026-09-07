package com.bakery.erp.ui

import android.content.Intent
import com.bakery.erp.data.CurrentActivityHolder

actual fun shareText(text: String, subject: String) {
    val activity = CurrentActivityHolder.activity ?: return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    activity.startActivity(Intent.createChooser(send, subject.ifEmpty { "Compartir" }))
}
