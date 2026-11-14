# Stage 12 — Offline workflow, backup, restore and migration

**Date:** 2026-11-13 (+07)
**Source commit:** `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`
**Test app:** RoomKit `com.example.roomkit`, debug build installed in place on RoomKit-Test.
**AVDs:** `RoomKit-Test` / `emulator-5554`; disposable `RoomKit-Restore-Test` / `emulator-5556`.

The Stage 12 message ended with “The complete Stage 12 checklist follows:” but no additional numbered checklist was present. Results below cover the requirements and actions explicitly listed in that message.

## Requirements and implementation reviewed

- Read `README.md`, `docs/PHASE3_UPDATE.md`, `docs/PHASE3_VERIFICATION.md`, and the saved Stage 3–11 reports. The Phase 3 requirements describe encrypted JSON backup, password-protected restore, pre-replacement validation, rejection of wrong-password, truncated and inconsistent data, and lifecycle preservation. The older verification report recorded only a 10→11 migration and incomplete device coverage.
- Current Room database schema is version 12. Registered migrations cover 1→2 through 11→12. The new 11→12 migration adds `chargeReason`, `refundPaidAt`, and `refundReference` to `move_out_settlements`.
- Found a version mismatch in `JsonBackupRepository`: export declared schema 11 although the live Room schema was 12, and restore accepted only schema 11. Strict row-shape validation also meant an authentic schema-11 settlement row would not match the v12 entity shape.

## Change and automated evidence

- Fixed backup export to declare schema 12. Restore accepts schema 11 and 12; schema-11 settlement rows default the three later fields to empty/null values during restore. No existing historical settlement was rewritten.
- Added regression `schema11BackupRestoresIntoCurrentSchema12WithSettlementDefaults` in `app/src/testShared/java/com/example/roomkit/Phase3Checks.kt`. Existing automated coverage also passed for wrong-password rejection without replacing data, inconsistent authenticated backup rejection, truncated/tampered ciphertext rejection, and encrypted round-trip.
- Ran `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`: **BUILD SUCCESSFUL**. JUnit XML reports **54 tests, 0 failures, 0 errors**. The schema-11 restore compatibility and `migration10To11PreservesExistingRows` tests both passed. The migration test constructs a v10-shaped DB then exercises 10→11→12 and checks a pre-existing tenant, schema version 12, and the meter guard.
- Installed `app/build/outputs/apk/debug/app-debug.apk` with `adb -s emulator-5554 install -r`; install succeeded without uninstall or data clear. The app opened after update; captured in `stage12-app-updated-launch.png`. No new RoomKit fatal exception appeared in the post-update Logcat capture (`stage12-post-update-logcat.txt`).

## Actual UI and device evidence

| Check | Result | Evidence |
|---|---|---|
| Emulator identity | **PASS** | `adb -s emulator-5554 emu avd name` returned `RoomKit-Test`; restore device returned `RoomKit-Restore-Test` on `emulator-5556`. Every device operation was serial-targeted. |
| Offline application workflow | **PASS** | Turned airplane mode on in Android Quick Settings, verified offline status, created the permitted Stage 12 fixtures and tenancy through RoomKit UI, then navigated the app offline. Screens: `stage12-airplane-on.png`, `stage12-offline-property.png`, `stage12-offline-tenancy-created.png`. No invoice/payment was created. |
| More than 10 rooms | **PASS** | Through UI, increased Suzuki’s configured capacity only from 8 to 11 and created vacant QA-1201/1202/1203 (room IDs 9/10/11). UI evidence: `stage12-capacity-saved.png`, `stage12-room1201-created.png`, `stage12-room1202-created.png`, `stage12-room1203-created.png`. |
| Dedicated tenant/tenancy | **PASS** | Created `Stage12_Offline_Backup_Tenant` (tenant ID 13; phone redacted in this report), identity `STG12-BACKUP-001`, and moved into QA-1201 (tenancy ID 11, room ID 9) through the UI. The 400,000 KHR deposit was inherited from the move-in form default. Screens: `stage12-tenant-saved.png`, `stage12-movein-review.png`, `stage12-offline-tenancy-created.png` (local-only because they show profile data). |
| Encrypted backup creation | **PASS** | Used the in-app “Save encrypted backup” UI while offline. The encrypted backup is a **local-only artifact** (`roomkit-stage12-encrypted-backup-2026-11-13.json`; 54,496 bytes; SHA-256 `dd64a633d30afb5c61e3532617d97ef77b57ecad866860dde53d1bb82835d191`). A byte search did not find the fake tenant name. Its password handoff is also local-only (`stage12-password-handoff.txt`, mode 0600); neither file is included in this report commit. |
| Restore UI confirmation | **PASS** | On the disposable restore AVD only, opened Restore and accepted the replace-data warning. No restore was completed. Evidence: `stage12-restore-open-picker.png`, `stage12-restore-password-dialog.xml`. |
| Wrong-password restore | **NOT TESTED** | Entered a wrong password in the restore dialog, but did not pair it with a valid RoomKit JSON payload. A valid-payload selection was not completed, so wrong-password rejection is unverified. The captured password-field text in `stage12-restore-password-focused.xml` and `stage12-restore-wrong-password-focus.xml` is redacted. |
| Unrelated-file restore | **PASS** | Through the supported picker, selected `not-a-backup.txt`. RoomKit returned a visible parse error (`Value This of type java.lang.String cannot be converted to JSONObject`) and stayed on the backup page. Read-only destination DB remained schema 12 with 0 buildings, rooms, tenants, tenancies, invoices, or payments. Screens: `stage12-restore-picker-documents-stage12.png`, `stage12-restore-unrelated-result2.png`; hierarchy `stage12-restore-unrelated-result.xml`; DB snapshot `/private/tmp/stage12-restore-after-unrelated.db` and WAL/SHM companions. |
| Corrupted-copy restore | **BLOCKED** | A separate corrupted JSON copy exists on the restore emulator, but could not be selected through the picker. No restore attempt occurred. |
| Valid restore / baseline comparison | **NOT TESTED** | Valid backup was not selectable through the picker. Consequently, replacement, valid restored records, and restore-time data comparison were not exercised. |
| Destination preservation after rejected restore | **PASS for no-op state only; rejected restore cases NOT TESTED** | Before/after attempted navigation, the disposable destination remained empty (0 buildings, rooms, tenants, tenancies, invoices, and payments; schema version 12), read from a copied DB/WAL/SHM using SQLite `mode=ro`. No rejected restore itself was invoked. Snapshot: `/private/tmp/stage12-restore-after-attempt.db` and companion WAL/SHM. |

