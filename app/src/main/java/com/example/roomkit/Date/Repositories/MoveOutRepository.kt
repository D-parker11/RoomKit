package com.example.roomkit.Date.Repositories

import androidx.room.withTransaction
import com.example.roomkit.Date.Local.Entity.*
import com.example.roomkit.Date.Billing.BillingCalculator
import com.roomkit.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

data class MoveOutInput(
    val date: Long, val electricity: Long, val water: Long?,
    val additionalRent: Long, val otherCharges: Long, val chargeReason: String,
    val applyDeposit: Long, val deductions: Long, val deductionReason: String
)
data class MoveOutSnapshot(
    val tenancy: TenancyEntity, val room: RoomEntity,
    val invoices: List<InvoiceEntity>, val payments: List<PaymentEntity>,
    val readings: List<MeterReadingEntity>
)
data class MoveOutReview(
    val snapshot: MoveOutSnapshot, val input: MoveOutInput,
    val electricityStart: Long, val waterStart: Long,
    val electricityCharge: Long, val waterCharge: Long,
    val newCharges: Long, val existingDebt: Long, val refund: Long, val debt: Long,
    val electricityBasis: MeterHistory.Basis = MeterHistory.Basis(electricityStart),
    val waterBasis: MeterHistory.Basis = MeterHistory.Basis(waterStart)
)

class MoveOutRepository(private val db: AppDatabase) {
    suspend fun snapshot(id: Long): MoveOutSnapshot {
        val tenancy = db.tenancyDao().getTenancyById(id) ?: error("Rental not found.")
        val room = db.roomDao().getRoomById(tenancy.roomId) ?: error("Room not found.")
        if (tenancy.status == "ACTIVE") {
            check(db.meterReadingDao().unassignedCount(room.roomId)==0) {
                "Legacy readings could not be assigned to a rental safely. Review these records before settlement; no data was deleted."
            }
        }
        return MoveOutSnapshot(tenancy, room,
            db.invoiceDao().getForTenancy(id), db.paymentDao().getForTenancy(id),
            db.meterReadingDao().getForTenancy(id))
    }

    fun settlementBasis(s: MoveOutSnapshot, type: String) = MeterHistory.settlementBasis(s, type)

    fun review(s: MoveOutSnapshot, input: MoveOutInput): MoveOutReview {
        val t = s.tenancy
        check(t.status == "ACTIVE") { "This rental has already ended." }
        require(input.date >= t.moveInDate && input.date <= System.currentTimeMillis()) {
            "Choose a move-out date between move-in and today."
        }
        require(listOf(input.additionalRent,input.otherCharges,input.applyDeposit,input.deductions).all { it >= 0 }) { "Amounts cannot be negative." }
        if (input.additionalRent > 0 || input.otherCharges > 0) require(input.chargeReason.isNotBlank()) { "Explain additional rent/fees and confirm they were not billed already." }
        if (input.deductions > 0) require(input.deductionReason.trim().let { reason ->
            reason.length >= 3 && reason.any(Char::isLetterOrDigit)
        }) { "Enter a meaningful reason for deposit deductions." }
        val official = s.invoices.filter { it.status !in listOf("VOID", "VOIDED", "DRAFT") }
        check(s.invoices.none { it.status == "DRAFT" }) { "Issue or remove existing drafts before move-out." }
        val period = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Phnom_Penh")).apply { timeInMillis = input.date }
        val periodKey = period.get(Calendar.YEAR)*12 + period.get(Calendar.MONTH)
        check(official.none { it.billingYear*12 + it.billingMonth-1 > periodKey }) { "A later billing period exists. Review it before move-out." }
        check(s.readings.none { it.readingDate > input.date }) { "Readings exist after this move-out date." }
        val e = settlementBasis(s, "ELECTRICITY")
        val w = settlementBasis(s, "WATER")
        val elecStart = e.previous
        val waterStart = w.previous
        require(input.electricity >= MeterHistory.minimumCurrent(s.readings.filter { it.meterType == "ELECTRICITY" }, e)) { "Final electricity must cover the current meter readings." }
        val metered = t.waterBillingMethod != "FIXED"
        if (metered) require(input.water != null && input.water >= MeterHistory.minimumCurrent(s.readings.filter { it.meterType == "WATER" }, w)) { "Final water must cover the current meter readings." }
        val fixedAlreadyBilled = official.any { it.billingMonth == period.get(Calendar.MONTH)+1 && it.billingYear == period.get(Calendar.YEAR) && it.waterBillingMethod == "FIXED" }
        val result = BillingCalculator.calculate(BillingCalculator.BillingInput(
            rentKhr=input.additionalRent, previousElectricity=elecStart,currentElectricity=input.electricity,
            electricityRateKhr=t.electricityRate,isWaterMeter=metered,previousWater=waterStart,
            currentWater=input.water ?: waterStart,waterRateKhr=t.waterRate,
            fixedWaterFeeKhr=if (fixedAlreadyBilled) 0 else t.waterRate,
            rubbishFeeKhr=0,otherChargesKhr=input.otherCharges, electricityCarriedUsage=e.carried, waterCarriedUsage=w.carried))
        val existingDebt = official.fold(0L) { sum, invoice ->
            val paid = s.payments.filter { it.status == "ACTIVE" && it.invoiceId == invoice.invoiceId }.sumOf { it.amountKhr }
            Math.addExact(sum, (invoice.totalKhr-paid).coerceAtLeast(0))
        }
        val totalDebt = Math.addExact(existingDebt,result.totalKhr)
        require(input.applyDeposit <= totalDebt) { "Deposit allocation exceeds invoice debt." }
        val used = Math.addExact(input.applyDeposit,input.deductions)
        require(used <= t.deposit) { "Deposit allocations exceed the deposit held." }
        return MoveOutReview(s,input,elecStart,waterStart,result.electricityChargeKhr,result.waterChargeKhr,
            result.totalKhr,existingDebt,t.deposit-used,totalDebt-input.applyDeposit,e,w)
    }

