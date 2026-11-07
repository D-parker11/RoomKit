package com.example.roomkit.Date.Ui.Tenancy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.example.roomkit.Date.Local.Model.TenantAssignmentFilter
import com.example.roomkit.Date.Local.Model.TenantAssignmentOption
import com.example.roomkit.Date.Local.Model.filterTenantAssignmentOptions
import com.example.roomkit.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.min

/** Presentation only: the existing live query and save-time checks remain authoritative. */
internal class TenantPickerDialog(
    context: Context,
    private val onSelected: (Long) -> Boolean,
    private val onRetry: () -> Unit
) : BottomSheetDialog(context) {
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(context, id)
    private val ink = color(R.color.roomkit_text_primary)
    private val muted = color(R.color.roomkit_text_secondary)
    private val surface = color(R.color.roomkit_surface)
    private val border = color(R.color.roomkit_border)
    private val soft = ColorUtils.blendARGB(surface, ink, 0.045f)
    private val availableInk = Color.rgb(22, 101, 52)
    private val availableFill = Color.rgb(234, 247, 239)
    private var snapshot = TenantPickerState()
    private var selectedTenantId: Long? = null
    private var filter = TenantAssignmentFilter.AVAILABLE
    private var visibleCount = 0

    private fun rounded(fill: Int, radius: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun label(value: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(if (bold) ink else muted)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        includeFontPadding = false
    }
    private fun params(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(14), dp(22), dp(18))
        background = GradientDrawable().apply {
            setColor(surface); val radius = dp(28).toFloat()
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        }
        isFocusableInTouchMode = true
    }
    private val search = EditText(context).apply {
        background = null; hint = "Search name or phone"
        contentDescription = "Search tenants by name or phone number"
        setHintTextColor(muted); setTextColor(ink); textSize = 15f
        setSingleLine(true); setPadding(dp(10), dp(14), 0, dp(14))
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
    }
    private val clearSearch = iconButton(Icon.CLOSE, "Clear search").apply {
        isVisible = false; setOnClickListener { search.setText("") }
    }
    private val segments = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(4), dp(4), dp(4), dp(4)); background = rounded(soft, 16)
    }
    private val availableTab = filterTab(TenantAssignmentFilter.AVAILABLE)
    private val allTab = filterTab(TenantAssignmentFilter.ALL)
    private val assignedTab = filterTab(TenantAssignmentFilter.ASSIGNED)
    private val sectionTitle = label("Available tenants", 14f, true)
    private val resultCount = label("", 12f)
    private val status = label("Loading tenant availability…", 15f).apply {
        gravity = Gravity.CENTER; setPadding(dp(18), dp(26), dp(18), dp(26))
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val retry = MaterialButton(context).apply {
        text = "Try again"; isAllCaps = false; cornerRadius = dp(14)
        backgroundTintList = ColorStateList.valueOf(ink); setTextColor(surface)
        isVisible = false; setOnClickListener { onRetry() }
    }
    private val rows = TenantRows()
    private val list = ListView(context).apply {
        adapter = rows; divider = null; setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = true; clipToPadding = false; setPadding(0, dp(2), 0, dp(4))
        setOnItemClickListener { _, _, position, _ ->
            val option = rows.getItem(position)
            val current = snapshot.options.firstOrNull { it.tenantId == option.tenantId }
            if (!snapshot.isLoading && snapshot.errorMessage == null &&
                current?.isAvailable == true && onSelected(current.tenantId)) dismiss()
        }
    }

    init {
        setTitle("Choose tenant")
        content.addView(View(context).apply { background = rounded(border, 3) },
            LinearLayout.LayoutParams(dp(32), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16)
            })
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("Choose tenant", 25f, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton(Icon.CLOSE, "Close tenant picker").apply {
            background = rounded(soft, 24); setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header)
        content.addView(label("Find an available tenant for this room.", 14f), params(top = 6, bottom = 20))

        // The native search container avoids theme-dependent TextInputLayout
        // underlines and removes the previous LayoutParams crash path entirely.
        val searchBox = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL; background = rounded(soft, 16, border)
            setPadding(dp(14), 0, dp(4), 0); minimumHeight = dp(56)
        }
        searchBox.addView(ImageView(context).apply {
            setImageDrawable(PickerIcon(Icon.SEARCH, muted))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        searchBox.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        searchBox.addView(clearSearch, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(searchBox, params(bottom = 16))
        listOf(availableTab, allTab, assignedTab).forEach {
            segments.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(segments, params(bottom = 20))
        val resultHeader = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        resultHeader.addView(sectionTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        resultHeader.addView(resultCount)
        content.addView(resultHeader, params(bottom = 12))
        content.addView(status, params()); content.addView(retry, params(bottom = 10))
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        content.addView(label("Assigned tenants stay linked to their current room until move-out.", 12f), params(top = 14))
        search.doAfterTextChanged { clearSearch.isVisible = !it.isNullOrEmpty(); updateList() }
        setContentView(content)
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setOnShowListener {
            val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.background = rounded(Color.TRANSPARENT, 0)
            sheet?.layoutParams?.let { p -> p.height = dp(550); sheet.layoutParams = p }
            behavior.skipCollapsed = true; behavior.state = BottomSheetBehavior.STATE_EXPANDED
            content.requestFocus(); content.post { resizeSheet() }
        }
        content.viewTreeObserver.addOnGlobalLayoutListener { if (isShowing) resizeSheet() }
    }

    private fun iconButton(kind: Icon, description: String) = ImageButton(context).apply {
        setImageDrawable(PickerIcon(kind, muted)); background = null
        setPadding(dp(14), dp(14), dp(14), dp(14)); contentDescription = description
        minimumWidth = dp(48); minimumHeight = dp(48)
    }
    private fun filterTab(value: TenantAssignmentFilter) = label("", 12.5f, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(3), dp(10), dp(3), dp(10))
        maxLines = 2; isClickable = true; isFocusable = true
        setOnClickListener { filter = value; updateList() }
    }
    fun render(state: TenantPickerState, selectedId: Long?) {
        snapshot = state; selectedTenantId = selectedId; updateList()
    }
    private fun updateList() {
        val available = snapshot.options.count { it.isAvailable }
        val assigned = snapshot.options.size - available
        listOf(
            Triple(availableTab, TenantAssignmentFilter.AVAILABLE, "Available · $available"),
            Triple(allTab, TenantAssignmentFilter.ALL, "All · ${snapshot.options.size}"),
            Triple(assignedTab, TenantAssignmentFilter.ASSIGNED, "Assigned · $assigned")
        ).forEach { (tab, value, title) ->
            val checked = filter == value
            tab.text = title; tab.isSelected = checked
            tab.background = if (checked) rounded(ink, 12) else null
            tab.setTextColor(if (checked) surface else muted)
            tab.contentDescription = "$title${if (checked) ", selected" else ""}"
        }
        val ready = !snapshot.isLoading && snapshot.errorMessage == null
        val options = if (ready) filterTenantAssignmentOptions(snapshot.options,
            search.text?.toString().orEmpty(), filter) else emptyList()
        visibleCount = options.size; rows.submit(options)
        list.isVisible = options.isNotEmpty(); retry.isVisible = snapshot.errorMessage != null
        status.isVisible = options.isEmpty()
        sectionTitle.text = when (filter) {
            TenantAssignmentFilter.AVAILABLE -> "Available tenants"
            TenantAssignmentFilter.ALL -> "All tenants"
            TenantAssignmentFilter.ASSIGNED -> "Assigned tenants"
        }
        resultCount.text = if (!ready) "" else "${options.size} ${if (options.size == 1) "result" else "results"}"
        status.text = when {
            snapshot.isLoading -> "Checking tenant availability…"
            snapshot.errorMessage != null -> snapshot.errorMessage
            snapshot.options.isEmpty() -> "No tenants yet\nAdd a tenant from More → Tenants."
            search.text?.isNotBlank() == true -> "No matches found\nTry another name, phone number or filter."
            filter == TenantAssignmentFilter.AVAILABLE -> "No available tenants\nOpen All to view current room assignments."
            filter == TenantAssignmentFilter.ASSIGNED -> "No assigned tenants\nNobody currently has an active room."
            else -> "No tenants found"
        }
        if (isShowing) content.post { resizeSheet() }
    }
    private fun resizeSheet() {
        if (!isShowing || content.width == 0) return
        val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val frame = Rect(); window?.decorView?.getWindowVisibleDisplayFrame(frame)
        val screenHeight = context.resources.displayMetrics.heightPixels
        val usable = if (frame.height() > dp(240)) min(frame.height(), screenHeight) else screenHeight
        val cap = (usable * 0.90f).toInt()
        var chrome = content.paddingTop + content.paddingBottom
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child === list || child.visibility == View.GONE) continue
            val margins = child.layoutParams as? ViewGroup.MarginLayoutParams
            chrome += child.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
        }
        val fontScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val rowSpace = if (list.isVisible) (dp(116) * min(visibleCount, 4) * fontScale).toInt() else 0
        val desired = (chrome + rowSpace + dp(12)).coerceAtLeast(dp(380)).coerceAtMost(cap)
        val p = sheet.layoutParams
        if (kotlin.math.abs(p.height - desired) > dp(2)) { p.height = desired; sheet.layoutParams = p }
    }

    private inner class TenantRows : BaseAdapter() {
        private var items: List<TenantAssignmentOption> = emptyList()
        fun submit(value: List<TenantAssignmentOption>) { items = value; notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = getItem(position).tenantId
        override fun hasStableIds() = true
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = getItem(position).isAvailable
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val option = getItem(position)
            val outer = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10))
                val card = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(104)
                    setPadding(dp(14), dp(16), dp(12), dp(16))
                }
                val avatar = label("", 16f, true).apply { gravity = Gravity.CENTER }
                card.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
                val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val name = label("", 16f, true).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
                val phone = label("", 13f)
                val statusLine = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
                val badge = label("", 11f, true).apply { setPadding(dp(8), dp(4), dp(8), dp(4)); gravity = Gravity.CENTER }
                val detail = label("", 12f).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
                statusLine.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(7) })
                statusLine.addView(detail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                body.addView(name, params(bottom = 4)); body.addView(phone, params(bottom = 9)); body.addView(statusLine)
                card.addView(body, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val trailing = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                card.addView(trailing, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginStart = dp(8) })
                addView(card, params()); tag = RowViews(card, avatar, name, phone, badge, detail, trailing)
            }
            val holder = outer.tag as RowViews
            val available = option.isAvailable
            val selected = available && option.tenantId == selectedTenantId
            holder.name.text = option.fullName; holder.name.setTextColor(if (available) ink else muted)
            holder.phone.text = option.phoneNumber.ifBlank { "No phone number" }
            holder.avatar.text = option.fullName.trim().split(Regex("\\s+")).take(2)
                .mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase(Locale.ROOT)
            holder.avatar.background = rounded(soft, 22)
            holder.badge.text = if (available) "Available" else "Assigned"
            holder.badge.background = rounded(if (available) availableFill else soft, 8)
            holder.badge.setTextColor(if (available) availableInk else muted)
            holder.detail.text = if (available) "No active room" else
                listOfNotNull(option.currentRoomNumber?.let { "Room $it" }, option.currentBuildingName)
                    .joinToString(" · ").ifBlank { "Active tenancy" }
            holder.trailing.setImageDrawable(PickerIcon(when {
                selected -> Icon.CHECK; available -> Icon.NEXT; else -> Icon.LOCK
            }, if (selected) availableInk else muted))
            holder.card.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 20)),
                rounded(surface, 18, if (selected) ink else border), rounded(Color.WHITE, 18))
            outer.isEnabled = available
            outer.contentDescription = "${option.fullName}, ${holder.phone.text}, ${option.assignmentLabel}" +
                    if (selected) ", selected" else if (!available) ", unavailable for assignment" else ""
            return outer
        }
    }
    private data class RowViews(val card: LinearLayout, val avatar: TextView, val name: TextView,
                                val phone: TextView, val badge: TextView, val detail: TextView, val trailing: ImageView)
    private enum class Icon { SEARCH, CLOSE, NEXT, CHECK, LOCK }
    private class PickerIcon(private val kind: Icon, color: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun draw(canvas: Canvas) {
            val saved = canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
            when (kind) {
                Icon.SEARCH -> { canvas.drawCircle(10f, 10f, 6f, paint); canvas.drawLine(14.5f, 14.5f, 20f, 20f, paint) }
                Icon.CLOSE -> { canvas.drawLine(7f, 7f, 17f, 17f, paint); canvas.drawLine(7f, 17f, 17f, 7f, paint) }
                Icon.NEXT -> { canvas.drawLine(9f, 6f, 15f, 12f, paint); canvas.drawLine(15f, 12f, 9f, 18f, paint) }
                Icon.CHECK -> { canvas.drawLine(5f, 12f, 10f, 17f, paint); canvas.drawLine(10f, 17f, 19f, 7f, paint) }
                Icon.LOCK -> { canvas.drawRoundRect(5f, 10f, 19f, 21f, 2f, 2f, paint); canvas.drawArc(8f, 2f, 16f, 16f, 180f, 180f, false, paint) }
            }
            canvas.restoreToCount(saved)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
