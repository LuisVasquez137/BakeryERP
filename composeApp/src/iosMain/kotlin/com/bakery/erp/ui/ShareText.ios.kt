package com.bakery.erp.ui

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun shareText(text: String, subject: String) {
    val keyWindow = UIApplication.sharedApplication.keyWindow ?: return
    val rootVc = keyWindow.rootViewController ?: return
    val activityVc = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null
    )
    rootVc.presentViewController(activityVc, animated = true, completion = null)
}
