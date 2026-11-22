package com.example.roomkit

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import com.example.roomkit.Date.Ui.Reminders.RemindersViewModel
import com.example.roomkit.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val remindersViewModel: RemindersViewModel by viewModels()

    private var pendingReminderInvoiceId: Long? = null
    private var pendingNavigationTab: Int? = null
    private var permissionRequested = false

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                reconcileReminders()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupNavigation()
        observeReminderBadge()

        pendingReminderInvoiceId = savedInstanceState
            ?.getLong(KEY_PENDING_REMINDER_INVOICE_ID, 0L)
            ?.takeIf { it > 0L }

        permissionRequested = savedInstanceState?.getBoolean("notificationPermissionRequested") ?: false
        consumeReminderIntent(intent)
    }

    private fun observeReminderBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                remindersViewModel.uiState.collect { state ->
                    if (state.loading) return@collect
                    val urgentCount = state.overdueCount + state.todayCount
                    if (urgentCount > 0) {
                        binding.bottomNav.getOrCreateBadge(R.id.moreFragment).apply {
                            isVisible = true
                            number = urgentCount
                            backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.rr_red)
                            badgeTextColor = Color.WHITE
                        }
                    } else {
                        binding.bottomNav.removeBadge(R.id.moreFragment)
                    }
                }
            }
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
        navController.addOnDestinationChangedListener { controller, destination, _ ->

            binding.bottomNav.visibility =
                if (destination.id in setOf(R.id.moveOutFragment, R.id.startupFragment, R.id.welcomeFragment)) {
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
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            openBottomTab(item.itemId)
        }

        binding.bottomNav.setOnItemReselectedListener { item ->
            openBottomTab(item.itemId)
        }
    }

    private fun openBottomTab(tabId: Int): Boolean {
        if (supportFragmentManager.isStateSaved || isOpeningScreen()) return false

        val currentEntry = navController.currentBackStackEntry

        if (currentEntry?.destination?.id == tabId) {
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

        intent.removeExtra(InvoiceReminderNotifications.EXTRA_INVOICE_ID)
        intent.action = null
        intent.data = null
    }

    override fun onPostResume() {
        super.onPostResume()

        reconcileReminders()
        remindersViewModel.refresh()
        openPendingReminderInvoice()
        if (!isOpeningScreen()) requestNotificationPermissionIfNeeded()
    }

    private fun isOpeningScreen(): Boolean = navController.currentDestination?.id in
            setOf(R.id.startupFragment, R.id.welcomeFragment)

    // Called only after Room has supplied an authoritative property count.
    fun finishStartup(hasProperty: Boolean) {
        if (supportFragmentManager.isStateSaved ||
            navController.currentDestination?.id != R.id.startupFragment) return
        navController.navigate(
            if (hasProperty) R.id.dashboardFragment else R.id.welcomeFragment,
            null,
            NavOptions.Builder().setPopUpTo(R.id.startupFragment, true).build()
        )
        if (hasProperty) {
            openPendingReminderInvoice()
            requestNotificationPermissionIfNeeded()
        }
    }

    fun finishPropertySetup() {
        if (supportFragmentManager.isStateSaved ||
            navController.currentDestination?.id != R.id.welcomeFragment) return
        navController.navigate(
            R.id.dashboardFragment, null,
            NavOptions.Builder().setPopUpTo(R.id.welcomeFragment, true).build()
        )
        openPendingReminderInvoice()
        requestNotificationPermissionIfNeeded()
    }

    private fun openPendingReminderInvoice() {
        val invoiceId = pendingReminderInvoiceId ?: return
        if (supportFragmentManager.isStateSaved || isOpeningScreen()) return

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

        outState.putBoolean("notificationPermissionRequested", permissionRequested)
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
        if (permissionRequested || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        permissionRequested = true

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
