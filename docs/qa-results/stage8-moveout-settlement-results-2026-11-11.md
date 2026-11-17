# Stage 8 — Move-out, final billing, and deposit settlement

## Target and test context

- Verified AVD: **RoomKit-Test**, serial **emulator-5554** (`sdk_gphone64_arm64`). All ADB commands were serial-targeted.
- Tenant/room/tenancy: Test Tenant Alpha / QA-301 / tenantId 3, roomId 2, tenancyId 2.
- Tested commit: `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`.
- Existing local changes were preserved. No production code was changed, no automated tests were run, and nothing was committed or pushed. The pre-existing modified RoomDetailFragment and RoomsFragment files, untracked test files, and prior QA artifacts remain in the workspace.
- The database was exported through `run-as tar` and queried from a host copy with SQLite `mode=ro` and `query_only=ON`. No SQL writes were made to the emulator.

## Results

| Check | Result | Evidence and details |
|---|---|---|
| **8.1 Starting state** | **PASS** | Before move-out, #0002/ID 2 was 445,000 total, 245,000 paid, 200,000 due; #0003/ID 3 was 440,000 total, 40,000 paid, 400,000 due. Account outstanding was 600,000; deposit held was 400,000; latest electricity reading was 125; tenancy was ACTIVE and QA-301 OCCUPIED. No settlement, deposit allocation, or final invoice/reading existed. [Starting invoice](stage8-01-start.png), [room state](stage8-04-room.png), [pre-move-out read-only evidence](stage8-after-cancel-db-evidence.json). |
| **8.2 Entry points and cancellation** | **PASS** | Room → Manage tenancy → Move Out and Alpha’s profile → Move Out opened the same Alpha / Suzuki / QA-301 form and showed invoices #2/#3, 400,000 held deposit, and 600,000 unpaid balance. Both visits were cancelled. Read-only snapshot afterward: tenancy ACTIVE, room OCCUPIED, zero move-out settlements, zero FINAL invoices, zero FINAL readings. [Room entry](stage8-07-room-entry.png), [profile entry](stage8-11-profile-entry.png), [cancel snapshot](stage8-after-cancel-db-evidence.json). |
| **8.3 Move-out date and billing period** | **PASS** | At the original emulator date, Nov 11, 2026, an earlier November move-out would fail the implementation’s “A later billing period exists” guard because December is already invoiced. The allowed emulator-only Settings change to Dec 31 made a same-period move-out possible. Source review confirms the date must be between move-in and today; a later official invoice blocks review. The UI says rent is not automatically prorated, and fixed water is excluded when that month is already billed. [Original date settings](stage8-12-original-date-settings.png), [Dec 31 selected](stage8-16-dec31-selected.png), [move-out date/form](stage8-22-moveout-dec31.png). |
| **8.4 Final readings and charges** | **PASS** | Submitting 124 kWh produced “Final electricity must cover the current meter readings.” The valid final value was restored to 125. Fixed water showed a 20,000 monthly fee and explicitly required no meter reading. Review and final invoice showed **0 KHR additional charges**: electricity 125→125 = 0; no additional rent (rent is not prorated automatically); fixed water 0 because December was already billed; rubbish 0 because no unbilled rubbish charge was entered and the December charge already exists. Other fees were 0. The saved zero-total FINAL invoice has all six items at zero, preserving the two regular invoices’ original items and totals. [124 rejected](stage8-24-reject124.png), [fixed-water/rent rules](stage8-25-lower-form.png), [review](stage8-43-review-bottom.png), [final DB evidence](stage8-final-db-evidence.json). |
| **8.5 Deposit validation** | **PASS with UI coverage limits** | Application 400,001 was rejected with “Deposit allocations exceed the deposit held.” A combined application 250,001 + deduction 150,000 was also rejected because allocations exceeded 400,000. Typing `-1` into either numeric settlement field yielded displayed text `1`; these are **INPUT PREVENTED**, not submitted negative-value rejection. **MISSING:** there is no editable refund amount field; refund is calculated as the remaining deposit and the checkbox only asserts that the refund was actually paid. Therefore a separately submitted application-plus-refund overage could not be exercised. [Over-application error](stage8-31-invalid-apply-message.png), [combined overage error](stage8-34-combined-rejected.png), [actual numeric-field text after minus input](stage8-37-negative-deduction2.png). |
| **8.6 Settlement and closure** | **PASS for financial settlement; FAIL for saved deduction-note metadata** | Reviewed 600,000 existing debt, 0 new final charges, 400,000 deposit applied, 0 deductions, 0 refund, and 200,000 debt remaining. The app allowed closure with that debt, consistent with its acknowledgement that remaining debt stays with the tenant. It allocated the deposit oldest-due first as two active DEPOSIT payments: **payment ID 6 → invoice ID 2: 200,000**; **payment ID 7 → invoice ID 3: 200,000**. Thus November became paid (total remains 445,000), December became 240,000 paid / 200,000 due (total remains 440,000), and debt decreased exactly once to 200,000. Tenancy ended Dec 31; final invoice ID 4 is FINAL, total 0. **Failure:** while clearing invalid test inputs, `-1` remained in the deduction-reason text field even though the deduction amount was cleared to 0. The settlement stored `Deductions: -1`. The ended settlement is read-only in the UI; I did not modify the database. [Reviewed figures](stage8-43-review-bottom.png), [completion/history](stage8-48-moveout-history.png), [invoice-linked allocations](stage8-58-dec-payments.png), [saved note visible](stage8-66-history-reopened.png), [read-only DB evidence](stage8-final-db-evidence.json). |
| **8.7 Occupancy, history, duplicate prevention** | **PASS with UI history limitation** | After closure and restart, total rooms remained 4; occupied fell from 4 to 3; vacant rose from 0 to 1. QA-301 showed Vacant / No active tenant. Alpha remained in Moved Out with an ended rental-history entry and Move Out History. Invoices #2, #3, and final #4 remained selectable; November’s original voided payment and its reason, other original payments, December Cash payment, and both deposit allocations remained visible. The reopened settlement fields were disabled and no second completion action was present. DB confirms exactly one settlement, one final invoice, and one final reading. **MISSING in UI:** after move-out, Room Detail shows dashes for current-month readings; Move Out History displays final electricity 125 but not a browsable initial/monthly meter-history list. Read-only DB confirms INITIAL ID 2 (100), MONTHLY ID 3 (100→115), MONTHLY ID 11 (115→125), and FINAL ID 12 (125→125) all remain. **No separate move-out settlement PDF/receipt control was present** in Move Out History. [Post-restart occupancy](stage8-49-restarted.png), [room totals](stage8-50-property-after.png), [QA-301 vacant](stage8-51-rooms-after.png), [invoice list](stage8-62-rental-invoices.png), [November payments/void](stage8-65-nov-payments-bottom.png), [read-only settlement after restart](stage8-68-history-readonly-final.png). |
| **8.8 Restart and clock restoration** | **PASS with UI note issue** | Force-stopped and reopened RoomKit. The 200,000 KHR debt, occupancy, invoice/payment history, settlement, and meter rows persisted with no duplicates. After restoring automatic date/time, Nov 11 historical profile/invoice access still worked; December invoice remained visible even though it was now a future period. Original settings were automatic date/time ON, automatic time zone ON, location-based zone OFF, 12-hour format; timezone `Asia/Phnom_Penh`. Clock changed only on the emulator from Nov 11, 16:29:47 +07 to Dec 31, then automatic time was restored and returned it to Nov 11, 16:57 +07. **Transient UI failure:** immediately after completion, the stale review acknowledgement appeared as a checked “pending refund has now actually been paid” control even though refund was 0; after reopening the saved settlement following restart, that control was absent. [Restart/dashboard](stage8-49-restarted.png), [invoice access after date restore](stage8-73-invoice-list-after-restore.png), [restored Settings](stage8-70-clock-restored.png), [post-completion UI](stage8-46-after-completion.png), [reopened settlement](stage8-68-history-readonly-final.png). |

