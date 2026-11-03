package com.example.roomkit.Date.Reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.roomkit.MainActivity

object InvoiceReminderNotifications {
    const val ACTION_OPEN = "com.example.roomkit.OPEN_REMINDER_INVOICE"
    const val EXTRA_INVOICE_ID = "reminderInvoiceId"
    private const val CHANNEL = "roomkit_invoice_reminders"

    private fun manager(context: Context) =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun canNotify(context: Context): Boolean {
        val nm = manager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(
                CHANNEL, "Invoice Reminders", NotificationManager.IMPORTANCE_HIGH
            ))
            if (nm.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, invoiceId: Long, title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN
            // Intent identity includes the full Long ID, not only extras or a truncated Int.
            data = Uri.parse("roomkit://reminder/invoice/$invoiceId")
            putExtra(EXTRA_INVOICE_ID, invoiceId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        // One tray entry per invoice. Later scheduled occurrences update that entry.
        manager(context).notify("invoice_$invoiceId", 0, notification)
    }

    fun cancel(context: Context, invoiceId: Long) {
        manager(context).cancel("invoice_$invoiceId", 0)
        manager(context).cancel(invoiceId.toInt()) // Remove a notification from the old worker.
    }
}
