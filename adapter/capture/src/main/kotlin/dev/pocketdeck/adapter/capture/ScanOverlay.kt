package dev.pocketdeck.adapter.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * The most width the status text may take, so that the pill's padding, the dot, the Stop button and the margins at both sides
 * always fit on the screen. Without it a long status ("Open My Cards ...") took the whole width and the Stop button, laid out
 * last, ended up off screen where it could not be tapped.
 */
internal fun statusMaxWidth(
    screenWidthPx: Int,
    density: Float,
): Int = (screenWidthPx - RESERVED_DP * density).toInt().coerceAtLeast((MIN_STATUS_DP * density).toInt())

private const val RESERVED_DP = 176
private const val MIN_STATUS_DP = 96

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
        status.setTextColor(INK_ON_PILL)
        status.textSize = STATUS_TEXT_SP
        // A long status wraps instead of pushing the Stop button off the screen.
        status.maxLines = 2
        status.ellipsize = TextUtils.TruncateAt.END
        status.maxWidth = statusMaxWidth(context.resources.displayMetrics.widthPixels, density)
        status.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val dot =
            View(context).apply {
                background =
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(ACCENT_ON_PILL)
                    }
            }
        val stop =
            Button(context).apply {
                text = context.getString(R.string.scan_stop)
                isAllCaps = false
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setTextColor(ON_ACCENT)
                stateListAnimator = null
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(18), 0, dp(18), 0)
                background =
                    GradientDrawable().apply {
                        setColor(ACCENT_ON_PILL)
                        cornerRadius = dp(CORNER_DP).toFloat()
                    }
                setOnClickListener { onStop() }
            }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(6), dp(6), dp(6))
            background =
                GradientDrawable().apply {
                    setColor(PILL_COLOR)
                    cornerRadius = dp(CORNER_DP).toFloat()
                }
            addView(dot, LinearLayout.LayoutParams(dp(DOT_DP), dp(DOT_DP)).apply { marginEnd = dp(10) })
            addView(status)
            addView(
                stop,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(BUTTON_HEIGHT_DP)).apply {
                    marginStart = dp(12)
                },
            )
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

        // The Signal brand's dark-theme values: the pill floats over a game, so it is always dark.
        const val PILL_COLOR = 0xF2171B22.toInt()
        const val INK_ON_PILL = 0xFFF1F4F9.toInt()
        const val ACCENT_ON_PILL = 0xFFFF7455.toInt()
        const val ON_ACCENT = 0xFF1A0B07.toInt()
        const val DOT_DP = 10
        const val CORNER_DP = 28
        const val BUTTON_HEIGHT_DP = 36
        const val DRAG_SLOP_PX = 12
        const val INITIAL_X = 24
        const val INITIAL_Y = 140
    }
}