## Final amounts and record IDs

| Item | Final value |
|---|---:|
| Additional final charges | **0 KHR** |
| Deposit held | 400,000 KHR |
| Deposit applied | **400,000 KHR** (payment IDs 6 and 7, 200,000 each) |
| Deposit refunded | **0 KHR** |
| Remaining held deposit | **0 KHR** |
| Remaining tenant debt | **200,000 KHR** |
| November invoice #0002 / ID 2 | Total 445,000; paid 445,000; balance 0 (PAID) |
| December invoice #0003 / ID 3 | Total 440,000; paid 240,000; balance 200,000 (PARTIALLY_PAID) |
| Final invoice ID 4 | FINAL, total 0, balance 0 |
| Tenancy ID 2 | ENDED Dec 31, 2026 |
| Room QA-301 / ID 2 | VACANT |
| Emulator date | Temporarily Dec 31, 2026; automatic time restored to Nov 11, 2026 |

The deposit is represented both by one move-out settlement record and by its two invoice-linked DEPOSIT payment rows; those are linked representations of the same 400,000 KHR application, not separate deductions. SQLite integrity check is `ok`. The comparison confirms both regular invoice snapshots/items, original payment IDs 1–5, non-target rooms, and non-target tenancies are unchanged. [Final read-only database evidence](stage8-final-db-evidence.json).

