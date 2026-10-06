package dev.pocketdeck.adapter.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The small draggable status pill with a Stop button, drawn over the game while a scan runs. It sits near the top
 * of the screen, where the game's "My Cards" screen has a header and no cards.
 */
class ScanOverlay(
    private val context: Context,
    private val onStop: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val status = TextView(context)
    private val view = buildView()
    private val layoutParams = buildLayoutParams()
    private var shown = false

    fun show() =
        main.post {
            if (!shown) {
                windowManager.addView(view, layoutParams)
                shown = true
            }
        }

    fun update(text: String) {
        main.post { status.text = text }
    }

    fun remove() =
        main.post {
            if (shown) {
                windowManager.removeView(view)
                shown = false
            }
        }

    private fun buildView(): View {
        val density = context.resources.displayMetrics.density

        fun dp(value: Int) = (value * density).toInt()
        status.setTextColor(Color.WHITE)
        status.textSize = STATUS_TEXT_SP
        val stop =
            Button(context).apply {
                text = context.getString(R.string.scan_stop)
                isAllCaps = false
                setOnClickListener { onStop() }
            }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(6), dp(4))
            background =
                GradientDrawable().apply {
                    setColor(PILL_COLOR)
                    cornerRadius = dp(CORNER_DP).toFloat()
                }
            addView(status)
            addView(stop, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(BUTTON_HEIGHT_DP)))
            setOnTouchListener(dragListener())
        }
    }

    private fun buildLayoutParams() =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = INITIAL_X
                y = INITIAL_Y
            }

    private fun dragListener() =
        object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(
                v: View,
                event: MotionEvent,
            ): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = layoutParams.x
                        startY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        moved = moved || abs(event.rawX - touchX) + abs(event.rawY - touchY) > DRAG_SLOP_PX
                        if (moved) {
                            layoutParams.x = startX + (event.rawX - touchX).toInt()
                            layoutParams.y = startY + (event.rawY - touchY).toInt()
                            windowManager.updateViewLayout(view, layoutParams)
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!moved) v.performClick()
                    }
                }
                return true
            }
        }

    private companion object {
        const val STATUS_TEXT_SP = 13f
        const val PILL_COLOR = 0xE6202833.toInt()
        const val CORNER_DP = 24
        const val BUTTON_HEIGHT_DP = 40
        const val DRAG_SLOP_PX = 12
        const val INITIAL_X = 24
        const val INITIAL_Y = 140
    }
}
