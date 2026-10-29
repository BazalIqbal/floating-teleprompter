package com.flowveo.prompter.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.flowveo.prompter.data.OverlayGeometry
import com.flowveo.prompter.util.CutoutHelper
import kotlin.math.roundToInt

/**
 * Adds the floating panel window over other apps, moves/resizes it, and switches it
 * between the full panel, the minimized bubble and the locked state.
 *
 * - The window is exactly the size of the panel; FLAG_NOT_TOUCH_MODAL lets every touch
 *   outside it fall through to the camera app (record button, zoom, lens switch).
 * - FLAG_NOT_FOCUSABLE keeps the keyboard and back button with the camera app.
 * - FLAG_HARDWARE_ACCELERATED is required: a window added directly from a Service is
 *   software-rendered otherwise, which made the panel stutter while the camera recorded.
 * - Locked: FLAG_NOT_TOUCHABLE plus window alpha 0.8, because Android 12+ only passes
 *   touches through overlays at or below 80% opacity.
 */
class OverlayWindowManager(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val density get() = context.resources.displayMetrics.density
    private var view: View? = null

    /** Panel geometry while minimized, to restore it afterwards. */
    private var panelGeometry: OverlayGeometry? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        BASE_FLAGS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Allow the panel to sit right next to the punch-hole camera.
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "Prompter overlay"
    }

    val isShowing get() = view != null

    val isLandscape get() = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Shows the panel at [saved] if it still fits on screen, otherwise at the default spot. */
    fun show(content: View, saved: OverlayGeometry?) {
        if (view != null) return
        apply(saved?.takeIf { fits(it) } ?: defaultGeometry())
        wm.addView(content, params)
        view = content
    }

    /** After rotation: use the geometry saved for the new orientation, or the default. */
    fun reposition(saved: OverlayGeometry?) {
        if (panelGeometry != null) return // minimized: keep the bubble where it is
        apply(saved?.takeIf { fits(it) } ?: defaultGeometry())
        update()
    }

    /** Current panel geometry (to save it per orientation). Null while minimized. */
    fun geometry(): OverlayGeometry? =
        if (panelGeometry != null) null else OverlayGeometry(params.x, params.y, params.width, params.height)

    fun moveBy(dx: Float, dy: Float) {
        val screen = CutoutHelper.screen(wm).bounds
        params.x = (params.x + dx.roundToInt()).coerceIn(0, (screen.width() - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.roundToInt()).coerceIn(0, (screen.height() - params.height).coerceAtLeast(0))
        update()
    }

    fun resizeBy(dw: Float, dh: Float) {
        val screen = CutoutHelper.screen(wm).bounds
        params.width = (params.width + dw.roundToInt()).coerceIn(dp(MIN_WIDTH_DP), screen.width() - params.x)
        params.height = (params.height + dh.roundToInt()).coerceIn(dp(MIN_HEIGHT_DP), screen.height() - params.y)
        update()
    }

    /** Shrinks the window to a bubble at the right edge, at the panel's height. */
    fun minimize() {
        if (panelGeometry != null) return
        panelGeometry = OverlayGeometry(params.x, params.y, params.width, params.height)
        val screen = CutoutHelper.screen(wm).bounds
        val size = dp(BUBBLE_DP)
        params.width = size
        params.height = size
        params.x = screen.width() - size - dp(8)
        params.y = (params.y + dp(40)).coerceAtMost(screen.height() - size)
        update()
    }

    fun restore() {
        val g = panelGeometry ?: return
        panelGeometry = null
        apply(g)
        update()
    }

    /** Locked: touches go straight through to the camera app. */
    fun setLocked(locked: Boolean) {
        params.flags = if (locked) BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else BASE_FLAGS
        params.alpha = if (locked) LOCKED_ALPHA else 1f
        update()
    }

    fun remove() {
        view?.let { wm.removeView(it) }
        view = null
        panelGeometry = null
    }

    /**
     * Default spot. Portrait: top-centre just under the front camera, full width minus
     * 16 dp, ~28% tall. Landscape: right beside the front camera (on whichever side the
     * cutout is), vertically centred, ~45% wide and ~55% tall.
     */
    private fun defaultGeometry(): OverlayGeometry {
        val screen = CutoutHelper.screen(wm)
        val w = screen.bounds.width()
        val h = screen.bounds.height()
        val margin = dp(MARGIN_DP)
        return if (isLandscape) {
            val width = (w * 0.45f).roundToInt()
            val height = (h * 0.55f).roundToInt()
            val gap = dp(8)
            val x = if (screen.cutoutRightPx > screen.cutoutLeftPx) {
                w - screen.cutoutRightPx - gap - width // camera on the right
            } else {
                screen.cutoutLeftPx + gap // camera on the left (or no cutout)
            }
            OverlayGeometry(x.coerceIn(0, w - width), (h - height) / 2, width, height)
        } else {
            OverlayGeometry(margin, screen.cutoutTopPx + dp(GAP_BELOW_CAMERA_DP), w - 2 * margin, (h * HEIGHT_FRACTION).roundToInt())
        }
    }

    private fun fits(g: OverlayGeometry): Boolean {
        val screen = CutoutHelper.screen(wm).bounds
        return g.width >= dp(MIN_WIDTH_DP) && g.height >= dp(MIN_HEIGHT_DP) &&
            g.x >= 0 && g.y >= 0 && g.x + g.width <= screen.width() && g.y + g.height <= screen.height()
    }

    private fun apply(g: OverlayGeometry) {
        params.x = g.x
        params.y = g.y
        params.width = g.width
        params.height = g.height
    }

    private fun update() {
        view?.let { wm.updateViewLayout(it, params) }
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private companion object {
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        const val LOCKED_ALPHA = 0.8f
        const val MARGIN_DP = 16
        const val GAP_BELOW_CAMERA_DP = 4
        const val HEIGHT_FRACTION = 0.28f
        const val MIN_WIDTH_DP = 200
        const val MIN_HEIGHT_DP = 140
        const val BUBBLE_DP = 56
    }
}