## Issues to retain for follow-up

1. The deduction-reason field retained the negative-input test text `-1` despite zero deductions; the saved ended settlement is not editable in the UI.
2. The refund checkbox amount did not refresh reliably while editing; the formal review correctly showed refund due 0 KHR. A stale checked refund acknowledgement appeared immediately after completion and disappeared after reopening.
3. Ended room/profile UI does not provide a browsable initial/monthly meter-history list, although the database records and final reading remain intact.
4. No independent refund amount control or move-out settlement receipt/PDF was available.

## Logcat and automated evidence

The final RoomKit/AndroidRuntime crash logs are empty; no new crashes were found. No automated tests were run for this stage. UI evidence, read-only database evidence, and source-rule inspection are separated above; no automated result is claimed.

---

## Follow-up: settlement validation and refund state (2026-11-12)

### Scope, requirements, and protected evidence

- Verified AVD **RoomKit-Test**, serial **emulator-5554** before interaction. No other emulator was targeted.
- Tested commit remains `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`; no commit or push was made. Existing local changes were preserved.
- Read `docs/PHASE3_UPDATE.md` and `docs/qa-tenant-workflow-fixes.md`. They require payments, payment reversals, and refunds to remain available after close, and define final billing/readings behavior. They do **not** explicitly require a post-move-out meter-history screen or a separate settlement PDF/receipt. Accordingly, the absence of those UI affordances is not reported as a documented requirement failure. Meter rows remain persisted and queryable. No receipt requirement was invented.
- Before installing, read-only inspection confirmed Alpha tenancy ID 2 ended Dec 31, 2026; QA-301 was vacant; November invoice ID 2 total/balance 445,000/0; December invoice ID 3 total/balance 440,000/200,000; held/applied/refund deposit 400,000/400,000/0. A second read-only inspection after the in-place schema migration reconfirmed these historical values. No emulator database writes were performed.
- Emulator date/time was not changed in this follow-up. It remained automatic (`adb shell date`: Nov 12, 2026, +07).

### Root causes and implementation

1. **Deduction reason persistence:** the review form reused a mutable deduction-reason field after invalid-input attempts. The completion path saved that text regardless of the final deduction amount, so a zero deduction could persist stale `-1`. The repository now revalidates the review inside the completion transaction, persists an empty deduction reason for zero deductions, and requires a trimmed meaningful reason for positive deductions. This enforces the rule at the persistence boundary as well as the UI. Existing Alpha settlement text was deliberately not rewritten.
2. **Refund acknowledgement:** the old flow accepted an “actually paid” acknowledgement at move-out completion, including for a zero refund, without recording a distinct refund transaction state. Refunds now begin pending, can be recorded once through a separate explicit action only for a positive refund, and store payment date/reference. Repeated confirmation is guarded by a conditional update. The UI distinguishes no refund, pending, and paid.
3. **Persistence and migration:** schema migration 11→12 adds a deduction charge reason and refund payment timestamp/reference; backup validation disallows impossible paid states. Files changed: `AppDatabase.kt`, `Date/Local/Entity/MoveOutSettlementEntity.kt`, `Date/Local/Dao/MoveOutDao.kt`, `Date/Repositories/MoveOutRepository.kt`, `Date/Ui/MoveOut/MoveOutViewModel.kt`, `Date/Ui/MoveOut/MoveOutFragment.kt`, `Date/Backup/JsonBackupRepository.kt`, `res/values/strings.xml`, `testShared/.../Phase3Checks.kt`, and new `MoveOutSettlementValidationTest.kt`.

### Build and automated evidence

