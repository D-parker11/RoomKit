@file:Suppress("PackageNaming")
package com.example.roomkit.Date.Repositories

import com.example.roomkit.Date.Reminders.InvoiceReminderScheduler
import android.content.Context
import com.example.roomkit.Date.Billing.BillingPeriod
import com.example.roomkit.Date.Local.Entity.BillingDraftAdjustmentEntity
import android.net.Uri
import androidx.room.withTransaction
import com.example.roomkit.Date.Billing.BillingCalculator
import com.example.roomkit.Date.Billing.CurrencyFormatter
import com.example.roomkit.Date.Billing.InvoicePdfGenerator
import com.example.roomkit.Date.Billing.InvoicePdfModel
import com.example.roomkit.Date.Billing.PaymentPdfItem
import com.example.roomkit.Date.Local.Dao.InvoiceDao
import com.example.roomkit.Date.Local.Dao.MeterReadingDao
import com.example.roomkit.Date.Local.Dao.PaymentDao
import com.example.roomkit.Date.Local.Entity.BuildingEntity
import com.example.roomkit.Date.Local.Entity.InvoiceEntity
import com.example.roomkit.Date.Local.Entity.PaymentEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.Date.Local.Entity.TenantEntity
import com.example.roomkit.Date.Local.Model.InvoiceWithDetails
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.example.roomkit.data.local.dao.TenantDao
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.local.dao.BuildingDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class BillingRawData(
    val buildings: List<BuildingEntity>,
    val rooms: List<RoomEntity>,
    val tenancies: List<TenancyEntity>,
    val tenants: List<TenantEntity>,
    val invoices: List<InvoiceEntity>,
    val payments: List<PaymentEntity>
)

class BillingRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val buildingDao: BuildingDao,
    private val invoiceDao: InvoiceDao,
    private val paymentDao: PaymentDao,
    private val tenancyDao: TenancyDao,
    private val tenantDao: TenantDao,
    private val roomDao: RoomDao,
    private val meterReadingDao: MeterReadingDao
) {

    //    //**
//    * Reactive stream consumed by BillingViewModel for the Billing screen list & counters.
//    */
    @Suppress("UNCHECKED_CAST")
    fun observeBillingData(): Flow<BillingRawData> {
        return combine(
            buildingDao.observeAllBuildings(),
            roomDao.observeAllRooms(),
            tenancyDao.observeAllTenancies(),
            tenantDao.observeAllTenants(),
            invoiceDao.observeAllInvoices(),
            paymentDao.observeAllPayments()
        ) { args: Array<Any> ->
            BillingRawData(
                buildings = args[0] as List<BuildingEntity>,
                rooms = args[1] as List<RoomEntity>,
                tenancies = args[2] as List<TenancyEntity>,
                tenants = args[3] as List<TenantEntity>,
                invoices = args[4] as List<InvoiceEntity>,
                payments = args[5] as List<PaymentEntity>
            )
        }
    }

    data class BillingDraft(
        val room: RoomEntity,
        val tenancy: TenancyEntity,
        val tenantName: String,
        val billingMonth: Int,
        val billingYear: Int,
        val dueDate: Long,
        val result: BillingCalculator.BillingResult,
        val previousElectricity: Long,
        val currentElectricity: Long,
        val previousWater: Long,
        val currentWater: Long,
        val billingPeriodStart: String,
        val billingPeriodEnd: String,
        val electricityCarriedUsage: Long = 0L, val waterCarriedUsage: Long = 0L,
        val electricityMeterSequence: Int = 0, val waterMeterSequence: Int = 0
    )

    suspend fun prepareBillingDraft(roomId: Long, month: Int, year: Int): Result<BillingDraft> {
        return try {
            require(month in 1..12 && year > 0) { "Invalid billing period." }
            db.withTransaction { prepareBillingDraftInternal(roomId, month, year) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun prepareBillingDraftInternal(roomId: Long, month: Int, year: Int): Result<BillingDraft> {
        val room = roomDao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Room not found."))

        val tenancy = tenancyDao.getActiveTenancyForRoom(roomId)
            ?: return Result.failure(IllegalStateException("No active tenancy for this room."))

        // Reject duplicate invoice
        val existing = invoiceDao.getInvoiceForMonth(tenancy.tenancyId, month, year)
        if (existing != null) {
            return Result.failure(IllegalStateException("An invoice already exists for this room for $month/$year."))
        }

        val tenant = tenantDao.getTenantById(tenancy.tenantId)
        val tenantName = tenant?.fullName ?: "Tenant"

        // Load Meter Readings recorded for this month
        val elecReading = meterReadingDao.getReadingForMonth(roomId, "ELECTRICITY", month, year)
            ?: return Result.failure(IllegalStateException("Electricity reading is missing for this month. Please record meter first."))

        val isWaterMeter = !tenancy.waterBillingMethod.equals("FIXED", ignoreCase = true)
        val waterReading = if (isWaterMeter) {
            meterReadingDao.getReadingForMonth(roomId, "WATER", month, year)
                ?: return Result.failure(IllegalStateException("Water meter reading is missing for this month. Please record meter first."))
        } else null

        val saved = db.billingDraftAdjustmentDao().get(tenancy.tenancyId, month, year)
        val monthPeriod = BillingPeriod.month(month, year)
        // tenancy.monthlyRent is ALREADY in riel. Do not multiply by 100!
        val input = BillingCalculator.BillingInput(
            rentKhr = tenancy.monthlyRent,
            previousElectricity = elecReading.previousReading,
            currentElectricity = elecReading.currentReading,
            electricityRateKhr = tenancy.electricityRate,
            isWaterMeter = isWaterMeter,
            previousWater = waterReading?.previousReading ?: 0L,
            currentWater = waterReading?.currentReading ?: 0L,
            waterRateKhr = tenancy.waterRate,
            fixedWaterFeeKhr = tenancy.waterRate,
            rubbishFeeKhr = tenancy.rubbishFee,
            otherChargesKhr = saved?.otherChargesKhr ?: 0L,
            discountKhr = saved?.discountKhr ?: 0L,
            electricityCarriedUsage = elecReading.carriedUsage, waterCarriedUsage = waterReading?.carriedUsage ?: 0L
        )

        val calculation = BillingCalculator.calculate(input)

        // Calculate due date (tenancy dueDay of next month)
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, tenancy.dueDay.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        return Result.success(
            BillingDraft(
                room = room,
                tenancy = tenancy,
                tenantName = tenantName,
                billingMonth = month,
                billingYear = year,
                dueDate = cal.timeInMillis,
                result = calculation,
                previousElectricity = elecReading.previousReading,
                currentElectricity = elecReading.currentReading,
                previousWater = waterReading?.previousReading ?: 0L,
                currentWater = waterReading?.currentReading ?: 0L,
                billingPeriodStart = saved?.billingPeriodStart ?: monthPeriod.first,
                billingPeriodEnd = saved?.billingPeriodEnd ?: monthPeriod.second,
                electricityCarriedUsage = elecReading.carriedUsage, waterCarriedUsage = waterReading?.carriedUsage ?: 0L,
                electricityMeterSequence = elecReading.meterSequence, waterMeterSequence = waterReading?.meterSequence ?: 0
            )
        )
    }

    /** Recalculate only user-editable adjustments; all meter/rate fields remain from the preview. */
    fun adjustDraft(draft: BillingDraft, other: Long, discount: Long): BillingDraft {
        val result = BillingCalculator.calculate(BillingCalculator.BillingInput(
            rentKhr = draft.tenancy.monthlyRent,
            previousElectricity = draft.previousElectricity,
            currentElectricity = draft.currentElectricity,
            electricityRateKhr = draft.tenancy.electricityRate,
            isWaterMeter = !draft.tenancy.waterBillingMethod.equals("FIXED", true),
            previousWater = draft.previousWater, currentWater = draft.currentWater,
            waterRateKhr = draft.tenancy.waterRate, fixedWaterFeeKhr = draft.tenancy.waterRate,
            rubbishFeeKhr = draft.tenancy.rubbishFee,
            otherChargesKhr = other, discountKhr = discount,
            electricityCarriedUsage = draft.electricityCarriedUsage, waterCarriedUsage = draft.waterCarriedUsage
        ))
        return draft.copy(result = result)
    }

    suspend fun saveDraftAdjustments(expected: BillingDraft, other: Long, discount: Long): Result<BillingDraft> {
        return try {
            val saved = db.withTransaction {
                val current = prepareBillingDraftInternal(expected.room.roomId,
                    expected.billingMonth, expected.billingYear).getOrThrow()
                check(current == expected) { "Billing details changed. Reopen the preview before saving." }
                val edited = adjustDraft(current, other, discount)
                db.billingDraftAdjustmentDao().save(BillingDraftAdjustmentEntity(
                    tenancyId = edited.tenancy.tenancyId,
                    billingMonth = edited.billingMonth, billingYear = edited.billingYear,
                    otherChargesKhr = other, discountKhr = discount,
                    billingPeriodStart = edited.billingPeriodStart,
                    billingPeriodEnd = edited.billingPeriodEnd
                ))
                edited
            }
            Result.success(saved)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    /** Existing callers still issue atomically through a DRAFT row with the same ID. */
    suspend fun issueInvoice(draft: BillingDraft): Result<Long> =
        persistRegularDraft(draft, draft.result.otherChargesKhr, draft.result.discountKhr, true)

    suspend fun findSavedDraft(roomId: Long, month: Int, year: Int): Long? = db.withTransaction {
        val tenancy = tenancyDao.getActiveTenancyForRoom(roomId) ?: return@withTransaction null
        invoiceDao.getInvoiceForMonth(tenancy.tenancyId, month, year)
            ?.takeIf { it.status.trim().equals("DRAFT", true) }?.invoiceId
    }

    suspend fun persistRegularDraft(expected: BillingDraft, other: Long, discount: Long, issue: Boolean): Result<Long> {
        return try {
            val invoiceId = db.withTransaction {
                val freshDraft = prepareBillingDraftInternal(
                    expected.room.roomId, expected.billingMonth, expected.billingYear
                ).getOrThrow()
                check(freshDraft == expected) {
                    "Billing details changed. Reload the preview before issuing."
                }
                val draft = adjustDraft(freshDraft, other, discount)
                val entity = InvoiceEntity(
                    tenancyId = draft.tenancy.tenancyId,
                    billingMonth = draft.billingMonth,
                    billingYear = draft.billingYear,
                    rentKhr = draft.tenancy.monthlyRent,
                    electricityPrevious = draft.previousElectricity,
                    electricityCurrent = draft.currentElectricity,
                    electricityUsage = draft.result.electricityUsage,
                    electricityRateKhrSnapshot = draft.tenancy.electricityRate,
                    electricityChargeKhr = draft.result.electricityChargeKhr,
                    waterBillingMethod = draft.tenancy.waterBillingMethod,
                    waterPrevious = if (draft.tenancy.waterBillingMethod.equals("FIXED", true)) null else draft.previousWater,
                    waterCurrent = if (draft.tenancy.waterBillingMethod.equals("FIXED", true)) null else draft.currentWater,
                    waterUsage = draft.result.waterUsage,
                    waterRateKhrSnapshot = draft.tenancy.waterRate,
                    fixedWaterFeeKhrSnapshot = if (draft.tenancy.waterBillingMethod.equals("FIXED", true)) draft.tenancy.waterRate else null,
                    waterChargeKhr = draft.result.waterChargeKhr,
                    rubbishFeeKhrSnapshot = draft.tenancy.rubbishFee,
                    otherChargesKhr = draft.result.otherChargesKhr,
                    utilitySubtotalKhr = draft.result.utilitySubtotalKhr,
                    discountKhr = draft.result.discountKhr,
                    totalKhr = draft.result.totalKhr,
                    amountPaidKhr = 0L,
                    balanceKhr = 0L,
                    issueDate = 0L,
                    dueDate = draft.dueDate,
                    status = "DRAFT",
                    billingPeriodStart = draft.billingPeriodStart,
                    billingPeriodEnd = draft.billingPeriodEnd,
                    electricityCarriedUsage = draft.electricityCarriedUsage, waterCarriedUsage = draft.waterCarriedUsage,
                    electricityMeterSequence = draft.electricityMeterSequence, waterMeterSequence = draft.waterMeterSequence
                )
                val newId = invoiceDao.insertInvoice(entity)
                db.writeDraftItems(entity.copy(invoiceId = newId))
                if (issue) invoiceDao.updateInvoice(entity.copy(
                    invoiceId = newId,
                    status = if (entity.totalKhr == 0L) "PAID" else "ISSUED",
                    balanceKhr = entity.totalKhr,
                    issueDate = System.currentTimeMillis()
                ))
                db.billingDraftAdjustmentDao().delete(draft.tenancy.tenancyId,
                    draft.billingMonth, draft.billingYear)

                // Automatically schedule reminders for this newly issued invoice
                newId
            }
            // Reminder failure must not report a committed invoice as unsaved.
            try {
                if (issue) {
                    val saved = invoiceDao.getInvoiceById(invoiceId)
                    if (saved != null && saved.balanceKhr > 0L) scheduleInvoiceReminders(invoiceId, expected.room.roomNumber, saved.dueDate)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                android.util.Log.w("BillingRepository", "Unable to schedule invoice reminder", e)
            }
            Result.success(invoiceId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("unused")
    fun observeInvoice(invoiceId: Long): Flow<InvoiceEntity?> = invoiceDao.observeInvoiceById(invoiceId)

    fun observeInvoiceDetailWithItems(invoiceId: Long) = invoiceDao.observeInvoiceDetailWithItems(invoiceId)

    fun observeInvoiceDetail(invoiceId: Long): Flow<InvoiceWithDetails?> {
        return invoiceDao.observeInvoiceDetailById(invoiceId)
    }

    fun observePayments(invoiceId: Long): Flow<List<PaymentEntity>> {
        return paymentDao.observePaymentsForInvoice(invoiceId)
    }

    suspend fun recordPayment(
        invoiceId: Long,
        amountKhr: Long,
        paymentDate: Long = System.currentTimeMillis(),
        method: String = "CASH",
        note: String? = null,
        referenceNumber: String? = null
    ): Result<Unit> {
        if (amountKhr <= 0L) {
            return Result.failure(IllegalArgumentException("Payment amount must be greater than 0៛."))
        }

        return try {
            val cleanReference = com.example.roomkit.Date.Billing.InvoiceFieldValidation.paymentReference(referenceNumber)
            val fullyPaid = db.withTransaction {
                val invoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: throw IllegalStateException("Invoice not found.")

                check(invoice.status.uppercase(Locale.US) in setOf(
                    "ISSUED", "PARTIALLY_PAID", "OVERDUE", "PAID"
                )) { "Payments can only be recorded for issued invoices." }
                if (invoice.status.uppercase(Locale.US) == "PAID") {
                    throw IllegalStateException("This invoice is already fully paid.")
                }

                val remainingBalance = (invoice.totalKhr - paymentDao.getTotalPaidForInvoice(invoiceId)).coerceAtLeast(0L)
                if (amountKhr > remainingBalance) {
                    throw IllegalArgumentException(
                        "Payment cannot exceed remaining balance of ${CurrencyFormatter.formatKhr(remainingBalance)}."
                    )
                }

                // Insert payment record
                paymentDao.insertPayment(
                    PaymentEntity(
                        invoiceId = invoiceId,
                        amountKhr = amountKhr,
                        paymentDate = paymentDate,
                        paymentMethod = method,
                        note = note,
                        referenceNumber = cleanReference
                    )
                )

                // Transactionally recalculate totalPaid and balance
                val totalPaid = paymentDao.getTotalPaidForInvoice(invoiceId)
                val newBalance = (invoice.totalKhr - totalPaid).coerceAtLeast(0L)

                val newStatus = BillingCalculator.resolveInvoiceStatus(
                    totalKhr = invoice.totalKhr,
                    amountPaidKhr = totalPaid,
                    dueDateTimestamp = invoice.dueDate
                )

                invoiceDao.updatePaymentBalances(invoiceId, totalPaid, newBalance, newStatus)

                newBalance == 0L
            }
            // Notification side effects happen only after the payment commits.
            if (fullyPaid) {
                try {
                    cancelInvoiceReminders(invoiceId)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    android.util.Log.w("BillingRepository", "Payment saved; reminder cancellation failed", e)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportInvoicePdf(invoiceId: Long): Result<Uri> {
        return try {
            val snapshot = invoiceDao.getInvoiceDetailWithItems(invoiceId) ?: error("Invoice not found.")
            val account = com.example.roomkit.Date.Billing.InvoiceAccountBalances.calculate(
                snapshot.detail.invoice, snapshot.accountInvoices)
            val payments = snapshot.accountInvoices.single { it.invoice.invoiceId == invoiceId }.payments
                .filter { it.status.trim().equals("ACTIVE", true) }
                .sortedWith(compareBy<PaymentEntity> { it.paymentDate }.thenBy { it.paymentId })
            require(snapshot.detail.invoice.currency == "KHR") { "Unsupported invoice currency." }
            val detail = snapshot.detail
            val inv = detail.invoice
            com.example.roomkit.Date.Billing.InvoiceItems.validate(inv, snapshot.items)

            val periodCal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.MONTH, inv.billingMonth - 1)
                set(Calendar.YEAR, inv.billingYear)
            }
            val periodFormat = SimpleDateFormat("MMM yyyy", Locale.US)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

            val paymentPdfItems = payments.map { p ->
                PaymentPdfItem(
                    paymentId = p.paymentId,
                    paymentDateFormatted = dateFormat.format(Date(p.paymentDate)),
                    method = p.paymentMethod,
                    note = p.note,
                    referenceNumber = p.referenceNumber,
                    amountKhr = p.amountKhr
                )
            }

            val model = InvoicePdfModel(
                currency = inv.currency,
                discountReason = inv.discountReason,
                items = snapshot.items,
                invoiceId = inv.invoiceId,
                invoiceNumberFormatted = String.format(Locale.US, "#INV-%04d", inv.invoiceId),
                billingPeriodFormatted = periodFormat.format(periodCal.time),
                status = inv.status,
                issueDateFormatted = dateFormat.format(Date(inv.issueDate)),
                dueDateFormatted = dateFormat.format(Date(inv.dueDate)),
                tenantName = detail.tenantName,
                tenantPhone = detail.tenantPhone,
                buildingName = detail.buildingName,
                roomNumber = detail.roomNumber,
                rentKhr = inv.rentKhr,
                electricityUsage = inv.electricityUsage,
                electricityRateKhr = inv.electricityRateKhrSnapshot,
                electricityChargeKhr = inv.electricityChargeKhr,
                isWaterMetered = !inv.waterBillingMethod.equals("FIXED", ignoreCase = true),
                waterUsage = inv.waterUsage,
                waterRateKhr = inv.waterRateKhrSnapshot,
                waterChargeKhr = inv.waterChargeKhr,
                rubbishFeeKhr = inv.rubbishFeeKhrSnapshot,
                utilitySubtotalKhr = inv.utilitySubtotalKhr,
                totalKhr = inv.totalKhr,
                amountPaidKhr = account.currentPaidKhr,
                balanceKhr = account.currentInvoiceBalanceKhr,
                accountBalance = account,
                payments = paymentPdfItems,
                otherChargesKhr = inv.otherChargesKhr,
                discountKhr = inv.discountKhr,
                billingPeriodDates = BillingPeriod.label(inv.billingPeriodStart, inv.billingPeriodEnd)
            )

            val uri = InvoicePdfGenerator.generateAndSavePdf(context, model)
            Result.success(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("unused")
    suspend fun refreshOverdueStatuses() {
        invoiceDao.markOverdueInvoices(System.currentTimeMillis())
    }

    @Suppress("UNUSED_PARAMETER")
    fun scheduleInvoiceReminders(invoiceId: Long, roomNumber: String, dueDate: Long) {
        InvoiceReminderScheduler.schedule(context, invoiceId, dueDate)
    }

    suspend fun voidPayment(paymentId: Long, reason: String): Result<Unit> {
        return try {
            val cleanReason = reason.trim()
            require(cleanReason.isNotEmpty()) { "Enter a reason for voiding this payment." }
            val reminder = db.withTransaction {
                val payment = paymentDao.getPaymentById(paymentId)
                    ?: error("Payment record not found.")
                check(payment.status == "ACTIVE") { "This payment has already been voided." }
                check(payment.paymentMethod != "DEPOSIT") {
                    "Deposit allocations belong to the saved move-out settlement and cannot be voided here."
                }
                val invoice = invoiceDao.getInvoiceById(payment.invoiceId) ?: error("Invoice not found.")
                check(invoice.status !in listOf("DRAFT", "VOID", "VOIDED")) {
                    "Only payments on issued invoices can be voided."
                }
                check(paymentDao.voidPayment(paymentId, cleanReason, System.currentTimeMillis()) == 1) {
                    "Payment changed. Reload and try again."
                }
                val paid = paymentDao.getTotalPaidForInvoice(invoice.invoiceId)
                val balance = invoice.totalKhr - paid
                check(balance >= 0L) { "Payment totals are inconsistent." }
                val status = BillingCalculator.resolveInvoiceStatus(invoice.totalKhr, paid, invoice.dueDate)
                invoiceDao.updatePaymentBalances(invoice.invoiceId, paid, balance, status)
                val tenancy = tenancyDao.getTenancyById(invoice.tenancyId)
                val room = tenancy?.let { roomDao.getRoomById(it.roomId) }
                Triple(invoice.invoiceId, room?.roomNumber ?: "Room", invoice.dueDate)
            }
            // Database commit is authoritative; reminder failure must not report a failed void.
            try {
                scheduleInvoiceReminders(reminder.first, reminder.second, reminder.third)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Existing workers read the latest database balance. */ }
            Result.success(Unit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
    }

    fun cancelInvoiceReminders(invoiceId: Long) {
        InvoiceReminderScheduler.cancel(context, invoiceId)
    }
    suspend fun voidInvoice(
        invoiceId: Long,
        reason: String
    ): Result<Unit> {
        return try {
            val cleanReason = reason.trim()

            require(invoiceId > 0L) {
                "Invalid invoice."
            }

            require(cleanReason.isNotBlank()) {
                "Enter a reason for voiding this invoice."
            }

            db.withTransaction {
                val invoice = invoiceDao.getInvoiceById(invoiceId)
                    ?: error("Invoice not found.")

                check(
                    invoice.status in setOf(
                        "ISSUED",
                        "UNPAID",
                        "OVERDUE"
                    )
                ) {
                    "Only issued, unpaid invoices can be voided."
                }

                check(!invoiceDao.hasActivePayments(invoiceId)) {
                    "This invoice has active payments and cannot be voided."
                }

                check(invoice.amountPaidKhr == 0L) {
                    "The invoice payment balance is inconsistent. " +
                            "Check its payment history before voiding."
                }

                check(
                    !invoiceDao.isLinkedToMoveOutSettlement(invoiceId)
                ) {
                    "This invoice belongs to a saved move-out settlement " +
                            "and cannot be voided here."
                }

                check(
                    invoiceDao.voidUnpaidInvoice(
                        invoiceId = invoiceId,
                        reason = cleanReason,
                        voidedAt = System.currentTimeMillis()
                    ) == 1
                ) {
                    "The invoice changed. Reload it and try again."
                }
            }

            // The database change has already succeeded.
            try {
                cancelInvoiceReminders(invoiceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(
                    "BillingRepository",
                    "Unable to cancel invoice reminders",
                    e
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
