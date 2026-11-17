# Stage 6 — Payments and payment history

Date: 11 November 2026, approximately 10:05–10:16 Asia/Phnom_Penh. Read [Stage 5](stage5-invoice-ui-results-2026-11-11.md) before starting. Verified `adb devices -l` listed emulator-5554 (sdk_gphone64_arm64, transport1), then serial-targeted `emu avd name` returned **RoomKit-Test**. Every subsequent device command targeted that serial. Used actual taps, text input, swipes, Back and app force-stop/reopen. These are simulated app records only; no banking app, QR scanning or real transfer was used.

## Tested version and preserved changes

Workspace HEAD: `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`. Tested existing installed debug APK from the Stage 4 fix; no build/install in this stage. APK does not expose a verified embedded commit ID, so HEAD identifies workspace baseline rather than a clean-commit-only build.

Preserved RoomsFragment initialization-order fix, RoomDetailFragment commented-code cleanup and replacement-usage fix; existing untracked MoveInValidationDatabaseTest, RoomDetailUsageTest, InvoiceSnapshotDatabaseTest and QA artifacts. Production diff is byte-identical to [Stage 5 preserved diff](stage5-preserved-production-changes.patch), verified in [comparison](stage6-preservation-comparison.json). Added only PaymentProtectionDatabaseTest and QA evidence. No production-code edits, commit or push.

## Baseline gate — PASS

UI and read-only database agree: **Invoice #0002 / invoiceId2 / tenancyId2 / QA-301 / Test Tenant Alpha / November2026 / KHR**, total445000, paid0, balance445000, dueDec5,2026. StatusISSUED, no payments. Therefore no repeated existing payments. [Invoice](stage6-01-start.png), [database](stage6-db-baseline.json).

November Payments baseline: outstanding445000, collected0, overdue0, one unpaid invoice. November dashboard baseline: billed445000, collected0, outstanding445000, one unpaid, 4 occupied rooms. [Payments](stage6-02-payments-baseline.png), [dashboard](stage6-03-dashboard-baseline.png). These totals contain only this invoice; no other tenant has an invoice/payment in the exported database.

## Actual UI checks

