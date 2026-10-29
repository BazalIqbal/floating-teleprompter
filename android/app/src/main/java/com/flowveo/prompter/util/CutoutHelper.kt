package com.flowveo.prompter.util

import android.graphics.Rect
import android.view.WindowInsets
import android.view.WindowManager

/** Screen size and the space taken by the front-camera cutout, for placing the panel just below it. */
object CutoutHelper {

    /** Cutout (front camera) insets per side; in landscape the camera sits on the left or right. */
    data class Screen(val bounds: Rect, val cutoutTopPx: Int, val cutoutLeftPx: Int = 0, val cutoutRightPx: Int = 0)

    fun screen(windowManager: WindowManager): Screen {
        val metrics = windowManager.currentWindowMetrics
        val top = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.displayCutout() or WindowInsets.Type.statusBars(),
        )
        val cutout = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
        return Screen(metrics.bounds, top.top, cutout.left, cutout.right)
    }
}
