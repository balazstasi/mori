package app.mori

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView

/**
 * A patch over part of Instagram that swallows taps. A cover with a [message] is an opaque panel
 * over a feed. One without dims a bottom bar button so it looks switched off.
 */
data class Cover(val key: String, val box: Box, val message: String? = null)

/** Keeps the accessibility overlays on screen in line with the latest list of covers. */
class Overlays(private val service: AccessibilityService) {

    private class Shown(val cover: Cover, val view: View)

    private val main = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val shown = HashMap<String, Shown>()
    private var counter: TextView? = null
    private val hideCounter = Runnable { hideCounterNow() }

    /** Safe to call from any thread. */
    fun show(covers: List<Cover>) {
        main.post { apply(covers) }
    }

    fun clear() {
        show(emptyList())
        main.post { hideCounterNow() }
    }

    /**
     * A small note at the top of the screen, such as "32/100", gone after a moment. Touches pass
     * through it, so it never gets in the way of a reel. Safe to call from any thread.
     */
    fun showCounter(text: String) {
        main.post {
            val view = counter ?: counterView().also { view ->
                try {
                    windowManager.addView(view, counterLayoutParams())
                    counter = view
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not add the counter", e)
                    return@post
                }
            }
            view.text = text
            main.removeCallbacks(hideCounter)
            main.postDelayed(hideCounter, COUNTER_MS)
        }
    }

    private fun hideCounterNow() {
        main.removeCallbacks(hideCounter)
        counter?.let(::remove)
        counter = null
    }

    private fun apply(covers: List<Cover>) {
        val keys = covers.mapTo(HashSet()) { it.key }
        shown.keys.filter { it !in keys }.forEach { key -> shown.remove(key)?.let { remove(it.view) } }

        for (cover in covers) {
            val current = shown[cover.key]
            if (current?.cover == cover) continue
            if (current != null && current.cover.message == cover.message) {
                try {
                    windowManager.updateViewLayout(current.view, layoutParams(cover.box))
                    shown[cover.key] = Shown(cover, current.view)
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not move a cover", e)
                }
                continue
            }
            current?.let { remove(it.view) }
            shown.remove(cover.key)
            val view = viewFor(cover)
            try {
                windowManager.addView(view, layoutParams(cover.box))
                shown[cover.key] = Shown(cover, view)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not add a cover", e)
            }
        }
    }

    private fun remove(view: View) {
        try {
            windowManager.removeView(view)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not remove a cover", e)
        }
    }

    private fun viewFor(cover: Cover): View {
        val dark = (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val message = cover.message
        val view = if (message == null) {
            View(service).apply {
                setBackgroundColor(if (dark) Color.argb(210, 0, 0, 0) else Color.argb(210, 255, 255, 255))
            }
        } else {
            val padding = (32 * service.resources.displayMetrics.density).toInt()
            TextView(service).apply {
                text = message
                gravity = Gravity.CENTER
                setPadding(padding, padding, padding, padding)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(if (dark) Color.rgb(230, 230, 230) else Color.rgb(38, 38, 38))
                setBackgroundColor(if (dark) Color.BLACK else Color.WHITE)
            }
        }
        // Taps and swipes stop here instead of reaching Instagram underneath.
        view.isClickable = true
        return view
    }

    // Reels are dark, so the note is a dark pill in both light and dark mode.
    private fun counterView(): TextView {
        val density = service.resources.displayMetrics.density
        return TextView(service).apply {
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 100 * density
                setColor(Color.argb(200, 0, 0, 0))
            }
        }
    }

    private fun counterLayoutParams() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (COUNTER_TOP_DP * service.resources.displayMetrics.density).toInt()
    }

    private fun layoutParams(box: Box) = WindowManager.LayoutParams(
        box.width,
        box.height,
        box.left,
        box.top,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        // Node bounds are measured from the top of the screen, cutout included.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private companion object {
        const val TAG = "Overlays"
        const val COUNTER_MS = 1_500L
        const val COUNTER_TOP_DP = 56
    }
}
