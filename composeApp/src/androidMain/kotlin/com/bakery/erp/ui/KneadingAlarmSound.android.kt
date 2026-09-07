package com.bakery.erp.ui

import android.content.Context
import android.media.MediaPlayer
import com.bakery.erp.R

/**
 * El Context lo inyecta `BakeryApp.onCreate` vía [initKneadingAlarmSound] —
 * mismo patrón que [LocalPreferences]. Si no se inicializó, sonar se vuelve
 * no-op en vez de reventar.
 */
@Volatile
private var appContext: Context? = null

@Volatile
private var mediaPlayer: MediaPlayer? = null

fun initKneadingAlarmSound(context: Context) {
    appContext = context.applicationContext
}

actual fun startKneadingAlarmSound() {
    if (mediaPlayer != null) return // ya está sonando
    val ctx = appContext ?: return
    mediaPlayer = runCatching {
        MediaPlayer.create(ctx, R.raw.kitchen_timer)?.apply {
            isLooping = true
            start()
        }
    }.getOrNull()
}

actual fun stopKneadingAlarmSound() {
    mediaPlayer?.let { mp ->
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.release() }
    }
    mediaPlayer = null
}