- **PASS:** `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` completed successfully; 49 unit tests completed with zero failures. New focused tests cover stale text with zero deduction, positive deduction without a valid reason, valid positive deduction, and one-time pending-refund completion with date/reference.
- **PASS:** debug APK installed with `adb install -r` in place. No uninstall or data clear. Database migrated to schema 12. These automated results are distinct from UI evidence.

### Actual UI follow-up and fixture status

- **PASS:** New fake room **QA-RF1** was created in the UI at rent 400,000 KHR. The property had capacity four and four rooms, so capacity was raised to five through the property UI before adding the room. This was an explicit fixture-only change; no existing tenancy was changed. Screenshot: [QA-RF1 vacant room](stage8-fix-11-room-created.png).
- **PASS:** New fake tenant **Stage8RefundTest**, phone `012345679`, was created in the UI after a read-only conflict check. Screenshots: [new tenant form](stage8-fix-17-tenant-filled.png), [tenant saved](stage8-fix-18-tenant-saved.png).
- **BLOCKED:** No test tenancy was completed. The last verified screen shows QA-RF1 still vacant; thus no refund entitlement, pending refund, or paid refund was created or claimed. The move-in workflow had selected the new tenant, but the form values and save were not completed. [Current verified emulator screen](stage8-fix-current.png). The positive/pending refund UI tests, post-restart persistence, and refund duplicate-attempt checks remain unrun.
- **BLOCKED:** Actual UI verification of deduction validation and the three refund display states remains outstanding. Automated evidence supports the repository/DAO rules but does not substitute for those requested UI checks.
- **PASS (preservation):** No UI path reopened Alpha or edited its records. Post-install read-only snapshot reconfirmed Alpha’s balances/settlement unchanged. QA-301 remains vacant and Alpha’s tenancy remains ended Dec 31, 2026.
- **PASS:** Recent Logcat scan on the verified emulator found no RoomKit `FATAL EXCEPTION` or new RoomKit crash. Current emulator date/time is automatic; no date change was made in this follow-up.

### Remaining Stage 8 status

Stage 8 is **not fully passed**. Positive and pending refund UI scenarios and their restart/duplicate checks remain blocked because the new test tenancy was not completed. No settlement receipt PDF was tested because project documents do not make one a requirement and the prior UI had no such action. Historical readings are retained in storage, while the earlier report’s post-move-out UI browsing limitation remains documented; the reviewed requirements do not explicitly require that UI path.

### Continuation attempt (2026-11-12)

- **PASS:** `adb devices -l` showed `emulator-5554` and `emu avd name` returned **RoomKit-Test**.
- **Blocker investigation:** QA-RF1 remained vacant and rent 400,000 KHR. The prior form state had not persisted; on reopening, its tenant field was blank. This is the exact reason the previous move-in was not completed: required tenant selection was missing, not a RoomKit validation message. The existing Stage8RefundTest (phone 012345679) was present and marked Available in the UI; no duplicate was created. Screenshots: [reopened QA-RF1](stage8-fix-movein-investigation.png), [available fake tenant](stage8-fix-tenant-picker.png).
- **BLOCKED at move-in review:** Stage8RefundTest was selected in the form and the room/date/rent showed QA-RF1 / Nov 12, 2026 / 400,000. However, the numeric keyboard remained open over the form, and the Review move-in action did not advance to review or produce a visible validation message. No move-in was confirmed, so the fixture remains without a tenancy and the requested settlement scenarios still cannot be performed. Screenshot: [move-in form with selected tenant](stage8-fix-movein-review.png). No other tenant was selected for confirmation and no tenancy/settlement record was created.
- **UNRUN:** UI deduction-without-reason, stale reason with zero deduction, zero-refund acknowledgement, positive pending refund, pending restart, refund completion, repeated confirmation, and paid restart checks. The earlier automated results remain the only evidence for code-path validation; they do not satisfy UI verification.
- **PASS (preservation):** Alpha was not opened for editing or settlement. Prior read-only verification after the app update remains the recorded evidence of Alpha’s 200,000 KHR remaining debt, unchanged settlement and ended tenancy. No date/time setting was changed in this attempt.
- **PASS:** Recent Logcat scan had no RoomKit `FATAL EXCEPTION` or new crash. Emulator remained on RoomKit-Test, emulator-5554.