| Check | Result | Actual observation / evidence |
|---|---|---|
| 6.1 Partial payment | PASS | Entered100000, Cash, Nov11; tapped Save once. Invoice paid100000/balance345000. Payments shows Partial, collected100000/outstanding345000. Exactly one active payment, ID1. [Ready](stage6-06-cash-ready.png), [saved](stage6-07-partial.png), [Payments](stage6-09-invalid-form.png), [DB](stage6-db-partial.json). |
| 6.2 Empty | PASS | Cleared field, tapped Save; “Enter a valid payment amount.” No record. [Screenshot](stage6-10-empty.png). |
| 6.2 Zero | PASS | Entered0, tapped Save; same validation rejection. [Screenshot](stage6-11-zero.png). |
| 6.2 Negative | INPUT PREVENTED | Typed-1, resulting text **1**. Minus filtered; did not save transformed positive1. Existing error text is retained from earlier rejection, not proof that negative1 was submitted. [Screenshot](stage6-12-negative.png). |
| 6.2 Above balance | PASS | Entered345001, tapped Save; “Amount cannot exceed balance of 345,000៛.” [Screenshot](stage6-13-overpayment.png). |
| 6.2 Letters | INPUT PREVENTED | Cleared and typedabc; field remained empty. Did not save. [Screenshot](stage6-14-letters.png). |
| 6.2 Decimal KHR | INPUT PREVENTED | Typed1.5; resulting text **15**. Decimal point filtered; did not save transformed15. Visible overpayment error is stale from previous attempt and is not counted as decimal rejection. [Screenshot](stage6-15-decimal.png). |
| 6.2 No additional records | PASS | Dismissed form; complete exported snapshot identical to post-6.1 snapshot: one active payment, paid100000/balance345000. [DB](stage6-db-invalid.json), [comparison](stage6-preservation-comparison.json). |
| 6.3 Method/reference | PASS | Bank Transfer available. Dedicated **Payment reference** field exists separately from Note. Entered45000 and TEST-PAY-002; saved once. Reopened invoice: Nov11, Bank transfer45000, referenceTEST-PAY-002. Two active payments, paid145000/balance300000. Reference is not MISSING. [Methods](stage6-16-methods.png), [ready](stage6-17-bank-ready.png), [reopened history](stage6-19-bank-history.png), [DB](stage6-db-bank.json). |
| 6.4 Full payment | PASS | Reopened form with remaining300000, Cash. Sent two back-to-back taps at observed Save location **(535,2200)** using one serial-targeted shell command. Exactly one new payment, ID3; three active total, paid445000/balance0, PAID. [Ready](stage6-21-full-ready.png), [saved history](stage6-22-full-paid.png), [DB](stage6-db-paid.json). |
| 6.4 Duplicate test delivery limitation | PASS for persisted outcome | Both coordinate tap injections were sent with no intentional delay. No intermediate frame/event trace proves whether second tap reached a still-enabled Save button or the sheet had already disappeared. Do not interpret this as proof of two accepted Save callbacks. One payment inserted. |
| 6.4 Further payment/outstanding | PASS | Paid invoice has no Record payment or KHQR action. Payments shows collected445000/outstanding0 and three receipts. Unpaid0 filter is empty. [Paid actions/history](stage6-29-restarted-all-history.png), [Payments](stage6-26-payments-paid.png), [Unpaid filter](stage6-27-unpaid-empty.png). |
| 6.5 Persistence | PASS | Force-stop/reopen. Same three payments and details, zero balance; full exported snapshot identical to paid snapshot. [Billing](stage6-25-restart-billing.png), [summary](stage6-28-paid-restarted-history.png), [all history](stage6-29-restarted-all-history.png), [DB](stage6-db-paid-restart.json). |
| 6.6 Void only300000 | PASS | Verified sheet explicitly showed payment#3,300000 before entering reason. Saved void with exact reason **Test payment entered incorrectly.** Original remains VOIDED with strikethrough, reason/date and “Not included in total paid”. Total445000, active paid145000/balance300000. [Selected target](stage6-31-void300-confirm.png), [reason](stage6-32-void300-reason.png), [result](stage6-33-voided.png), [DB](stage6-db-voided.json). |
| 6.6 Second void | PASS (unavailable) | Tapped voided payment card; no void sheet/action opens. No further balance change. This is UI unavailability, not a submitted backend retry. [Screenshot](stage6-34-second-void-unavailable.png). Backend retry tested separately below. |
| 6.6 Totals | PASS | November Payments: collected145000/outstanding300000/overdue0, Partial card paid145000 of445000. November dashboard: billed445000/collected145000/outstanding300000, one partial. Compared with baseline, collected+145000 and outstanding-145000, billed unchanged; compared with full payment, collected-300000/outstanding+300000. No other invoices affect these totals. [Payments](stage6-40-navigation-root.png), [dashboard](stage6-37-dashboard-after-void.png). |
| 6.7 Invoice void submission | BLOCKED (action unavailable) | Inspected complete invoice action area while145000 active payments remain: Void invoice is hidden, so no UI submission or destructive confirmation is possible. Record payment/KHQR/export/share remain; did not invoke QR/export/share. Invoice unchanged. This is observed UI prevention, not a claimed rejection dialog. [Bottom actions](stage6-41-bottom-actions.png). Isolated backend rejection PASS below. |
| 6.8 Final restart | PASS | Force-stop/reopen again. Invoice#0002 remains issued in document lifecycle, Partial payment status, paid145000/balance300000; two active payments and one voided. Same invoice and payment rows as immediately after void. [Invoice](stage6-43-final-invoice.png), [history/reason](stage6-44-final-history.png), [all three](stage6-45-final-all-payments.png), [DB](stage6-db-final.json). |
| Snapshot/data preservation | PASS (read-only DB) | All invoice financial snapshot fields/items, total, due date,10 meter readings, deposit/tenancy/tenant/room records unchanged. Only expected payment balances/status and three new payment records changed. Integrityok; one invoice total. [Comparison](stage6-preservation-comparison.json). |
| Crashes | PASS | No crash-buffer entries or AndroidRuntime exceptions from10:04 through final checks. [Crash log](stage6-crash-logcat.log), [runtime log](stage6-android-runtime.log), both empty. |

