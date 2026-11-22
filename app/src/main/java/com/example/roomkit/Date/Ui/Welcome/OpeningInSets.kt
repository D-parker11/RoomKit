@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Ui.Welcome

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun applyOpeningInsets(view: View) {
    val bottom = view.paddingBottom
    // MainActivity owns status/side insets; these screens own the bottom
    // navigation-bar inset because the BottomNavigationView is hidden here.
    ViewCompat.setOnApplyWindowInsetsListener(view) { target, insets ->
        val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        target.setPadding(target.paddingLeft, target.paddingTop, target.paddingRight, bottom + nav.bottom)
        insets
    }
    view.post { ViewCompat.requestApplyInsets(view) }
}
