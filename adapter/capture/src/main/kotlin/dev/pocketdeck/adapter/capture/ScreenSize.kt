package dev.pocketdeck.adapter.capture

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

internal class ScreenSize(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
) {
    companion object {
        fun of(context: Context): ScreenSize {
            val windowManager = context.getSystemService(WindowManager::class.java)
            val density = context.resources.displayMetrics.densityDpi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.maximumWindowMetrics.bounds
                return ScreenSize(bounds.width(), bounds.height(), density)
            }
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            return ScreenSize(metrics.widthPixels, metrics.heightPixels, density)
        }
    }
}
