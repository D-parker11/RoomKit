package com.example.roomkit.Date.Repositories

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import java.util.Locale
import com.example.roomkit.Date.Local.Dao.InvoiceDao
import com.example.roomkit.Date.Local.Dao.MeterReadingDao
import com.example.roomkit.Date.Local.Entity.MeterReadingEntity
import com.example.roomkit.Date.Local.Entity.RoomEntity
import com.example.roomkit.Date.Local.Entity.TenancyEntity
import com.example.roomkit.data.local.dao.RoomDao
import com.example.roomkit.data.local.dao.TenancyDao
import com.example.roomkit.data.local.dao.TenantDao
import com.roomkit.data.local.AppDatabase

class MeterReadingRepository(
    private val db: AppDatabase,
    private val roomDao: RoomDao,
    private val tenancyDao: TenancyDao,
    private val tenantDao: TenantDao,
    private val meterReadingDao: MeterReadingDao,
    private val invoiceDao: InvoiceDao
) {

    data class InitialMeterContext(
        val room: RoomEntity,
        val tenancy: TenancyEntity,
        val tenantName: String,
        val previousElectricity: Long,
        val existingCurrentElectricity: Long?,
        val previousWater: Long,
        val existingCurrentWater: Long?,
        val isLockedByInvoice: Boolean,
        val electricityBasis: MeterHistory.Basis = MeterHistory.Basis(previousElectricity),
        val waterBasis: MeterHistory.Basis = MeterHistory.Basis(previousWater),
        val history: List<MeterReadingEntity> = emptyList()
    )

    private fun locksMeterReadings(status: String?): Boolean =
        status?.trim()?.uppercase(Locale.ROOT) in setOf(
            "ISSUED", "UNPAID", "PARTIAL", "PARTIALLY_PAID", "PAID", "OVERDUE"
        )

    private suspend fun getActiveInvoiceStatus(tenancyId: Long, month: Int, year: Int): String? {
        return if (invoiceDao.hasIssuedInvoiceForPeriod(tenancyId, month, year)) "ISSUED" else null
    }

    suspend fun loadContext(roomId: Long, month: Int, year: Int): Result<InitialMeterContext> = try {
        MeterSaveValidation.period(month, year)
        db.withTransaction { loadContextInternal(roomId, month, year) }
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    private suspend fun loadContextInternal(roomId: Long, month: Int, year: Int): Result<InitialMeterContext> {
        val room = roomDao.getRoomById(roomId)
            ?: return Result.failure(IllegalStateException("Room not found."))

        val tenancy = tenancyDao.getActiveTenancyForRoom(roomId)
            ?: return Result.failure(IllegalStateException("No active tenancy for this room."))

        val tenant = tenantDao.getTenantById(tenancy.tenantId)
        val tenantName = tenant?.fullName ?: "Tenant"

        // Check if an official active invoice exists and locks this month
        // Saved monthly facts are immutable even before issue or after void.
        val invoiceStatus = getActiveInvoiceStatus(tenancy.tenancyId, month, year)
        val isLocked = locksMeterReadings(invoiceStatus)

        val history = meterReadingDao.getForTenancy(tenancy.tenancyId)
        check(meterReadingDao.unassignedCount(roomId) == 0) { "Resolve unassigned legacy meter readings before recording new readings." }
        val existingElec = meterReadingDao.getReadingForMonth(roomId, "ELECTRICITY", month, year)
        val existingWater = meterReadingDao.getReadingForMonth(roomId, "WATER", month, year)
        val e = MeterHistory.monthly(history.filter { it.meterType == "ELECTRICITY" }, month, year, room.initialElectricityReading?.toLong() ?: 0L)
        val w = MeterHistory.monthly(history.filter { it.meterType == "WATER" }, month, year, room.initialWaterReading?.toLong() ?: 0L)
        val prevElectricity = e.previous
        val prevWater = w.previous

        return Result.success(
            InitialMeterContext(
                room = room,
                tenancy = tenancy,
                tenantName = tenantName,
                previousElectricity = prevElectricity,
                existingCurrentElectricity = existingElec?.currentReading,
                previousWater = prevWater,
                existingCurrentWater = existingWater?.currentReading,
                isLockedByInvoice = isLocked || existingElec != null || existingWater != null ||
                    db.billingPeriodDao().get(year, month)?.status == "CLOSED", electricityBasis = e, waterBasis = w, history = history
            )
        )
    }

    suspend fun isReadingLocked(roomId: Long, month: Int, year: Int): Boolean {
        val tenancy = tenancyDao.getActiveTenancyForRoom(roomId) ?: return false
        val status = getActiveInvoiceStatus(tenancy.tenancyId, month, year)
        return locksMeterReadings(status)
    }

    suspend fun saveReadings(
        roomId: Long,
        month: Int,
        year: Int,
        prevElec: Long,
        currElec: Long,
        isWaterFixed: Boolean,
        prevWater: Long,
        currWater: Long,
        expectedContext: InitialMeterContext
    ): Result<Unit> {
        return try {
            MeterSaveValidation.period(month, year)
            db.withTransaction {
                val fresh = loadContextInternal(roomId, month, year).getOrThrow()
                MeterSaveValidation.unchanged(expectedContext, fresh)
                val tenancy = fresh.tenancy
                check(!fresh.isLockedByInvoice) {
                    "This month is locked or its monthly readings have already been recorded."
                }
                val moveIn = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Phnom_Penh")).apply { timeInMillis = tenancy.moveInDate }
                require(year * 12 + month - 1 >= moveIn.get(java.util.Calendar.YEAR) * 12 + moveIn.get(java.util.Calendar.MONTH)) {
                    "The billing period cannot be before this tenancy began."
                }
                check(isWaterFixed == tenancy.waterBillingMethod.equals("FIXED", true)) {
                    "Water billing changed. Reopen the meter screen."
                }
                MeterSaveValidation.reading(prevElec, currElec, fresh.previousElectricity)
                if (!isWaterFixed) MeterSaveValidation.reading(prevWater, currWater, fresh.previousWater)
                MeterSaveValidation.noLaterReading(meterReadingDao.hasReadingAfterPeriod(roomId, "ELECTRICITY", month, year))
                if (!isWaterFixed) MeterSaveValidation.noLaterReading(meterReadingDao.hasReadingAfterPeriod(roomId, "WATER", month, year))

                check(fresh.history.none { it.billingYear * 12 + it.billingMonth > year * 12 + month }) { "Later meter history exists. Reopen the current billing period." }
                fresh.electricityBasis.usage(currElec)
                if (!isWaterFixed) fresh.waterBasis.usage(currWater)
                // Electricity
                val existingElec = meterReadingDao.getReadingForMonth(roomId, "ELECTRICITY", month, year)
                if (existingElec != null) {
                    error("Recorded monthly electricity readings are immutable.")
                } else {
                    meterReadingDao.insertReading(
                        MeterReadingEntity(
                            tenancyId = tenancy.tenancyId,
                            roomId = roomId,
                            meterType = "ELECTRICITY",
                            previousReading = prevElec,
                            carriedUsage = fresh.electricityBasis.carried, meterSequence = fresh.electricityBasis.sequence,
                            currentReading = currElec,
                            readingDate = System.currentTimeMillis(),
                            billingMonth = month,
                            billingYear = year
                        )
                    )
                }

                // Water
                if (!isWaterFixed) {
                    val existingWater = meterReadingDao.getReadingForMonth(roomId, "WATER", month, year)
                    if (existingWater != null) {
                        error("Recorded monthly water readings are immutable.")
                    } else {
                        meterReadingDao.insertReading(
                            MeterReadingEntity(
                                tenancyId = tenancy.tenancyId,
                                roomId = roomId,
                                meterType = "WATER",
                                previousReading = prevWater,
                                carriedUsage = fresh.waterBasis.carried, meterSequence = fresh.waterBasis.sequence,
                                currentReading = currWater,
                                readingDate = System.currentTimeMillis(),
                                billingMonth = month,
                                billingYear = year
                            )
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun history(roomId: Long) = meterReadingDao.historyForRoom(roomId)

    /** Replacement is recorded now, during an active tenancy, without changing issued invoices. */
    suspend fun replaceMeter(roomId: Long, month: Int, year: Int, type: String,
                             closing: Long, opening: Long, reason: String, expected: InitialMeterContext): Result<Unit> = try {
        db.withTransaction {
            val fresh = loadContextInternal(roomId, month, year).getOrThrow()
            MeterSaveValidation.unchanged(expected, fresh)
            val now = System.currentTimeMillis()
            val date = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Phnom_Penh")).toLocalDate()
            check(date.monthValue == month && date.year == year) { "Reopen the meter screen for the current month." }
            check(now >= fresh.tenancy.moveInDate && fresh.history.none { it.readingDate > now }) { "Replacement cannot precede existing meter history or move-in." }
            require(type == "ELECTRICITY" || (type == "WATER" && !fresh.tenancy.waterBillingMethod.equals("FIXED", true))) { "Select a metered utility." }
            require(reason.trim().isNotEmpty() && reason.trim().length <= 500) { "Enter a replacement reason (maximum 500 characters)." }
            require(opening >= 0 && closing >= 0) { "Readings cannot be negative." }
            check(fresh.history.none { it.billingYear * 12 + it.billingMonth > year * 12 + month }) { "Later meter history exists." }
            check(invoiceDao.getForTenancy(fresh.tenancy.tenancyId).none {
                (it.billingYear * 12 + it.billingMonth > year * 12 + month && it.status.trim().uppercase(Locale.ROOT) !in setOf("VOID", "VOIDED")) ||
                        (it.billingYear * 12 + it.billingMonth == year * 12 + month && it.status.trim().uppercase(Locale.ROOT) == "DRAFT")
            }) { "Resolve this month’s draft or any later invoice before replacing the meter." }
            val rows = fresh.history.filter { it.meterType == type }
            val basis = MeterHistory.advance(if (type == "ELECTRICITY") fresh.electricityBasis else fresh.waterBasis, rows)
            MeterSaveValidation.replacement(closing, opening, MeterHistory.minimumCurrent(rows, basis))
            val sequence = Math.addExact(basis.sequence, 1)
            basis.usage(closing) // overflow validation
            meterReadingDao.insertReading(MeterReadingEntity(
                tenancyId=fresh.tenancy.tenancyId, roomId=roomId, meterType=type,
                previousReading=closing, currentReading=opening, readingDate=now,
                billingMonth=month, billingYear=year, readingType="METERREPLACEMENT",
                eventOrdinal=sequence, meterSequence=sequence, reason=reason.trim()))
            // Keep the saved monthly fact unchanged. Usage after its closing reading is
            // carried by replacement history into the next bill or final settlement.

        }
        Result.success(Unit)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }
}
