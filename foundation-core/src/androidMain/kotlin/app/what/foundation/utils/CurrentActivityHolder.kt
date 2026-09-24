package app.what.foundation.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    val currentActivity: Activity?
        get() = currentActivityRef?.get()

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        // Do not clear on pause: an activity might be transitioning or in multi-window
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        // Do not clear on stop if this activity is merely covered
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
        }
    }
}
