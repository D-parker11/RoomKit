package com.example.roomkit.Date.Ui.Tenancy

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Local.Model.VacantRoomOption
import com.example.roomkit.R

class RoomOptionAdapter(
    context: Context,
    private val rooms: List<VacantRoomOption>
) : ArrayAdapter<VacantRoomOption>(context, 0, rooms) {

    var selectedRoomId: Long? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(position, convertView, parent)
    }

    private fun createViewFromResource(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dropdown_room_option, parent, false)

        val room = getItem(position) ?: return view

        val tvOption = view.findViewById<TextView>(R.id.tvRoomOptionText)
        val ivCheck = view.findViewById<ImageView>(R.id.ivCheckmark)

        // Rent formatted: Room 204 — $300/mo
        val rentDisplay = CurrencyFormatter.formatKhr(room.monthlyRent).removeSuffix(".00")
        tvOption.text = "Room ${room.roomNumber} — $rentDisplay/mo"

        val isSelected = room.roomId == selectedRoomId
        ivCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        view.setBackgroundResource(if (isSelected) R.drawable.bg_item_menu_selected else 0)

        return view
    }
}