Test files were staged on `emulator-5556` under `/sdcard/Download/Stage12` and copies under `/sdcard/Documents/Stage12`. `ls` and a read-only MediaStore query confirmed both JSON files (`application/json`) and the unrelated file (`text/plain`). The Documents provider displayed the `.txt` file but omitted the JSON files, so the unrelated-file rejection could be exercised while valid and corrupted JSON selections could not. The disposable destination remained empty and was not replaced.

## Read-only original-device database evidence

Baseline before Stage 12: schema 12; 8 rooms, 12 tenants, 10 tenancies, 27 readings, 12 invoices, 12 payments, and 3 settlements. At backup time, after authorized UI setup: 11 rooms, 13 tenants, 11 tenancies, 28 readings, while the protected billing tables remained at 12 invoices, 12 payments, and 3 settlements. Suzuki capacity is 11.

The final read-only snapshot of RoomKit-Test confirms protected invoices remain unchanged:

| Invoice ID | Total KHR | Paid KHR | Balance KHR | Status |
|---:|---:|---:|---:|---|
| 2 (Alpha Nov) | 445,000 | 445,000 | 0 | PAID |
| 3 (Alpha Dec) | 440,000 | 240,000 | 200,000 | PARTIALLY_PAID |
| 4 (Alpha final) | 0 | 0 | 0 | ISSUED |
| 6 (Nine A Nov) | 140,000 | 0 | 140,000 | ISSUED |
| 7 (Nine A final) | 5,000 | 0 | 5,000 | ISSUED |
| 8 (Nine B Nov) | 245,000 | 45,000 | 200,000 | PARTIALLY_PAID |
| 9 (Stage 10) | 140,000 | 40,000 | 100,000 | PARTIALLY_PAID |
| 12 (Stage 10) | 439,000 | 0 | 439,000 | ISSUED |

The read-only final DB snapshot is `/private/tmp/stage12-original-final.db` with WAL/SHM companions. It confirms 11 rooms, 13 tenants, 11 tenancies, 28 readings, 12 invoices, and 12 payments. Alpha’s remaining 200,000 KHR, Nine A’s 145,000 KHR, Nine B’s 200,000 KHR and 50,000 KHR held deposit, invoice 9’s 100,000 KHR balance, and invoice 12’s 439,000 KHR balance are preserved.

## Migration scope

- **Isolated migration:** **PASS**, 10→11→12, automated test as above.
- **Actual older APK update:** **PASS, empty schema-11 database only.** Created a third blank AVD `RoomKit-Migration-Test` / `emulator-5558`. Built the repository HEAD APK (schema 11), installed and launched it, and confirmed the DB `user_version=11`. Installed the current schema-12 debug APK with `adb install -r`, without uninstalling or clearing data, relaunched, and confirmed `user_version=12` plus the three new settlement columns. The app dashboard opened and no migration-related fatal/Room verification errors were found (`stage12-migration-v11-fresh-install.png`, `stage12-migration-v12-dashboard.png`, `stage12-migration-upgrade-schema.txt`, `stage12-migration-upgrade-crashes.txt`). This actual APK upgrade used an empty database; data-preserving populated migration is evidenced by the separate automated 10→11→12 test, not by this device upgrade.
- RoomKit-Test received only the in-place current debug APK update. Its original DB version remained 12.

