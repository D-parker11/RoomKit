package com.example.roomkit

import android.app.Activity
import android.widget.FrameLayout
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Inflates shipped views without running screen/database operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class RoomKitLayoutsInflationTest {
    @Test
    fun screensDialogsSheetsAndRowsInflateWithAppTheme() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_RoomKit)
        val activity = controller.setup().get()
        try {
            val layouts = intArrayOf(
                R.layout.bottom_sheet_add_building,
                R.layout.bottom_sheet_manage_tenancy,
                R.layout.bottom_sheet_paper_size,
                R.layout.bottom_sheet_pdf_exported,
                R.layout.bottom_sheet_record_payment,
                R.layout.bottom_sheet_void_payment,
                R.layout.dialog_dashboard_month,
                R.layout.dialog_move_in_review,
                R.layout.dialog_review,
                R.layout.dialog_void_invoice,
                R.layout.fragment_add_room,
                R.layout.fragment_add_tenant,
                R.layout.fragment_billing,
                R.layout.fragment_billing_defaults,
                R.layout.fragment_buildings,
                R.layout.fragment_dashboard,
                R.layout.fragment_edit_room,
                R.layout.fragment_foundation_tools,
                R.layout.fragment_invoice_detail,
                R.layout.fragment_invoice_preview,
                R.layout.fragment_meter_reading,
                R.layout.fragment_more,
                R.layout.fragment_move_in,
                R.layout.fragment_move_out,
                R.layout.fragment_payment_qr,
                R.layout.fragment_payment_setup,
                R.layout.fragment_payments,
                R.layout.fragment_reminder,
                R.layout.fragment_replacement_invoice,
                R.layout.fragment_reports,
                R.layout.fragment_room_detail,
                R.layout.fragment_rooms,
                R.layout.fragment_setting,
                R.layout.fragment_startup,
                R.layout.fragment_tenant_details,
                R.layout.fragment_tenant_list,
                R.layout.fragment_welcome,
                R.layout.item_billing_invoice,
                R.layout.item_building_card,
                R.layout.item_building_filter,
                R.layout.item_dashboard_attention,
                R.layout.item_dashboard_attention_modern,
                R.layout.item_dialog_review_section,
                R.layout.item_dropdown_room_option,
                R.layout.item_meter_history,
                R.layout.item_payment_history,
                R.layout.item_payment_outstanding,
                R.layout.item_payment_record,
                R.layout.item_reminder_invoice,
                R.layout.item_room_occupied,
                R.layout.item_room_vacant,
                R.layout.item_rooms_header,
                R.layout.item_settings_row,
                R.layout.item_summary_row,
                R.layout.item_tenancy_history,
                R.layout.item_tenant_card
            )
            for (layout in layouts) {
                val name = activity.resources.getResourceEntryName(layout)
                try {
                    assertNotNull(name, activity.layoutInflater.inflate(layout, FrameLayout(activity), false))
                } catch (error: Exception) {
                    throw AssertionError("Unable to inflate $name with Theme.RoomKit", error)
                }
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
