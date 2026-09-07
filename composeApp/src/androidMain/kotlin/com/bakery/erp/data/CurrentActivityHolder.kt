package com.bakery.erp.data

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Mantiene una referencia débil al Activity en primer plano. Necesario para servicios que
 * requieren un Context de Activity (no de Application), como `PrintManager.print()` que
 * abre un diálogo del sistema y falla con "startActivity() from outside Activity" si recibe
 * el Application context.
 *
 * Se registra como [Application.ActivityLifecycleCallbacks] en `BakeryApp.onCreate()`.
 */
object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var activityRef: WeakReference<Activity>? = null

    val activity: Activity? get() = activityRef?.get()

    fun registerWith(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
