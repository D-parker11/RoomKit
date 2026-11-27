@file:Suppress("PackageNaming")

package com.example.roomkit.Date.Ui.Common

import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.DimenRes

/** Uses the same font-scale-aware text dimensions as XML layouts. */
fun TextView.setRoomKitTextSize(@DimenRes size: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(size))
}
