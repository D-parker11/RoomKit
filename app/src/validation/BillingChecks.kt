import com.example.roomkit.Date.Billing.BillingCalculator
import com.example.roomkit.Date.Billing.CurrencyFormatter

fun main() {
    val input = BillingCalculator.BillingInput(
        rentKhr = 400000, previousElectricity = 890, currentElectricity = 905,
        electricityRateKhr = 1000, isWaterMeter = true,
        previousWater = 60, currentWater = 72, waterRateKhr = 1500,
        fixedWaterFeeKhr = 20000, rubbishFeeKhr = 5000
    )
    val result = BillingCalculator.calculate(input)
    check(result.electricityChargeKhr == 15000L)
    check(result.waterChargeKhr == 18000L)
    check(result.utilitySubtotalKhr == 38000L)
    check(result.totalKhr == 438000L)
    check(BillingCalculator.calculate(input.copy(isWaterMeter = false)).totalKhr == 440000L)
    check(BillingCalculator.calculate(input.copy(discountKhr = 8000)).totalKhr == 430000L)
    check(runCatching { BillingCalculator.calculate(input.copy(currentElectricity = 889)) }.isFailure)
    check(runCatching { BillingCalculator.calculate(input.copy(rentKhr = -1)) }.isFailure)
    check(runCatching { BillingCalculator.calculate(input.copy(discountKhr = 500000)) }.isFailure)
    check(runCatching { BillingCalculator.calculate(input.copy(electricityRateKhr = Long.MAX_VALUE)) }.isFailure)
    check(CurrencyFormatter.parseKhrInput("400000") == 400000L)
    check(CurrencyFormatter.parseKhrInput("400000៛") == 400000L)
    check(CurrencyFormatter.parseKhrInput("100.50") == null)
    check(CurrencyFormatter.parseKhrInput("-1") == null)
    check(CurrencyFormatter.formatKhr(400000) == "400,000 ៛")
    check(BillingCalculator.resolveInvoiceStatus(438000, 100000, 2000, 1000) == "PARTIALLY_PAID")
    check(BillingCalculator.resolveInvoiceStatus(438000, 100000, 2000, 3000) == "OVERDUE")
    check(BillingCalculator.resolveInvoiceStatus(438000, 438000, 2000, 3000) == "PAID")
    check(BillingCalculator.resolveInvoiceStatus(438000, 0, 2000, 1000) == "ISSUED")
    println("19 KHR billing and formatting checks passed")
}
