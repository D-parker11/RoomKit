package com.example.roomkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var pendingReminderInvoiceId: Long? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) reconcileReminders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.visibility = if (destination.id == R.id.moveOutFragment)
                android.view.View.GONE else android.view.View.VISIBLE
            when (destination.id) {
                R.id.buildingsFragment, R.id.roomsFragment ->
                    binding.bottomNav.menu.findItem(R.id.buildingsFragment)?.isChecked = true
                R.id.invoiceDetailFragment ->
                    binding.bottomNav.menu.findItem(R.id.billingFragment)?.isChecked = true
            }
        }
        pendingReminderInvoiceId = savedInstanceState?.getLong("pendingReminderInvoiceId", 0L)
            ?.takeIf { it > 0L }
        consumeReminderIntent(intent)
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntent(intent)
        // Navigation is deferred to onPostResume so FragmentManager state is not saved.
    }

    private fun consumeReminderIntent(intent: Intent?) {
        if (intent?.action != InvoiceReminderNotifications.ACTION_OPEN) return
        val id = intent.getLongExtra(InvoiceReminderNotifications.EXTRA_INVOICE_ID, 0L)
        if (id > 0L) pendingReminderInvoiceId = id
        // Consume once: rotation must not reopen the invoice after the user goes back.
        intent.removeExtra(InvoiceReminderNotifications.EXTRA_INVOICE_ID)
        intent.action = null
        intent.data = null
    }

    override fun onPostResume() {
        super.onPostResume()
        reconcileReminders()
        val id = pendingReminderInvoiceId ?: return
        pendingReminderInvoiceId = null
        val entry = navController.currentBackStackEntry
        if (entry?.destination?.id == R.id.invoiceDetailFragment &&
            entry.arguments?.getLong("invoiceId") == id) return
        navController.navigate(
            R.id.invoiceDetailFragment,
            bundleOf("invoiceId" to id),
            NavOptions.Builder()
                .setPopUpTo(R.id.dashboardFragment, false)
                .setLaunchSingleTop(true)
                .build()
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingReminderInvoiceId?.let { outState.putLong("pendingReminderInvoiceId", it) }
        super.onSaveInstanceState(outState)
    }

    private fun reconcileReminders() {
        try {
            InvoiceReminderScheduler.start(applicationContext)
        } catch (e: Exception) {
            android.util.Log.w("RoomKit", "Unable to start invoice reminders", e)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