    suspend fun complete(expected: MoveOutReview) = db.withTransaction {
        val id = expected.snapshot.tenancy.tenancyId
        check(db.moveOutDao().get(id) == null) { "Move-out already completed." }
        val fresh = snapshot(id)
        check(fresh == expected.snapshot) { "Rental, readings or payments changed. Review again." }
        val r = review(fresh,expected.input)
        val i=r.input; val t=fresh.tenancy
        val cal=Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Phnom_Penh")).apply { timeInMillis=i.date }
        val now=System.currentTimeMillis()
        // Every completed move-out retains a final document, including zero new charges.
        val finalId = run {
            val finalDraft = InvoiceEntity(
                tenancyId=id,invoiceKind="FINAL",billingMonth=cal.get(Calendar.MONTH)+1,billingYear=cal.get(Calendar.YEAR),
                rentKhr=i.additionalRent,electricityPrevious=r.electricityStart,electricityCurrent=i.electricity,
                electricityUsage=r.electricityBasis.usage(i.electricity),electricityRateKhrSnapshot=t.electricityRate,
                electricityChargeKhr=r.electricityCharge,waterBillingMethod=t.waterBillingMethod,
                waterPrevious=if(t.waterBillingMethod=="FIXED") null else r.waterStart,
                waterCurrent=if(t.waterBillingMethod=="FIXED") null else i.water,
                waterUsage=if(t.waterBillingMethod=="FIXED") null else r.waterBasis.usage(i.water!!),
                waterRateKhrSnapshot=t.waterRate,fixedWaterFeeKhrSnapshot=if(t.waterBillingMethod=="FIXED") r.waterCharge else null,
                waterChargeKhr=r.waterCharge,rubbishFeeKhrSnapshot=0,otherChargesKhr=i.otherCharges,
                utilitySubtotalKhr=Math.addExact(Math.addExact(r.electricityCharge,r.waterCharge),i.otherCharges),
                electricityCarriedUsage=r.electricityBasis.carried, waterCarriedUsage=r.waterBasis.carried,
                electricityMeterSequence=r.electricityBasis.sequence, waterMeterSequence=r.waterBasis.sequence,
                totalKhr=r.newCharges,balanceKhr=0L,issueDate=0L,dueDate=i.date,status="DRAFT")
            val savedId = db.invoiceDao().insertInvoice(finalDraft)
            val savedDraft = finalDraft.copy(invoiceId = savedId)
            db.writeDraftItems(savedDraft)
            db.invoiceDao().updateInvoice(savedDraft.copy(status = "ISSUED",
                issueDate = now, balanceKhr = r.newCharges))
            savedId
        }
        var allocation=i.applyDeposit
        // Oldest debt first. Every allocation is a traceable payment, not income received again.
        val invoices=db.invoiceDao().getForTenancy(id).filter { it.status !in listOf("VOID","VOIDED","DRAFT") }.sortedBy { it.dueDate }
        for (invoice in invoices) {
            val paid=db.paymentDao().getTotalPaidForInvoice(invoice.invoiceId)
            val balance=(invoice.totalKhr-paid).coerceAtLeast(0)
            val amount=minOf(allocation,balance)
            if(amount>0) {
                db.paymentDao().insertPayment(PaymentEntity(invoiceId=invoice.invoiceId,amountKhr=amount,
                    paymentMethod="DEPOSIT",note="Move-out deposit allocation",paymentDate=now))
                allocation-=amount
                db.invoiceDao().updatePaymentBalances(invoice.invoiceId,paid+amount,balance-amount,
                    BillingCalculator.resolveInvoiceStatus(invoice.totalKhr,paid+amount,invoice.dueDate))
            }
        }
        fun finalReading(type: String, value: Long, basis: MeterHistory.Basis) = MeterReadingEntity(
            tenancyId=id, roomId=t.roomId, meterType=type, previousReading=basis.previous,
            currentReading=value, carriedUsage=basis.carried, meterSequence=basis.sequence,
            readingDate=i.date, billingMonth=cal.get(Calendar.MONTH)+1, billingYear=cal.get(Calendar.YEAR), readingType="FINAL")
        db.meterReadingDao().insertReading(finalReading("ELECTRICITY", i.electricity, r.electricityBasis))
        if (t.waterBillingMethod != "FIXED") db.meterReadingDao().insertReading(finalReading("WATER", i.water!!, r.waterBasis))
        val savedChargeReason = if (i.additionalRent > 0 || i.otherCharges > 0) i.chargeReason.trim() else ""
        val savedDeductionReason = if (i.deductions > 0) i.deductionReason.trim() else ""
        db.moveOutDao().insert(MoveOutSettlementEntity(id,t.roomId,i.date,i.electricity,
            if(t.waterBillingMethod=="FIXED") null else i.water,t.deposit,i.applyDeposit,
            savedChargeReason,i.deductions,savedDeductionReason,r.refund,false,null,null,r.debt,finalId,now))
        check(db.tenancyDao().markTenancyEnded(id,i.date)==1) { "Rental was already ended." }
        db.roomDao().updateRoomStatus(t.roomId,"VACANT")
    }
}