The UI obstacle in this attempt was the still-open numeric keyboard preventing a reliable transition from the move-in form; no explicit application validation error was displayed. All refund checks remain **BLOCKED/UNRUN**, not passed.

### QA-RF1 move-in blocker resolution (2026-11-12)

This entry supersedes the prior continuation’s statement that the move-in Review action was blocked by the keyboard.

- Verified again: device serial `emulator-5554`, AVD **RoomKit-Test**. Current screen was the existing QA-RF1 move-in form for Stage8RefundTest; no duplicate fixtures were created.
- Android Back dismissed the numeric keyboard once and left the move-in form open. The selected tenant remained visible as **Stage8RefundTest**. The UI hierarchy showed rent 400000, deposit 400000, billing day 1, due day 5, and an enabled/clickable `btnConfirmMoveIn`.
- The Review tap initially missed because it did not use the button’s actual current accessibility bounds. Using the hierarchy’s current bounds opened the confirmation dialog. This was an interaction-coordinate error, not an application validation defect; no code change or new test was needed.
- **PASS: fixture move-in completed through UI.** Confirmation displayed tenant Stage8RefundTest, building Suzuki, room QA-RF1, move-in Nov 12 2026, rent/deposit 400000 KHR each, billing day 1, due day 5, electricity rate 1000 KHR/kWh, fixed water 20000 KHR/month, rubbish 10000 KHR/month, and initial electricity 0 kWh. After confirmation, QA-RF1 details showed Occupied, Stage8RefundTest, and deposit 400000 KHR. Screenshots: [form after Back](stage8-back-dismissed-keyboard.png), [review confirmation](stage8-after-review-tap.png), [completed move-in](stage8-movein-confirmed.png).
- The UI showed no existing invoice for Nov 2026. For the planned zero-additional-usage move-out, electricity baseline/final will be 0; rent is not automatically prorated and there is no previous debt, so a 400000 KHR deposit can result in a positive refund, subject to review’s actual calculation.
- **UNRUN:** No move-out settlement has yet been reviewed or saved for QA-RF1. Positive deduction reason rejection, stale reason with zero deductions, pending refund display/persistence, paid refund recording, duplicate prevention, paid-refund restart, and zero-refund acknowledgement checks remain **UNRUN**. The previous report’s earlier automated evidence is unchanged and is not UI evidence.
- **PRESERVATION:** Alpha’s existing settlement and 200000 KHR remaining December debt were not opened or edited. No manual database changes or clock changes were made. No commit or push was made.

### Refund-test continuation (2026-11-12)

- **PASS: emulator identity** remained RoomKit-Test / `emulator-5554`.
- **PASS: actual tenancy terms observed in move-in confirmation** were rent 400000, deposit 400000, billing day 1, due day 5, electricity 1000/kWh, fixed water 20000/month, rubbish 10000/month, initial electricity 0. QA-RF1 room details afterward showed Stage8RefundTest and 400000 deposit. There was no November invoice yet.
- **BLOCKED before move-out workflow:** after returning to QA-RF1 room details, `btnManageTenancy` was exposed as clickable/enabled in accessibility bounds `[63,1790][1017,1906]`; taps at its reported center did not visibly open the manage dialog in the captured screen. No move-out settlement was started or saved. This is not evidence of a product defect yet; touch delivery/coordinate mapping must be diagnosed in a subsequent continuation. No deduction or refund tests were attempted against an unreviewed settlement.
- Checks 1–9 (deduction reason validation/persistence, final-charge/refund calculation, pending state, restart persistence, explicit refund recording, duplicate prevention, paid restart, and zero-refund acknowledgement) remain **UNRUN**. Existing automated tests still cover repository/DAO behavior only; they are not UI passes.
- Alpha was not edited; emulator clock was not changed. The last Logcat filter found no RoomKit FATAL EXCEPTION. No code, database, unrelated record, commit, or push changes were made during this continuation.

### Manage Tenancy and deduction UI retry (2026-11-12)