## Settings, app integrity, and remaining stage status

- RoomKit-Test original automatic time and time zone remained enabled (`auto_time=1`, `auto_time_zone=1`); the device date was not changed. Airplane mode was restored off. During quick-settings restoration, Do Not Disturb was inadvertently toggled; it has since been explicitly returned to off (`zen_mode=0`). Wi-Fi/mobile connectivity returned; visible status bar showed Wi-Fi and T-Mobile service. Restore AVD data was not modified.
- No app data was cleared, no uninstall occurred, and no emulator database was manually edited. No real transactions, commit, or push occurred.
- Stage 10 natural reminder delivery remains **NOT TESTED** as recorded in `stage10-reminders-results-2026-11-12.md`; Stage 12 does not close that gap. Other prior stages retain their own UI-unavailable and NOT TESTED limitations documented in their reports (including Stage 3 negative-input UI restrictions, Stage 5 future-rate editing, and Stage 9 historical meter UI access).
- Stage 12 is **INCOMPLETE**: valid restore, wrong-password rejection, corrupt/unrelated-file rejection through UI, and real older-APK upgrade remain unverified. The app’s restore workflow needs a selectable-file test in the Android document picker before these can pass.

## Continuation results — 2026-11-14 (+07)

This addendum supersedes the earlier restore and offline-workflow statuses above where they conflict. It records the follow-up authorized on 2026-11-14. Tested source commit remains `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`; the working tree contains extensive pre-existing local edits. This continuation extended `Phase3Checks.kt` with populated migration coverage and did not make a production-code change. No commit or push was made.

### Current-build backup and populated restore

- **AVD identity: PASS.** Immediately before final verification, `emulator-5554` returned `RoomKit-Test`; `emulator-5556` returned `RoomKit-Restore-Test`. Every ADB command used an explicit serial. `RoomKit-Migration-Test` was not connected during this continuation.
- **Backup selection: PASS.** The in-app encrypted-backup flow produced JSON in Downloads/RoomKit. The normal document picker, using `*/*`, showed and selected it. The earlier picker obstacle was the selected Documents provider/location not listing JSON, not a confirmed MIME-filter defect; no picker bypass or picker code fix was used.
- **Current tested build backup: PASS.** [`roomkit-stage12-encrypted-backup-2026-11-14.json`](roomkit-stage12-encrypted-backup-2026-11-14.json), 54,479 bytes, SHA-256 `3876b7b61bd5b8699ff069d44a86e6824f052cae995d7c11848fafbbd53b97b4`. Created through the app UI on RoomKit-Test before the offline invoice/payment below. The disposable password is only in the local mode-0600 handoff file; it is not reproduced here or in screenshots.
- **Overwrite cancellation: PASS.** On the populated restore AVD, selected the backup, reviewed the overwrite warning, canceled, and compared read-only table counts; destination records remained intact. Evidence: `stage12-current-file-picker.png`, `stage12-current-restore-warning-cancel.png`.
- **Successful restore: PASS.** Selected the same backup through the normal picker, confirmed replacement, entered the handoff password, and received the app’s “Restore complete” confirmation (`stage12-restore-current-result.png`). After force-stop/reopen (`stage12-current-restore-restart.png`), read-only DB rows matched the decrypted backup payload exactly: 1 building, 11 rooms, 13 tenants, 11 tenancies, 28 readings, 12 invoices, 72 invoice items, 12 payments, 3 settlements, and zero rows in the three billing-period tables. Foreign-key check returned no violations. The restore was a full replacement; no sentinel was present in this backup’s baseline. The app’s replacement confirmation explicitly states that rental and financial records are replaced.
- **Failed-restore safety on populated destination: PASS.** Earlier this continuation, wrong password, unrelated file, and truncated/corrupt backup copy were each attempted through the UI against a populated destination; after each, the destination’s baseline rows and sentinel remained unchanged. The valid backup file was not modified; corruption was tested with a copy. Evidence includes `stage12-wrong-password-result.png`, `stage12-unrelated-file-populated-result.png`, and `stage12-corrupted-populated-result.png`, with read-only comparison snapshots retained under `/private/tmp` during the run. Picker cancellation and overwrite-warning cancellation also left the baseline unchanged. These checks supplement, rather than replace, the current-build successful restore above.
- **Backup encryption: PASS.** Existing code-level/automated evidence remains applicable: PBKDF2-HMAC-SHA256 with 210,000 iterations derives the key; AES-256-GCM encrypts the payload with a 16-byte salt and 12-byte IV. Wrong-password, authenticated-tamper/inconsistent-payload, truncation, and round-trip tests passed. The handoff password is excluded from versioned evidence.

### Populated migration coverage

