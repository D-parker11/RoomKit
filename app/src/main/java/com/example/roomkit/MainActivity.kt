package com.example.roomkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private var pendingReminderInvoiceId: Long? = null
    private var pendingNavigationTab: Int? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                reconcileReminders()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupNavigation()

        if (savedInstanceState == null &&
            intent?.action == Intent.ACTION_MAIN
        ) {
            openBottomTab(R.id.dashboardFragment)
        }

        pendingReminderInvoiceId = savedInstanceState
            ?.getLong(KEY_PENDING_REMINDER_INVOICE_ID, 0L)
            ?.takeIf { it > 0L }

        consumeReminderIntent(intent)
        requestNotificationPermissionIfNeeded()

        binding.root.post {
            val destination = navController.currentDestination

            val screen = when (destination?.id) {
                R.id.dashboardFragment -> "Dashboard"
                R.id.buildingsFragment -> "Property"
                else -> destination?.label?.toString() ?: "Unknown"
            }

            android.widget.Toast.makeText(
                this,
                "NAV CHECK\n" +
                        "Screen: $screen\n" +
                        "Restored: ${savedInstanceState != null}\n" +
                        "Action: ${intent?.action}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )

            insets
        }
    }

    private fun setupNavigation() {
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        navController = navHost.navController

        // Selection is managed by the screen's originating tab.
        // Do not also call setupWithNavController().
        navController.addOnDestinationChangedListener {
                controller, destination, _ ->

            binding.bottomNav.visibility =
                if (destination.id == R.id.moveOutFragment) {
                    View.GONE
                } else {
                    View.VISIBLE
                }

            val entry = controller.currentBackStackEntry
                ?: return@addOnDestinationChangedListener

            val savedTab = entry.savedStateHandle
                .get<Int>(KEY_ORIGIN_TAB)

            val previousTab = controller.previousBackStackEntry
                ?.savedStateHandle
                ?.get<Int>(KEY_ORIGIN_TAB)

            val originTab = pendingNavigationTab
                ?: savedTab
                ?: previousTab
                ?: defaultTabFor(destination.id)

            entry.savedStateHandle[KEY_ORIGIN_TAB] = originTab
            highlightTab(originTab)

// Add the diagnostic code here.
            Log.d(
                "RoomKitNav",
                "destinationId=${destination.id}, " +
                        "label=${destination.label}, " +
                        "pending=$pendingNavigationTab, " +
                        "saved=$savedTab, " +
                        "previous=$previousTab, " +
                        "chosen=$originTab, " +
                        "dashboardId=${R.id.dashboardFragment}, " +
                        "propertyId=${R.id.buildingsFragment}"
            )

            binding.bottomNav.post {
                val menu = binding.bottomNav.menu
                val checkedItems = (0 until menu.size())
                    .map { menu.getItem(it) }
                    .filter { it.isChecked }
                    .joinToString { "${it.title}=${it.itemId}" }

                Log.d("RoomKitNav", "checkedAfterNavigation=$checkedItems")
            }
        } // End of addOnDestinationChangedListener

        binding.bottomNav.setOnItemSelectedListener { item ->
            openBottomTab(item.itemId)
        }

        binding.bottomNav.setOnItemReselectedListener { item ->
            openBottomTab(item.itemId)
        }
    }

    private fun openBottomTab(tabId: Int): Boolean {
        if (supportFragmentManager.isStateSaved) return false

        val currentEntry = navController.currentBackStackEntry

        if (currentEntry?.destination?.id == tabId) {
            // A screen opened from Dashboard can become the selected
            // tab's root when the user explicitly taps that tab.
            currentEntry.savedStateHandle[KEY_ORIGIN_TAB] = tabId
            highlightTab(tabId)
            return true
        }

        navigateWithTab(tabId) {
            navController.navigate(
                tabId,
                null,
                rootNavigationOptions()
            )
        }

        return true
    }

    private fun highlightTab(tabId: Int) {
        // Checking a menu item updates its appearance without navigating.
        binding.bottomNav.menu.findItem(tabId)?.isChecked = true
    }

    private fun navigateWithTab(tabId: Int, navigate: () -> Unit) {
        pendingNavigationTab = tabId

        try {
            navigate()
        } finally {
            pendingNavigationTab = null
        }
    }

    private fun rootNavigationOptions(): NavOptions {
        return NavOptions.Builder()
            .setPopUpTo(R.id.dashboardFragment, false)
            .setLaunchSingleTop(true)
            .build()
    }

    private fun defaultTabFor(destinationId: Int): Int {
        return when (destinationId) {
            R.id.buildingsFragment,
            R.id.roomsFragment,
            R.id.roomDetailFragment,
            R.id.addRoomFragment,
            R.id.editRoomFragment,
            R.id.moveInFragment,
            R.id.meterReadingFragment,
            R.id.moveOutFragment -> R.id.buildingsFragment

            R.id.billingFragment,
            R.id.invoiceDetailFragment,
            R.id.invoicePreviewFragment,
            R.id.replacementInvoiceFragment,
            R.id.paymentQrFragment -> R.id.billingFragment

            R.id.paymentsFragment,
            R.id.recordPaymentBottomSheet -> R.id.paymentsFragment

            R.id.moreFragment,
            R.id.tenantListFragment,
            R.id.tenantDetailsFragment,
            R.id.addTenantFragment,
            R.id.editTenantFragment,
            R.id.remindersFragment,
            R.id.roomkitSettingsFragment,
            R.id.roomkitBillingDefaultsFragment,
            R.id.foundationToolsFragment,
            R.id.roomkitReportsFragment -> R.id.moreFragment

            else -> R.id.dashboardFragment
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeReminderIntent(intent)

        // Open the invoice in onPostResume(), after saved state is cleared.
    }

    private fun consumeReminderIntent(intent: Intent?) {
        if (intent?.action != InvoiceReminderNotifications.ACTION_OPEN) {
            return
        }

        val invoiceId = intent.getLongExtra(
            InvoiceReminderNotifications.EXTRA_INVOICE_ID,
            0L
        )

        if (invoiceId > 0L) {
            pendingReminderInvoiceId = invoiceId
        }

        // Consume once so rotation does not reopen the same notification.
        intent.removeExtra(InvoiceReminderNotifications.EXTRA_INVOICE_ID)
        intent.action = null
        intent.data = null
    }

    override fun onPostResume() {
        super.onPostResume()

        reconcileReminders()
        openPendingReminderInvoice()
    }

    private fun openPendingReminderInvoice() {
        val invoiceId = pendingReminderInvoiceId ?: return
        if (supportFragmentManager.isStateSaved) return

        val currentEntry = navController.currentBackStackEntry
        val alreadyShowingInvoice =
            currentEntry?.destination?.id == R.id.invoiceDetailFragment &&
                    currentEntry.arguments?.getLong(ARG_INVOICE_ID) == invoiceId

        if (alreadyShowingInvoice) {
            currentEntry?.savedStateHandle
                ?.set(KEY_ORIGIN_TAB, R.id.billingFragment)

            highlightTab(R.id.billingFragment)
            pendingReminderInvoiceId = null
            return
        }

        navigateWithTab(R.id.billingFragment) {
            navController.navigate(
                R.id.invoiceDetailFragment,
                bundleOf(ARG_INVOICE_ID to invoiceId),
                rootNavigationOptions()
            )
        }

        pendingReminderInvoiceId = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingReminderInvoiceId?.let { invoiceId ->
            outState.putLong(KEY_PENDING_REMINDER_INVOICE_ID, invoiceId)
        }

        super.onSaveInstanceState(outState)
    }

    private fun reconcileReminders() {
        try {
            InvoiceReminderScheduler.start(applicationContext)
        } catch (exception: Exception) {
            Log.w(TAG, "Unable to start invoice reminders", exception)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    private companion object {
        const val TAG = "RoomKit"
        const val KEY_ORIGIN_TAB = "roomkit_origin_tab"
        const val KEY_PENDING_REMINDER_INVOICE_ID = "pendingReminderInvoiceId"
        const val ARG_INVOICE_ID = "invoiceId"
    }
}