# Stage 5 — Invoice testing, 11 November 2026

## Target and baseline

Verified `adb devices -l`: emulator-5554, sdk_gphone64_arm64, transport 1. Serial-targeted `emu avd name` returned **RoomKit-Test** before interaction. All subsequent device commands used emulator-5554. Actual taps, text entry, swipes, Back, screenshots, and force-stop/reopen were used. Test window approximately 09:31–09:44 Asia/Phnom_Penh.

Read [Stage 3](stage3-ui-results-2026-11-10.md) and [Stage 4](stage4-meter-ui-results-2026-11-10.md) first. Stage 3 move-in/saved terms and isolated negative validation passed; retained-negative UI submission remained blocked because fields filtered minus signs. Stage 4 metered water and replacement follow-ups passed; the replacement room-summary display defect was fixed and retested successfully. QA-301 remained ready with INITIAL 100 and November MONTHLY 100→115, usage 15.

Workspace HEAD: `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`. Used the existing installed debug APK from the Stage 4 fix. The APK does not expose a verified embedded commit identifier; this is the workspace baseline, not a claim of testing a clean commit. Preserved existing RoomsFragment initialization-order fix, RoomDetailFragment legacy-comment cleanup and usage-display fix, MoveInValidationDatabaseTest, RoomDetailUsageTest and prior QA artifacts. [Preserved tracked diff](stage5-preserved-production-changes.patch). No production code change, build/install, commit or push in Stage 5. Added only an isolated host regression test and QA evidence.

Initial read-only database inspection found **zero invoices, invoice items and draft adjustments**. No existing issued-invoice conflict. Target: Suzuki / QA-301 / Test Tenant Alpha, tenancyId=2, roomId=2, November 2026. No other room was invoiced.

## Actual UI results

| Check | Result | Observation and screenshots |
|---|---|---|
| 5.1 Calculations | PASS | Preview Invoice showed Alpha, QA-301, Nov 2026, KHR, electricity previous 100/current 115/usage 15 × 1000 = 15000; rent 400000, fixed water 20000, rubbish 10000, total **445000**. No deposit charge/payment. Full monthly rent applied despite move-in Nov 10; no proration appeared. [Preview](stage5-02-prepare.png), [actions](stage5-03-preview-actions.png). |
| 5.2 Save/reopen | PASS | Saved Draft #0001, left/reopened and force-stopped/reopened. Same draft and 445000 persisted. UI exposes no payment entry for draft and states no payment is due until issue. No duplicate created. [Saved](stage5-05-saved-draft.png), [draft actions](stage5-08-draft-actions.png), [restored after restart](stage5-12-draft1-restored.png). Internal ID confirmed separately below. |
| 5.3 Negative other charge | INPUT PREVENTED | Typed -1; field displayed positive 1 because minus was filtered. This is not a negative-value rejection. Did not save 1; restored 5000. [Screenshot](stage5-14-negative-other.png). |
| 5.3 Excessive discount | PASS | Discount 999999 displayed “Discount exceeds the subtotal.” Save/Issue disabled; tapping Save did not persist invalid values. Restored 10000. [Screenshot](stage5-15-excessive-discount.png). |
| 5.3 Valid edit | PASS | Other charges 5000, discount 10000, reason “Test discount”; total **440000**. Saved, left and reopened with values/reason intact, still Draft #0001. [Valid values](stage5-16-valid-adjustments.png), [reopened](stage5-18-adjustments-reopened.png). |
| 5.4 Delete/fresh draft | PASS | Used Delete draft and confirmation for this run’s Draft #0001 only. Then prepared fresh November preview without adjustments and saved Draft #0002 at **445000**. [Confirmation](stage5-19-delete-confirm.png), [deleted](stage5-20-deleted.png), [fresh preview](stage5-22-fresh-preview.png), [fresh saved](stage5-23-fresh-saved.png). |
| 5.5 Issue | PASS | Reopened fresh saved Draft #0002, tapped Issue invoice and confirmed. Became Invoice #0002, Unpaid, total 445000, paid 0, balance 445000 KHR, **due Dec 5, 2026**, matching requested next-month rule. [Reopened draft](stage5-24-fresh-reopened.png), [confirmation](stage5-25-issue-confirm.png), [issued](stage5-26-issued.png). |
| 5.5 Payment availability | PASS | Record payment became available. Opened payment sheet and dismissed with Back without submitting. **No payment recorded.** [Payment sheet](stage5-28-payment-available.png). |
| 5.6 Locks | PASS | Issued screen has static financial items, no editor and no ordinary delete. Tapping amount and text entry did not change it. Only Void invoice offered; never invoked. [Issued actions](stage5-27-issued-actions.png), [lock inspection](stage5-29-lock-actions.png). No destructive request was submitted because ordinary delete is unavailable. |
| 5.6 Duplicate preparation | PASS | Returned to QA-301 for the same month; UI offers View Invoice rather than another preparation action and opens existing #0002. No second active regular invoice. [Room](stage5-31-repeat-access.png), [same invoice](stage5-32-same-invoice.png). This verifies the UI prevents reaching a second preparation path, rather than claiming a second issue request was submitted. |
| 5.7 Future-rate editing UI | NOT TESTED | Manage Tenancy offers View Tenant Profile and Move Out only. Tenant editor exposes identity/contact/notes, no future electricity-rate control. Did not change tenancy terms or use the database to work around missing UI. [Manage](stage5-33-tenancy-controls.png), [profile](stage5-34-profile-terms.png), [editor](stage5-35-profile-editor.png). Isolated automated evidence below is separate. |
| 5.8 Restart | PASS | Force-stop/reopen, Billing shows exactly one unpaid record; opened Invoice #0002 with unchanged tenant/month/items/currency, total445000, paid0, balance445000 and Dec5 due date. [Billing after restart](stage5-36-restart-billing.png), [invoice after restart](stage5-37-restarted-invoice.png). |
| Meter preservation | PASS (database evidence) | All 10 history rows unchanged throughout; details below. No monthly reading was saved during Stage 5. |
| Crashes | PASS for RoomKit | No new RoomKit crash or AndroidRuntime exception. Bluetooth system-process crash is separately documented below. |

