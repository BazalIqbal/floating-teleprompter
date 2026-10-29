package com.flowveo.prompter.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/**
 * Opens the camera when prompting starts.
 * Must be called from an Activity: Android blocks activity starts from a background service.
 */
object CameraLauncher {

    /** A camera app the user can pick in Settings. */
    data class CameraApp(val packageName: String, val label: String)

    /** Social apps with their own camera, offered when installed. */
    private val SOCIAL_APPS = listOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.ss.android.ugc.trill", // TikTok (some regions)
        "com.lemon.lvoverseas", // CapCut
    )

    /**
     * Opens [packageName] if given, otherwise the phone's default camera in video mode.
     * Returns false when nothing could be opened.
     */
    fun open(activity: Activity, packageName: String?): Boolean {
        if (packageName != null) {
            val launch = activity.packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null && start(activity, launch)) return true
        }
        val intent = Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply {
            // Unofficial "front camera please" extras. Some camera apps honour one of them;
            // the rest ignore them and open the last-used lens.
            putExtra("android.intent.extras.CAMERA_FACING", 1)
            putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        return start(activity, intent)
    }

    /** Installed camera apps plus installed social apps, for the Settings picker. */
    fun installedApps(context: Context): List<CameraApp> {
        val pm = context.packageManager
        val cameras = pm.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA), PackageManager.MATCH_DEFAULT_ONLY)
            .map { CameraApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        val social = SOCIAL_APPS.mapNotNull { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                CameraApp(pkg, pm.getApplicationLabel(info).toString())
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
        return (cameras + social).distinctBy { it.packageName }
    }

    fun labelFor(context: Context, packageName: String?): String {
        if (packageName == null) return "Phone default"
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "Phone default (app not found)"
        }
    }

    private fun start(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