- **PASS: Manage Tenancy navigation works.** On a fresh RoomKit-Test screenshot with QA-RF1 detail, the button was fully visible and enabled. A single tap at its current accessibility bounds opened the `Manage Tenancy` dialog (options `View Tenant Profile`, `Move Out`, `CANCEL`); choosing `Move Out` opened the form for Stage8RefundTest / QA-RF1. The prior apparent failure was due to screenshot/UI transition timing: the first captured frame showed the underlying room while the hierarchy already reflected the dialog. No code defect was found; no code was changed.
- **PASS: actual charge/refund preview inspected before confirmation.** Final electricity was set to the confirmed starting baseline of 0. The app displayed held deposit 400000, current unpaid 0, new final invoice 20000, existing debt 0, deposit applied 0, deductions 0, refund due 400000, and debt after settlement 20000. The 20000 final charge is the configured fixed-water fee for the unbilled November period; the screen says it is excluded only if that month was already billed. Rent is not automatically prorated. The preview therefore shows 400000 refund while leaving a separate 20000 final debt. This preview was not confirmed, and no settlement was saved.
- **PASS: positive deduction with blank reason was rejected by the UI review step.** Entered 1000 KHR deduction with the reason blank and tapped Review Settlement. The form displayed: **“Enter a meaningful reason for deposit deductions.”** The review did not advance. Screenshot: [reason validation](stage8-deduction-validation-result.png).
- **BLOCKED: zero deduction with stale reason persistence / pending refund flow.** A temporary reason was typed while the amount remained 1000; no settlement was saved. Dismissing the keyboard using Android Back returned to the room detail instead of merely hiding the keyboard at that point, so the test form state was lost. No move-out was completed. Consequently pending refund restart, explicit paid recording, duplicate protection, paid restart, and zero-refund acknowledgement UI checks remain **UNRUN**. The visible positive-reason rejection is the only new deduction UI result.
- **PRESERVATION:** QA-RF1 remains occupied by the same Stage8RefundTest tenancy, since move-out was never confirmed. No duplicate tenancy or fixture was created. Alpha’s settlement and 200000 KHR debt were not edited. No database writes or clock changes were made.
- **Logcat:** the single Manage Tenancy tap/menu navigation and move-out form produced no RoomKit fatal exception in the inspected logs. No crash is inferred as proof of navigation; navigation itself was visually verified through the menu and form.

### Refund completion and final verification (2026-11-12)

Target remained **RoomKit-Test / emulator-5554**. No clock setting was changed; `adb shell date` showed Nov 12, 2026 +07. No real payment was made.

| Requested check | Result | Evidence |
|---|---|---|
| Positive deduction without meaningful reason | **PASS** | Entered 1000 KHR with blank reason. Review remained on the form and showed “Enter a meaningful reason for deposit deductions.” [Validation screenshot](stage8-deduction-validation-result.png). |
| Zero deduction with stale reason | **PASS** | Entered temporary reason `Test damage`, then set deduction to 0. The reason field hid when amount became zero; review showed 0 deductions. After completion, read-only DB has `deductionsKhr=0` and `deductionReason=''`, confirming the stale text was not saved. [Zero input screenshot](stage8-zero-deduction-stale.png), [review screenshot](stage8-review-final-settlement-values.png). |
| Final charges and refund calculation before confirmation | **PASS** | Review displayed final invoice 20000 KHR (unbilled November fixed-water fee), existing debt 0, deposit applied 20000, deductions 0, refund due 380000, debt after settlement 0. Rent was not prorated. This matched the requested expected figures; only then was move-out confirmed. [Review screenshot](stage8-review-final-settlement-values.png). |
| Pending move-out settlement and final charge | **PASS** | Saved settlement for tenancy ID 5; final invoice ID 5 is FINAL 20000 KHR, marked PAID with 20000 paid and 0 balance. Exactly one active DEPOSIT payment exists for invoice 5: payment ID 8, 20000 KHR. Deposit application is represented once by that payment plus the settlement snapshot; refund is separate settlement state. [Saved pending screen](stage8-pending-refund-saved.png), [read-only DB query below]. |
| Pending amount/status after force-stop/reopen | **PASS** | After restart, reopening Move Out History showed invoice #5 paid, refund status **Pending — 380000 KHR**, held deposit 400000 and current balance 0. [Pending after restart](stage8-pending-after-restart.png). |
| Record simulated refund once | **PASS** | The app’s confirmation dialog stated 380000 KHR and date 12 Nov 2026. Recorded once with reference `TEST-RF1`. No external transfer was made. [Confirmation dialog](stage8-record-refund-dialog.png), [paid screen](stage8-refund-paid.png). |
| Repeated action/reopening duplicate prevention | **PASS** | After recording, the primary “Record Refund as Paid” action was absent; after restart and reopening, hierarchy still had no `btnPrimaryAction` or refund acknowledgement control. Read-only database shows one settlement row for tenancy 5 and `refundPaid=1`, with a single stored amount/reference. Thus no second refund action was available to repeat. |
| Paid status/details after restart | **PASS with UI formatting issue** | After a second force-stop/reopen, history showed status **Paid** and date 12 Nov 2026. DB confirms amount 380000, paid=1, timestamp present, reference `TEST-RF1`. UI concatenates the date and reference without a separating space (`2026TEST-RF1`); the stored values are distinct and correct. [Paid after restart](stage8-refund-paid-after-restart.png), [read-only DB query below]. |
| Zero-refund acknowledgement regression | **PASS (read-only UI inspection)** | Opened Alpha’s existing, unchanged Move Out History. UI showed “Refund status: No refund due”; accessibility hierarchy exposed neither `cbAcknowledge` nor `btnPrimaryAction`, so no refund-paid acknowledgement/action is shown for zero refund. No Alpha record was changed. [Alpha zero-refund history](stage8-alpha-zero-refund-history.png). |
| Alpha preservation | **PASS** | Read-only DB still shows tenancy 2’s settlement held/applied/refund 400000/400000/0, historical deduction text unchanged, and debt 200000. Invoices 2/3 remain 445000/0 and 440000/200000; final invoice 4 remains 0. QA-301/Alpha remains ended Dec 31, 2026. |
| Crashes/time | **PASS** | Final Logcat scan contained no RoomKit FATAL EXCEPTION. Emulator clock remained automatic/current Nov 12, 2026 +07; no time change was made. |