No UI failure blocked dependent checks. Future-rate editing remains the only NOT TESTED UI check. Screenshots caught during navigation that show the room/dashboard rather than the intended invoice are not used as invoice passes.

## Read-only database evidence

Exported app database/WAL using `exec-out run-as ... tar`, then inspected host copies with SQLite URI `mode=ro` and `PRAGMA query_only=ON`. No emulator SQL writes, database edits, data clear or uninstall. Initial host snapshot retained under `/private/tmp/roomkit-stage5-before/`.

- [Draft 1](stage5-db-draft1.json): invoiceId=1, DRAFT, six items, total445000, no payments. Draft balance is 0 until issue.
- [Edited](stage5-db-edited.json): same ID1, total440000, other5000, discount10000, reason Test discount, item discount -10000; no payments.
- [Deleted](stage5-db-deleted.json): no invoices/items/payments; rooms, tenants, tenancies and readings preserved.
- [Fresh draft](stage5-db-fresh.json): invoiceId=2, DRAFT, no adjustments, total445000.
- [Issued](stage5-db-issued.json): SAME ID2 becomes ISSUED, six items, total445000, paid0/balance445000. UI number #0002 is formatted from the unique internal ID; schema has no separate invoice-number column.
- [Final after restart](stage5-db-final.json): exactly one REGULAR invoice, tenancy2/month11/year2026/statusISSUED, ID2, no payments. Row and every item equal the issued snapshot.
- Electricity snapshot: previous100/current115/usage15/rate1000/charge15000. Fixed-water snapshot20000; rubbish10000; rent400000; no deposit item. Zero OTHER and DISCOUNT rows exist but add nothing. Period Nov1–Nov30. Due timestamp1796489999999 = **2026-12-05 23:59:59.999 +07:00**.
- [Final comparison](stage5-final-comparison.json): all 10 meter rows, tenancies, tenants, rooms and payments exactly equal pre-run baseline; issued invoice/items identical across restart; integrity_check=ok. Deposit remains a tenancy term, not a charge or payment. All other rooms/tenancies unchanged.

## Isolated automated evidence — not UI coverage

Added [InvoiceSnapshotDatabaseTest](../../app/src/test/java/com/example/roomkit/InvoiceSnapshotDatabaseTest.kt), Robolectric with a fresh in-memory Room database and production schema guards/BillingRepository. Fixture matches requested rent/deposit/rates and 100→115 November usage. Issued fixture invoice total445000/rate1000/charge15000/paid0/balance445000. Changed only the isolated fixture tenancy rate to1200, then restored1000. At both steps the entire issued invoice, all item rows and meter history remained identical; attempting prepare for the same month failed and retained exactly one invoice.

**PASS: 1 test, 0 failures/errors/skips.** Command: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest --tests com.example.roomkit.InvoiceSnapshotDatabaseTest --console=plain`. [Log](stage5-snapshot-test.log), [JUnit](stage5-snapshot-junit.xml). Initial sandbox attempt could not access Gradle cache lock; rerun with approved access succeeded. No test accessed the emulator database. No APK was rebuilt or installed.

## Runtime notes and final state

[Crash buffer](stage5-crash-logcat.log) from 09:31 onward contains a **system Bluetooth** native SIGABRT at09:34:06 (`com.google.android.bluetooth`, HCI hardware error0x42), not RoomKit. ADB daemon/device access briefly interrupted before first save; reconnected to the same serial, checked state, and continued. [AndroidRuntime log](stage5-android-runtime.log) contains no exceptions. No RoomKit crash observed.

Final emulator state: QA-301 November2026 **Invoice #0002 / internal ID2 / ISSUED**, total445000 KHR, paid0, balance445000, dueDec5. First test draft ID1 was deleted by UI; fresh ID2 remains issued and was not voided. No payments, other-room invoices, tenancy-term changes, meter-history changes, or unrelated record changes. Production code and prior local changes preserved; no commit or push.