- **Isolated populated migration 10→11→12: PASS.** `migration10To11PreservesExistingRows` now migrates a representative tenant, room, active/ended tenancy, initial/monthly/final meter history, regular and final invoices/items, partial payment, and pending-refund settlement. It verifies IDs, links, amounts, history, v11/v12 defaults, schema version 12, a meter guard, and insertion of a new tenant after migration.
- **Automated build/tests: PASS.** The focused migration test passed. Then `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain` succeeded: 54 tests, zero failures/errors. Output retained in `stage12-build-tests-2026-11-14.txt`.
- **Actual populated old-APK upgrade: NOT TESTED.** The prior RoomKit-Migration-Test device evidence was an empty schema-11 upgrade only. This continuation did not claim it preserves populated financial history.

### Offline workflow and reliability evidence

- **Offline tenant/room/move-in: PASS (earlier UI evidence).** This continuation did not repeat these saved transactions.
- **Offline reading, invoice, payment, restart: PASS (UI).** On RoomKit-Test, QA-1201 / room 9 and Stage12_Offline_Backup_Tenant / tenant 13 / tenancy 11 were used. With Wi-Fi and mobile data disabled in Quick Settings, saved November electricity 100→110 (10 kWh). Preview showed rent 100,000 + electricity 10,000 + fixed water 20,000 + rubbish 10,000 = 140,000 KHR, due December 5, 2026. Issued invoice ID 14 / number 0014, then recorded exactly one simulated 40,000 KHR Cash payment. UI showed 100,000 KHR balance. Screens: `stage12-reading-saved.png`, `stage12-invoice-prep.png`, `stage12-offline-draft-saved.png`, `stage12-issue-confirm.png`, `stage12-invoice-issued.png`, `stage12-payment-saved.png`.
- **Offline persistence: PASS (UI + read-only DB).** Force-stop/reopen and device reboot returned to the app. Read-only DB showed invoice 14 total 140,000, paid 40,000, balance 100,000, PARTIALLY_PAID; payment 13 was active Cash for 40,000; the tenancy’s initial 100 and monthly 110 readings remained. Screens: `stage12-offline-reopened.png`, `stage12-after-device-reboot.png`.
- **Room count/search: PASS (UI).** Property view showed 11 actual rooms under configured capacity 11; QA-1201 search returned its matching card (`stage12-property.png`, `stage12-room-search.png`, `stage12-search-settled.png`). Repeated full-list scrolling was not separately timed/recorded: **NOT TESTED**.
- **Form rotation: NOT TESTED.** Rotation was not performed on unsaved tenant, meter, draft, or payment forms in this continuation.
- **History protection: PASS for restore warnings.** Canceling both file selection/overwrite confirmation and failed restores did not replace destination data. Other destructive-history rules retain the prior report’s coverage and limitations.

### Final integrity and environment

- Post-restore table comparison and post-restart DB inspection confirmed the current backup baseline. The newly created offline invoice/payment are on the original RoomKit-Test and are separate from the restored destination.
- Protected source balances remain unchanged: Alpha 200,000 KHR due; Nine A 145,000 KHR due; Nine B 200,000 KHR due with 50,000 KHR deposit held; invoice 9 balance 100,000 KHR; invoice 12 balance 439,000 KHR. The only intended financial additions in this continuation were invoice 14 and payment 13 on the dedicated Stage 12 tenancy.
- Final source-device settings: automatic time `1`, automatic time zone `1`, airplane mode `0`, mobile data `1`, Wi-Fi enabled and connected. No clock/time-zone changes were made; network was restored after offline testing. Restore AVD is disposable and contains the successful replacement result.
- Sanitized Logcat captures: `stage12-roomkit_test-logcat-sanitized.txt` and `stage12-restore_test-logcat-sanitized.txt`; no RoomKit `FATAL EXCEPTION` or ANR entries were found in those captures. No password is included in the sanitized artifacts.
- Stage 10 natural delivery remains **NOT TESTED**. It is not closed by Stage 12.

### Remaining Stage 12 gaps

- Populated real older-APK device upgrade: **NOT TESTED** (isolated populated migration passed; prior actual device upgrade was empty).
- Unsaved-form rotation coverage for tenant, meter, draft, and payment: **NOT TESTED**.
- Repeated scrolling timing: **NOT TESTED**.
- Stage 10 natural reminder delivery: remains **NOT TESTED** in its own report.

Stage 12 backup creation, current-build populated restore/restart, restore rejection safety, encryption evidence, offline billing workflow, and populated isolated migration are now evidenced as passed. The project is not declared fully passed while the gaps above and prior-stage requirements remain open.

## Continuation results — 2026-11-14, follow-up rotation and room-list coverage

This continuation reused the saved Stage 12 report and fixtures. It made no source-code changes, performed no financial save, and did not repeat any completed restore, migration, or payment operation. The tested source commit is unchanged (`167b552419e2d4852356cc2eb2f5a91a7c9bd25f`); unrelated working-tree changes were preserved. No commit or push was made.

### UI evidence from RoomKit-Test

The AVD was verified as `RoomKit-Test` on `emulator-5554` before the UI session. The disposable restore AVD had previously been verified as `RoomKit-Restore-Test` on `emulator-5556`. A migration AVD was not connected in this continuation. All emulator commands that ran were explicitly serial-targeted.

