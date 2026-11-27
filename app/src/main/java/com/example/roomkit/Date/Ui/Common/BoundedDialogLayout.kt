@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Common

import android.content.Context
import android.util.AttributeSet
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import com.example.roomkit.R

/** A wrap-content dialog with a weighted scroll area and a footer that stays visible. */
class BoundedDialogLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isInEditMode) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val metrics = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics
        val insets = metrics?.windowInsets?.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        val windowHeight = metrics?.bounds?.height() ?: resources.displayMetrics.heightPixels
        val usableHeight = windowHeight - (insets?.top ?: 0) - (insets?.bottom ?: 0)
        val margin = resources.getDimensionPixelSize(R.dimen.roomkit_size_24)
        val maximum = (usableHeight - margin * 2).coerceAtLeast(0)
        val parentLimit = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            maximum
        } else {
            minOf(MeasureSpec.getSize(heightMeasureSpec), maximum)
        }
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(parentLimit, MeasureSpec.AT_MOST))
    }
}