Some navigation screenshots captured restored invoice destinations instead of the Payments root. Evidence23/24/30/35/36/38/39 is not counted for the originally intended check: screenshots24 and30 show payment#2 void sheet opened while navigating; neither was confirmed or given a reason. Dismissed safely. Screenshot39 shows an unsaved new payment form, also dismissed without saving. Corrected navigation and verified actual state before subsequent writes. Database evidence confirms payment#2 never changed and no extra payments were added.

## Payment IDs and balances

All amounts KHR; all payment dates displayed11Nov2026. Dedicated reference exists only for ID2; notes are null.

| Step | Payment ID | Amount/method | Active paid | Balance | Stored invoice status |
|---|---|---|---|---|---|
| Baseline | none | none | 0 | 445000 | ISSUED |
| 6.1 | 1 | 100000 CASH | 100000 | 345000 | PARTIALLY_PAID |
| 6.3 | 2 | 45000 BANK_TRANSFER, TEST-PAY-002 | 145000 | 300000 | PARTIALLY_PAID |
| 6.4 | 3 | 300000 CASH | 445000 | 0 | PAID |
| 6.6/final | 3 VOIDED;1/2 ACTIVE | ID3 reason retained | 145000 | 300000 | PARTIALLY_PAID |

Schema stores a combined status, not a separate document-status column. Raw final `invoices.status` is **PARTIALLY_PAID**, not literalISSUED. `InvoiceStatusResolver` maps this to **DocumentStatus.ISSUED / PaymentStatus.PARTIALLY_PAID**; issued number, issueDate and immutable document fields remain unchanged. UI shows “Partially Paid”. This distinction is supported by source and isolated test, not an unexposed document-status badge claimed as UI evidence.

## Read-only database evidence

Serial-targeted `exec-out run-as com.example.roomkit tar cf - databases` exported database/WAL to temporary host snapshots. Queried copies with SQLite URI `mode=ro` and `PRAGMA query_only=ON`. No emulator database SQL writes. JSON snapshots linked above contain invoice/items/payments/readings/tenancy/tenant/room rows; comparison verifies invalid tests caused no writes, both restarts preserved exact rows, and snapshot/history/deposit stayed unchanged. Final active payments1+2 sum145000; payment3 retainedVOIDED; no other payments or invoices.

## Isolated automated evidence — separate from UI

Added [PaymentProtectionDatabaseTest](../../app/src/test/java/com/example/roomkit/PaymentProtectionDatabaseTest.kt), fresh in-memory Room database with production guards/repository. Issued matching445000 fixture, recorded100000/45000/300000, voided only300000, then attempted second void and invoice void while145000 active remains. Both calls rejected; invoice, payments, immutable items and readings unchanged. Repeated-payment void error: “This payment has already been voided.” Invoice void fails the issued/unpaid-status guard for PARTIALLY_PAID. Test also verifies logical documentISSUED/paymentPARTIALLY_PAID mapping.

**PASS:1 test,0 failures/errors/skips.** `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.example.roomkit.PaymentProtectionDatabaseTest --console=plain`. [Gradle log](stage6-payment-protection-test.log), [JUnit](stage6-payment-protection-junit.xml). No emulator database access by tests; no APK installed.

## Final state

Invoice#0002/ID2, QA-301/Alpha/November2026: total445000, paid145000, **balance300000 left unpaid for Stage7**, dueDecember5. Document issued; payment Partial. Two active payments (IDs1,2), one voided (ID3) with reason. No invoice void, other payment void, deleted records, banking actions, production-code change, data clear/uninstall, commit or push. No observed payment/balance failure; direct invoice-void submission remains BLOCKED by hidden UI action, with separate backend rejection evidence.