| Check | Result | Evidence / limits |
|---|---|---|
| Unsaved tenant form rotation | **PASS** | Entered `Stage12 Rotation Check`, phone value redacted in committed report, ID `STG12-ROT-001`; rotated to landscape and back. All values remained visible. Canceled the form; tenant count remained 13. `stage12-rotation-tenant-landscape-lower.png`, `stage12-rotation-tenant-back-portrait.png`, `stage12-rotation-tenant-cancelled.png`. No tenant was created. |
| Unsaved meter form rotation | **PASS for input retention; saved-record post-check NOT TESTED** | On Room 01 / existing tenant `babab`, entered current electricity 15 against previous 0, rotated to landscape and back, and saw the value/calculated usage remain. Left using the screen back action without tapping Save. `stage12-rotation-meter-unsaved-portrait.png`, `stage12-rotation-meter-entered.png`, `stage12-rotation-meter-landscape.png`, `stage12-rotation-meter-return.png`. A post-exit read-only DB check could not be made after emulator access was blocked; no save action was issued. |
| Unsaved draft-invoice rotation | **BLOCKED** | Not reached. The next emulator command was rejected by automatic approval review due to the account usage limit; no alternate ADB route was attempted. |
| Unsaved payment form rotation | **BLOCKED** | Not reached for the same emulator-access restriction. No payment was entered or saved. |
| Room count above 10 and capacity | **PASS** | Starting UI showed 11 rooms. Increased configured Suzuki capacity from 11 to 12 through the UI and created vacant QA-1204 with 100,000 KHR/month. The successful-save toast and QA-1204 card are visible together immediately after returning to the list, without leaving Property or reopening it. Approximate appearance was within the first returned screen (about one second; not instrumented). Evidence: `stage12-capacity-12-saved.png`, `stage12-room1204-immediate-return.png`. UI now showed at least 12 rooms under capacity 12. |
| Search, occupancy filters, repeated scrolling | **PASS for pre-add list; post-add recheck NOT TESTED** | On the original 11-room list, All showed 11, Occupied showed 8, and Vacant showed 3; screenshots: `stage12-longlist-start.png`, `stage12-longlist-occupied-filter.png`, `stage12-longlist-vacant-filter.png`. Repeated scroll screenshots show the lower QA-1202/1203/QA-301/302/303 rows: `stage12-longlist-scroll-1.png`, `stage12-longlist-scroll-2.png`. Searching QA-302 produced a matching result (`stage12-longlist-search-QA302.png`, `stage12-longlist-QA302-result.png`). After QA-1204 was added, a fresh All/Occupied/Vacant/search-QA-1204 pass was not completed. |
| Opening multiple room cards | **PASS** | Opened QA-1202, QA-302, Room 01, and QA-RF1 during this continuation; captured `stage12-open-room-1202.png`, `stage12-QA302-detail.png`, `stage12-room01-detail.png`, and `stage12-meter-tenancy-detail.png`. |
| Existing-record protection | **PASS for actions performed** | QA-1204 was checked for absence before creation. Tenant rotation was canceled; meter rotation was exited without Save. Protected invoice #8 was opened for inspection only. The final attempted back tap on that invoice screen was rejected before execution; it caused no invoice change. No financial forms were saved in this continuation. |
| Empty states / destructive-history controls / crash and ANR audit | **NOT RECHECKED in this continuation** | Prior Stage 12 restore-warning and populated-destination rejection evidence remains as recorded above. This continuation did not retest unrelated deletion/history controls or empty states. Existing sanitized Logcat files are from the earlier run; no fresh post-rotation crash/ANR capture was possible after the access restriction. |
| Populated older-APK upgrade | **NOT TESTED** | Local APK discovery found only `app/build/outputs/apk/debug/app-debug.apk`; no suitable older APK artifact was available. Keep the passing isolated populated migration result separate from the earlier empty-database schema-11 device upgrade. |
| Stage 10 natural reminder delivery | **NOT TESTED** | No natural delivery observation was performed; status remains open in the Stage 10 report. |

### Interruption and settings

Before the rotation checks, the emulator’s original orientation settings were `accelerometer_rotation=1` and `user_rotation=0`. To perform the supported UI rotation checks, auto-rotate was disabled and the device was rotated to landscape and back to portrait, leaving `accelerometer_rotation=0` and `user_rotation=0`. Restoring auto-rotate was attempted after the emulator command began requiring approval, but automatic approval review rejected that command because the account usage limit had been reached. No command was executed by that rejected call, and no alternate access route was used. **Auto-rotate restoration is BLOCKED; current value is not re-verified after the rejected attempt.** Date, time, time zone, and network were not changed in this continuation. This restoration gap must be resolved by restoring `accelerometer_rotation=1` on RoomKit-Test and confirming it before further UI tests.

### Integrity and overall status