### Read-only database evidence

Exported DB/WAL/SHM with `run-as tar` to a host copy and queried with SQLite `mode=ro` and `PRAGMA query_only=ON`; no emulator DB writes were made.

```text
tenancy 2: finalInvoice 4; held 400000; applied 400000; deductions 0;
  legacy reason 'Charges: [newline] Deductions: -1'; refund 0; paid 0; debt 200000
tenancy 5: finalInvoice 5; held 400000; applied 20000; deductions 0;
  deductionReason ''; refund 380000; paid 1; refundPaidAt present;
  reference 'TEST-RF1'; debt 0
invoice 2: REGULAR 445000 / paid 445000 / balance 0 / PAID
invoice 3: REGULAR 440000 / paid 240000 / balance 200000 / PARTIALLY_PAID
invoice 4: FINAL 0 / paid 0 / balance 0 / ISSUED
invoice 5: FINAL 20000 / paid 20000 / balance 0 / PAID
payment 8: invoice 5 / DEPOSIT / 20000 / ACTIVE
active payments for invoice 5: count 1, sum 20000
tenancy 5: ENDED; roomId 5; exactly one move_out_settlements row
```

The refund itself is recorded on the settlement (`refundKhr`, `refundPaid`, timestamp, reference), not as a normal invoice payment. The final-charge deposit application is represented by the one invoice-linked DEPOSIT payment. No duplicate final charge or refund row was found.

### Read-only stale-reason evidence verification (2026-11-12)

- **PASS: saved deduction amount/reason.** Re-verified the current RoomKit-Test (`emulator-5554`, AVD **RoomKit-Test**) database by exporting a copy and querying SQLite in read-only/query-only mode. QA-RF1 / Stage8RefundTest is tenancy ID 5. Its saved settlement has `deductionsKhr=0` and `deductionReason=''` (empty string; schema column is non-null). No settlement fields were changed.
- **PASS: temporary text was tested before zeroing the deduction.** The Stage 8 interaction record states that `Test damage` was entered as the temporary reason, then the deduction amount was changed to zero; the reason field hid automatically at zero. The captured zero-input screen shows the deduction at 0 ([screenshot](stage8-zero-deduction-stale.png)); the preceding stale-reason entry is captured in [the reason-entry screenshot](stage8-deduction-stale-reason.png). The saved empty reason corroborates that stale text was not persisted.
- The stale-reason regression is **PASS**. This is verification only; the completed settlement remains unchanged.

All requested Stage 8 refund UI checks in this continuation passed, except the UI has a minor paid-detail formatting defect: missing separator between paid date and reference. Alpha’s old `-1` historical deduction reason remains untouched as required.
