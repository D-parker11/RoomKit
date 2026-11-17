package com.example.roomkit.Date.Billing

import android.content.Context

enum class InvoicePaperSize(val width: Int, val height: Int) {
    A4(595, 842), A5(420, 595);

    fun remember(context: Context) {
        context.applicationContext.getSharedPreferences("roomkit_document_options", Context.MODE_PRIVATE)
            .edit().putString("paper_size", name).apply()
    }

    companion object {
        fun selected(context: Context): InvoicePaperSize {
            val value = context.applicationContext.getSharedPreferences("roomkit_document_options", Context.MODE_PRIVATE)
                .getString("paper_size", "A4")
            return values().firstOrNull { it.name == value } ?: A4
        }
    }
}