No evidence indicates a change to Alpha, Nine A, Nine B, invoices 9 or 12, or existing reminder claims. Previously verified balances remain the last verified values: Alpha 200,000 KHR due; Nine A 145,000 KHR due; Nine B 200,000 KHR due with 50,000 KHR deposit held; invoice 9 balance 100,000 KHR; invoice 12 balance 439,000 KHR. This continuation did not perform a new database snapshot, so these are carried-forward evidence, not a fresh read.


## Continuation results — 2026-11-14, rotation and post-add verification

This continuation closed the meter post-exit, draft/payment rotation, post-add search/filter, and auto-rotate restoration checks. It created no new financial record and did not repeat any saved financial transaction. Source commit remains `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`; working-tree implementation changes were not altered in this continuation.

### Emulator identity and settings

- **AVD/serial: PASS.** `RoomKit-Test` was verified on `emulator-5554` before any device operation. The only targeted device during this continuation was `emulator-5554`.
- **Before testing:** auto-rotate was off (`accelerometer_rotation=0`, `user_rotation=0`) from the prior rotation session. Automatic time and time zone were on, airplane mode was off.
- **Restored and reverified:** auto-rotate `1`, portrait `user_rotation=0`; automatic time `1`; automatic time zone `1`; airplane mode `0`; mobile data `1`; Wi-Fi `1`, connected to the emulator’s `AndroidWifi`; DND `zen_mode=0`. Emulator time at final check: `Sat Nov 14 12:37:42 +07 2026`. No date, time-zone, network, notification permission, or DND setting was changed in this continuation. Auto-rotate restoration is now **PASS**.

### UI and read-only database checks

| Check | Result | Evidence |
|---|---|---|
| Meter record after rotated form exit | **PASS — no unintended save** | On Room 01 / tenancy 1, the form had a temporary unsaved current value 15. It was exited without Save. Final read-only DB query shows exactly one electricity record for that room: `INITIAL 0→0`; no monthly record. `stage12-resume-readonly-db.txt`; copied DB/WAL/SHM at `/private/tmp/stage12-resume-roomkit-final.db*`. |
| Unsaved draft form rotation | **PASS — retained values; no draft persisted** | Used QA-302 / tenancy 3 / Test Tenant Water’s existing November readings and no existing invoice. The preview showed 427,000 KHR before adjustment. Entered temporary other charges 5,000 KHR; the displayed total became 432,000 KHR and the screen said “Unsaved changes.” Landscape and portrait screenshots show the 5,000 input retained. Exited through Back without Save/Issue. Final DB has zero QA-302 November invoices and zero saved draft adjustments. `stage12-draft-unsaved-before-rotation.png`, `stage12-draft-unsaved-entered.png`, `stage12-draft-rotation-landscape.png`, `stage12-draft-rotation-portrait.png`, `stage12-draft-preview-exited.png`. |
| Unsaved payment form rotation | **PASS — retained value; no payment persisted** | Opened the existing protected Nine B invoice #8 only to inspect its form. Confirmed total 245,000, paid 45,000, balance 200,000 KHR. Changed the unsaved form amount from its default to 12,345 KHR, rotated landscape and portrait, and verified 12,345 remained. Dismissed the form without Save. Read-only DB still shows invoice #8 at 245,000/45,000/200,000 and one existing payment. `stage12-invoice8-for-payment-rotation.png`, `stage12-payment-unsaved-entered.png`, `stage12-payment-rotation-landscape.png`, `stage12-payment-rotation-portrait.png`, `stage12-payment-form-dismissed.png`. |
| Post-add room filters and search | **PASS** | After QA-1204 was created, the list showed All 12, Occupied 8, Vacant 4. Occupied and Vacant filters displayed 8 and 4; search `QA-1204` returned its vacant card at 100,000 KHR/month. `stage12-postadd-room-list.png`, `stage12-postadd-occupied.png`, `stage12-postadd-vacant.png`, `stage12-postadd-search-QA1204.png`. |
| Post-add repeated scrolling | **PASS** | Two more scroll positions were captured after creation; the lower list contains QA-1204, QA-301 and subsequent room cards. `stage12-postadd-scroll-1.png`, `stage12-postadd-scroll-2.png`. |
| App integrity / crashes | **PASS for this observed interval** | RoomKit was force-stopped/reopened during the DB check and UI tests completed without an app crash. Final filtered Logcat query found no RoomKit FATAL EXCEPTION or RoomKit ANR. A native fatal signal line was present for the unrelated Android `droid.bluetooth` process (PID 1020); it is not attributed to RoomKit. |

### Final integrity snapshot

Read-only database checks after the UI tests confirm Room 01 has no monthly reading, QA-302 has no November invoice/draft adjustment, invoice #8 retains its one existing payment, and totals remain 12 rooms, 13 invoices, and 13 payments. Protected invoice values are in `stage12-resume-protected-financial-check.txt`: Alpha November 0 due; Alpha December 200,000 due; Nine A regular/final 140,000/5,000 due; Nine B 200,000 due; invoice 9 100,000 due; invoice 12 439,000 due; dedicated Stage 12 invoice 14 100,000 due. No evidence of a change to protected records.

