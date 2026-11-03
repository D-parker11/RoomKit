package com.example.roomkit.Date.Repositories

import androidx.room.withTransaction
import com.example.roomkit.Date.Local.Dao.InvoiceDao
import com.example.roomkit.Date.Local.Dao.MeterReadingDao
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Model.RoomDetailUiState
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.example.roomkit.data.local.dao.TenantDao
import com.roomkit.data.local.AppDatabase
import com.roomkit.data.local.dao.BuildingDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class RoomDetailRepository(
    private val db: AppDatabase,
    private val roomDao: RoomDao,
    private val buildingDao: BuildingDao,
    private val tenancyDao: TenancyDao,
    private val tenantDao: TenantDao,
    private val meterReadingDao: MeterReadingDao,
    private val invoiceDao: InvoiceDao
) {

    fun observeRoomDetail(
        roomId: Long,
        month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
        year: Int = Calendar.getInstance().get(Calendar.YEAR)
    ): Flow<RoomDetailUiState?> {
        require(roomId > 0L) { "Invalid room ID." }
        require(month in 1..12) { "Invalid billing month." }
        require(year > 0) { "Invalid billing year." }

        val periodLabel = createPeriodLabel(month, year)

        return combine(
            roomDao.observeRoomById(roomId),
            tenancyDao.observeActiveTenancyForRoom(roomId)
        ) { room, tenancy ->
            room to tenancy
        }.flatMapLatest { (room, tenancy) ->
            if (room == null) {
                return@flatMapLatest flowOf<RoomDetailUiState?>(null)
            }

            val tenantFlow = tenancy?.let {
                tenantDao.observeTenantById(it.tenantId)
            } ?: flowOf(null)

            val invoiceFlow = tenancy?.let {
                invoiceDao.observeInvoiceForMonth(
                    tenancyId = it.tenancyId,
                    month = month,
                    year = year
                )
            } ?: flowOf(null)

            combine(
                buildingDao.observeBuildingById(room.buildingId),
                tenantFlow,
                meterReadingDao.observeReadingForPeriod(
                    roomId,
                    "ELECTRICITY",
                    month,
                    year
                ),
                meterReadingDao.observeReadingForPeriod(
                    roomId,
                    "WATER",
                    month,
                    year
                ),
                invoiceFlow
            ) { building, tenant, electricity, water, invoice ->

                val waterIsFixed = tenancy?.waterBillingMethod
                    .equals("FIXED", ignoreCase = true)

                val monthlyRent = tenancy?.monthlyRent
                    ?: room.monthlyRent

                RoomDetailUiState(
                    roomId = room.roomId,
                    roomNumber = room.roomNumber,
                    buildingId = room.buildingId,
                    buildingName = building?.name ?: "Building",

                    isOccupied = tenancy != null,
                    monthlyRent = monthlyRent,

                    tenancyId = tenancy?.tenancyId,
                    tenantId = tenancy?.tenantId,
                    tenantName = tenant?.fullName,
                    tenantPhone = tenant?.phoneNumber,
                    moveInDate = tenancy?.moveInDate,
                    deposit = tenancy?.deposit,

                    periodLabel = periodLabel,
                    billingMonth = month,
                    billingYear = year,

                    electricityPrevious = electricity?.previousReading,
                    electricityCurrent = electricity?.currentReading,
                    electricityUsage = calculateUsage(electricity),

                    waterIsFixed = waterIsFixed,
                    waterPrevious =
                        if (waterIsFixed) null else water?.previousReading,
                    waterCurrent =
                        if (waterIsFixed) null else water?.currentReading,
                    waterUsage =
                        if (waterIsFixed) null else calculateUsage(water),

                    invoiceId = invoice?.invoiceId,
                    invoiceStatus = invoice?.status,
                    rentCharge = invoice?.rentKhr,

                    electricityCharge = invoice?.electricityChargeKhr,

                    // When no invoice exists, this also supplies the
                    // configured fixed fee for the water reading card.
                    waterCharge = invoice?.waterChargeKhr
                        ?: if (waterIsFixed) tenancy?.waterRate else null,

                    rubbishCharge = invoice?.rubbishFeeKhrSnapshot,

                    // Only display an actual saved invoice total/date.
                    totalInvoiceAmount = invoice?.totalKhr,
                    invoiceDueDate = invoice?.dueDate
                )
            }
        }
    }

    suspend fun recordMeterReadings(
        roomId: Long,
        month: Int,
        year: Int,
        newElectric: Long,
        newWater: Long
    ): Result<Unit> {
        return try {
            require(roomId > 0L) { "Invalid room ID." }
            require(month in 1..12) { "Invalid billing month." }
            require(year > 0) { "Invalid billing year." }

            db.withTransaction {
                val room = roomDao.getRoomById(roomId)
                    ?: throw IllegalArgumentException("Room not found.")

                val tenancy = tenancyDao.getActiveTenancyForRoom(roomId)
                    ?: throw IllegalStateException(
                        "Assign a tenant before recording billing readings."
                    )

                val moveInCalendar = Calendar.getInstance().apply {
                    timeInMillis = tenancy.moveInDate
                }

                val moveInPeriod =
                    moveInCalendar.get(Calendar.YEAR).toLong() * 12 +
                            moveInCalendar.get(Calendar.MONTH)

                val selectedPeriod =
                    year.toLong() * 12 + month - 1

                require(selectedPeriod >= moveInPeriod) {
                    "Billing readings cannot be recorded before " +
                            "the tenant's move-in month."
                }

                val existingInvoice = invoiceDao.getInvoiceForMonth(
                    tenancy.tenancyId,
                    month,
                    year
                )

                check(existingInvoice == null) {
                    "An invoice already exists for this period. " +
                            "Use the invoice correction workflow."
                }

                val timestamp = System.currentTimeMillis()

                insertPeriodReading(
                    roomId = roomId,
                    meterType = "ELECTRICITY",
                    month = month,
                    year = year,
                    currentReading = newElectric,
                    initialReading = room.initialElectricityReading,
                    timestamp = timestamp
                )

                if (!tenancy.waterBillingMethod.equals("FIXED", true)) {
                    insertPeriodReading(
                        roomId = roomId,
                        meterType = "WATER",
                        month = month,
                        year = year,
                        currentReading = newWater,
                        initialReading = room.initialWaterReading,
                        timestamp = timestamp
                    )
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertPeriodReading(
        roomId: Long,
        meterType: String,
        month: Int,
        year: Int,
        currentReading: Long,
        initialReading: Double?,
        timestamp: Long
    ) {
        val label = if (meterType == "ELECTRICITY") {
            "Electricity"
        } else {
            "Water"
        }

        require(currentReading >= 0L) {
            "$label reading cannot be negative."
        }

        val existing = meterReadingDao.getReadingForPeriod(
            roomId,
            meterType,
            month,
            year
        )

        check(existing == null) {
            "$label readings already exist for this period. " +
                    "Edit the existing readings instead."
        }

        check(
            !meterReadingDao.hasReadingAfterPeriod(
                roomId,
                meterType,
                month,
                year
            )
        ) {
            "A later $label reading exists. " +
                    "Historical readings need a correction workflow."
        }

        val tenancyId = tenancyDao.getActiveTenancyForRoom(roomId)?.tenancyId ?: error("No active tenancy")
        check(meterReadingDao.unassignedCount(roomId) == 0) { "Resolve unassigned legacy readings first." }
        val history = meterReadingDao.getForTenancy(tenancyId).filter { it.meterType == meterType }
        check(history.none { it.billingYear * 12 + it.billingMonth > year * 12 + month }) { "Later meter history exists." }
        val basis = MeterHistory.monthly(history, month, year, initialWholeReading(initialReading, label))
        val previousValue = basis.previous
        basis.usage(currentReading)

        require(currentReading >= previousValue) {
            "$label reading ($currentReading) cannot be lower " +
                    "than the previous reading ($previousValue)."
        }

        meterReadingDao.insertReading(
            MeterReadingEntity(
                tenancyId = tenancyDao.getActiveTenancyForRoom(roomId)?.tenancyId ?: error("No active tenancy"),
                roomId = roomId,
                meterType = meterType,
                previousReading = previousValue, carriedUsage=basis.carried, meterSequence=basis.sequence,
                currentReading = currentReading,
                readingDate = timestamp,
                billingMonth = month,
                billingYear = year
            )
        )
    }

    private fun initialWholeReading(
        value: Double?,
        label: String
    ): Long {
        if (value == null) return 0L

        require(value.isFinite() && value >= 0.0) {
            "$label initial reading is invalid."
        }

        return try {
            BigDecimal.valueOf(value).longValueExact()
        } catch (e: ArithmeticException) {
            throw IllegalArgumentException(
                "$label initial reading must be a whole number " +
                        "for the current meter-reading schema."
            )
        }
    }

    private fun calculateUsage(
        reading: MeterReadingEntity?
    ): Long? {
        if (reading == null) return null

        return if (reading.currentReading >= reading.previousReading) {
            reading.usage
        } else {
            null
        }
    }

    private fun createPeriodLabel(
        month: Int,
        year: Int
    ): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }

        return SimpleDateFormat("MMM yyyy", Locale.US)
            .format(calendar.time)
    }
}