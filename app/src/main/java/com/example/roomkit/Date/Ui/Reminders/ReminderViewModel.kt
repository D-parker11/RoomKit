package com.example.roomkit.Date.Ui.Reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomkit.Date.Reminders.InvoiceReminderNotifications
import com.example.roomkit.Date.Reminders.InvoiceReminderStore
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

class RemindersViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository = RemindersRepository(AppDatabase.getInstance(context))
    private val selected = MutableStateFlow(ReminderFilter.OVERDUE)
    private val refresh = MutableStateFlow(0L)
    private val clock = flow {
        while (true) { emit(System.currentTimeMillis()); delay(15_000L) }
    }
    private val time = combine(clock, refresh) { _, _ -> System.currentTimeMillis() }

    // Retry creates a new subscription after an error instead of leaving a dead collector.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RemindersUiState> = refresh.flatMapLatest {
        combine(repository.observe(), selected, time) { invoices, filter, now ->
            val store = InvoiceReminderStore.get(context)
            val all = invoices.mapNotNull { invoice ->
                ReminderScreenLogic.card(invoice, now) { key ->
                    store.hasClaim(invoice.invoiceId, invoice.dueDate, key)
                }
            }.sortedWith(compareBy<ReminderCard> { it.invoice.dueDate }.thenBy { it.invoice.invoiceId })
            RemindersUiState(
                loading = false, filter = filter,
                overdueCount = all.count { it.filter == ReminderFilter.OVERDUE },
                todayCount = all.count { it.filter == ReminderFilter.TODAY },
                upcomingCount = all.count { it.filter == ReminderFilter.UPCOMING },
                notificationsEnabled = InvoiceReminderNotifications.canNotify(context),
                cards = all.filter { it.filter == filter }
            )
        }.catch { error ->
            if (error is CancellationException) throw error
            android.util.Log.w("RemindersScreen", "Unable to load reminders", error)
            emit(RemindersUiState(loading = false, filter = selected.value,
                error = "Could not load reminders. Please try again."))
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemindersUiState())

    fun select(filter: ReminderFilter) { selected.value = filter }
    fun refresh() { refresh.value += 1L }
}