The current working tree was reviewed and `git diff --check` passed. `./gradlew :app:testDebugUnitTest :app:assembleDebug --rerun-tasks --console=plain` completed **BUILD SUCCESSFUL**; 54 tests, 0 failures, 0 errors, 0 skipped. Output included existing Room KSP unused-column and Android deprecation warnings. No production source changes were made in this continuation.

### Migration coverage review

- `AppDatabase` registers the full sequential migration chain 1→2 through 11→12.
- **Populated isolated automated path 10→11→12: PASS** from earlier evidence; this verifies representative tenant, room, active/ended tenancy, readings, invoices/items, payment, settlement, defaults, links, and post-migration insertion.
- **Actual APK upgrade on an empty schema-11 DB: PASS** from earlier evidence only.
- **Populated older-APK device upgrade: NOT TESTED.** The Git base contains schema-11 source, but no migration emulator is currently connected (`adb devices` showed only RoomKit-Test and RoomKit-Restore-Test). The local emulator executable reported “Incompatible processor” when queried for AVD management, so a disposable populated-upgrade device could not be started in this environment. The automated migration result is not being used as a substitute for device-upgrade evidence.
- No suitable standalone older APK artifact is present in the project build outputs; only the current debug APK exists there.

Stage 12 remains **INCOMPLETE** because the populated old-APK upgrade and previously listed history/deletion-protection and empty-state checks remain unverified. Stage 10 natural reminder delivery remains **NOT TESTED**. No commit or push has occurred in this continuation.

### Final review and branch handoff — 2026-11-14

The source and test changes already present on the feature branch were reviewed with `git diff --check` and the debug unit-test/build run recorded above. They were committed as `a175457` (`Fix tenancy billing and backup workflows`) and pushed to `origin/fix/tenant-and-workflow-qa`. The Stage 12 QA report, screenshots, database snapshots, and backup/password handoff remain local and were not staged or pushed. No saved financial transaction was repeated in the final continuation. Stage 12 remains incomplete for the gaps listed above; Stage 10 natural delivery remains **NOT TESTED**.

## Final continuation — history protection, empty states, and older APK investigation — 2026-11-14

This section records the follow-up to the saved Stage 12 report. It supersedes the preceding continuation’s remaining status for room deletion protection and the listed empty states. No Alpha, Nine A/B, invoice 9, invoice 12, or other protected financial record was changed. A dedicated UI fixture was created and ended on QA-1204 solely to exercise ended-tenancy history protection.

### Emulator and settings

- Verified `RoomKit-Test` / `emulator-5554` before UI actions; `RoomKit-Restore-Test` / `emulator-5556` was the only other connected AVD. All UI/database commands targeted `emulator-5554`. `RoomKit-Migration-Test` remained disconnected.
- Final RoomKit-Test state: date `Sat Nov 14 13:16 +07 2026`, zone `Asia/Phnom_Penh`; automatic time/time zone enabled; auto-rotate enabled, portrait; airplane mode off; Wi-Fi and mobile data enabled; DND off. No device setting was changed during this continuation.
- A recent Logcat scan contained no RoomKit fatal exception or RoomKit ANR entry. This is an interval check, not a claim about all past runs.

### UI checks

| Check | Result | Evidence |
|---|---|---|
| Room search empty state | **PASS** | Search `NO-SUCH-STG12` displayed “No rooms yet.” Screenshot: [room-search-empty](stage12-room-search-empty.png). |
| Tenant search empty state | **PASS** | Search `NO-SUCH-STG12` displayed “No tenants found” and “Try another name, phone number or ID/passport.” Screenshot: [tenant-search-empty](stage12-tenant-search-empty.png). |
| Billing empty state | **PASS** | Changed only the Billing month selector from November to October 2026. It displayed 0 invoices and “No matching invoices for Oct 2026.” Returned the selector to November 2026. Screenshot: [billing-empty-month](stage12-billing-prev-month.png). |
| No-vacant-room state | **PASS** | After moving the dedicated QA-1204 test tenant in, the vacant-room filter displayed “No vacant rooms.” The room count remained 12; the test tenancy was subsequently ended. Evidence retained locally: `stage12-movein-deleteguard-complete.png`. |
| Destructive warning cancellation | **PASS** | Opened the Delete Room confirmation for history-bearing QA-1201, captured the warning, and canceled. The room and tenancy remained intact. Screenshot: [delete-warning](stage12-delete-history-warning.png). |
| Active-tenancy deletion protection | **PASS** | Confirmed Delete on dedicated QA-1201. The app refused with “This room currently has an active tenant. Move the tenant out before removing the room.” The form stayed open and QA-1201 remained occupied. Screenshot/hierarchy: [active-room delete refusal](stage12-delete-result-top.png), `stage12-delete-result-top.xml`. |
| Ended-tenancy history deletion protection | **PASS** | Created tenant ID 14 `Stage12DeletionGuard` (unique UI-checked phone; identifying contact values intentionally omitted from the committed report), moved into vacant QA-1204, then moved out the same day with final reading 0. Review showed final invoice 0, existing debt 0, deposit applied 0, deductions 0, refund 0, remaining debt 0. After return to the room card, QA-1204 was vacant. Confirming Delete then produced “This room has rental history and cannot be permanently deleted. Archive it instead.” The edit screen remained open. Screenshots/hierarchy retained locally: `stage12-moveout-review-lower.png`, `stage12-moveout-deletionguard-completed.png`, `stage12-qa1204-after-moveout.png`, `stage12-qa1204-delete-guarded.png`, `stage12-qa1204-delete-guarded.xml`. |
| Tenant and settlement history after restart | **PASS** | Force-stopped/reopened RoomKit, opened Stage12DeletionGuard’s tenant profile, observed the ended QA-1204 tenancy (14 Nov 2026), then opened Move Out History. UI showed invoice #15, ID 15, issued for 0 KHR and “Refund status: No refund due.” Identifying profile screenshot/hierarchy are local-only: `stage12-deletionguard-profile.png`, `stage12-deletionguard-profile.xml`, `stage12-deletionguard-settlement.png`, `stage12-deletionguard-settlement.xml`. |

