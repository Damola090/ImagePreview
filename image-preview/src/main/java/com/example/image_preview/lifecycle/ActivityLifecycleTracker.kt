package com.example.image_preview.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.image_preview.core.EventTracker

/**
 * Automatically captures screen views by hooking into the Application
 * lifecycle callbacks. The developer never has to add tracking code to
 * individual Activities — this runs globally for the entire app.
 *
 * This is one of the most powerful patterns in SDK development.
 */
internal class ActivityLifecycleTracker(
    private val tracker: EventTracker,
    private val shouldTrackScreenViews: Boolean = true
) : Application.ActivityLifecycleCallbacks {

    // Track foreground/background transitions
    private var activitiesStarted = 0

    override fun onActivityStarted(activity: Activity) {
        activitiesStarted++
        if (activitiesStarted == 1) {
            // App came to foreground
            tracker.capture(
                event = "Application Opened",
                properties = mapOf("from_background" to true)
            )
        }
    }

    override fun onActivityStopped(activity: Activity) {
        activitiesStarted--
        if (activitiesStarted == 0) {
            // App went to background
            tracker.capture(
                event = "Application Backgrounded",
                properties = emptyMap()
            )
        }
    }

    override fun onActivityResumed(activity: Activity) {
        if (shouldTrackScreenViews) {
            // Use the Activity class name as screen name by default
            // Developers can override this by setting a custom screen name
            val screenName = activity.javaClass.simpleName
                .removeSuffix("Activity")
                .removeSuffix("Screen")

            tracker.capture(
                event = "Screen Viewed",
                properties = mapOf(
                    "screen_name" to screenName,
                    "activity_class" to activity.javaClass.name
                )
            )
        }
    }

    // Required overrides — no-ops for our purposes
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityDestroyed(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