### Read-only database evidence and protected values

The read-only source DB/WAL/SHM copies are retained in `/private/tmp` only and are not included in version control. A sanitized query summary is committed separately. It showed QA-1204 as vacant (room ID 12), tenant ID 14, ended tenancy ID 12, zero deposit, settlement ID 4 with zero applied/deductions/refund/debt, and zero-value issued final invoice ID 15. Totals after this authorized fixture were 12 rooms, 14 tenants, 12 tenancies, 31 meter readings, 14 invoices, 13 payments, and 4 settlements. The UI/DB history is retained; the room itself was not deleted or archived.

The post-check read-only snapshot preserved the protected invoice values: ID 2 445,000/445,000/0; ID 3 440,000/240,000/200,000; ID 4 0/0/0; ID 6 140,000/0/140,000; ID 7 5,000/0/5,000; ID 8 245,000/45,000/200,000; ID 9 140,000/40,000/100,000; ID 12 439,000/0/439,000 (total/paid/balance KHR). Alpha, Nine A, and Nine B settlement rows were unchanged. Full database files and record-level private query output remain local-only.

### Older APK and populated device upgrade

- A schema-11 APK **was available to build**: repository revision `167b552` was exported into an isolated `/private/tmp` checkout and `:app:assembleDebug` completed successfully. The APK SHA-256 was `6eb76cf4df34334937500f4b70e7f763a9620924d60b12f7cd76a6ac49ff0aae`; source `AppDatabase` at that revision declares schema 11. The built APK is retained in `/private/tmp` only.
- **Populated in-place device upgrade: NOT TESTED.** The AVD catalog contains `RoomKit-Migration-Test`, but starting it on port 5558 failed before boot. The emulator reported `Incompatible processor. This Qt build requires the following features: neon`; a headless/offscreen launch also exited without creating a device. `adb devices -l` continued to show only RoomKit-Test and RoomKit-Restore-Test. No device was installed, cleared, or modified for this attempted upgrade. This does not replace the earlier passing isolated populated 10→11→12 migration test, nor the empty schema-11 device upgrade.

### Evidence packaging, remaining gaps, and overall status

- The final QA commit includes this report, `stage12-final-readonly-integrity-summary.txt`, `stage12-older-apk-upgrade-attempt.txt`, `stage12-final-device-settings-logcat.txt`, and these sanitized screenshots: [room search empty](stage12-room-search-empty.png), [tenant search empty](stage12-tenant-search-empty.png), [October billing empty](stage12-billing-prev-month.png), [delete warning](stage12-delete-history-warning.png), [active-tenancy delete refusal](stage12-delete-result-top.png), [ended-history delete refusal](stage12-qa1204-delete-guarded.png), [zero-charge review](stage12-moveout-review-lower.png), and [vacant room after move-out](stage12-qa1204-after-moveout.png). The corresponding two delete-refusal UI hierarchies are also included. **Any other evidence filenames cited in this report are local-only**, including the tenant/profile screenshots, full UI hierarchies with profile data, app and raw Logcat captures, raw database/WAL/SHM copies, encrypted backups, and the password handoff. None of those files is part of the QA commit.
- Remaining Stage 12 gap: populated older-APK **device** upgrade remains **NOT TESTED** because the dedicated migration AVD cannot boot on this host. The isolated populated migration result remains PASS. Stage 10 natural reminder delivery remains **NOT TESTED**. Other historical limitations recorded in Stages 3–11 remain open; Stage 12 is not a declaration that all project requirements passed.
- The report’s previous checkpoint text saying the history/empty-state checks were not rechecked is historical; the results above close those checks. The sanitized report/evidence package is published on `fix/tenant-and-workflow-qa`; other cited local-only evidence remains excluded from the branch.
