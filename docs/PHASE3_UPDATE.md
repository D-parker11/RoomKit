# RoomKit Phase 3 completion update

Prepared 4 November 2026. Base commit: 9ac5e1909405c07f9a5c0d8e1a29298b150155fb.

## Changes

- Twelve meaningful JUnit calculator tests, run by `testDebugUnitTest`. The calculator remains pure Kotlin with no Android imports.
- Saved MONTHLY meter readings are immutable, including before issue and after reopening. Existing invoice snapshot editing/replacement is retained. Meter replacement preserves the original monthly record and carries unbilled consumption into the next period/settlement.
- Transactional month close: all eligible ongoing tenancies are prepared/issued together; missing readings or any other error roll back the entire close. Repeated/concurrent closes reuse persisted invoice IDs.
- Controlled reopening requires a reason. An append-only close/reopen history records each transition. Issued invoices and saved monthly readings remain locked. Payments, payment reversals and refunds remain available after close.
- Password-protected JSON export/restore with the Android document picker. Rental, meter, invoice/item, payment, deposit settlement and month-close records are included.
- Month close and Backup & Restore are available through **More → Settings**.
- Room database version 11 with a non-destructive 10 → 11 migration. Two tables are added; existing data is neither reclassified nor cleared. Fresh installs receive the same new guards.

## Install the supplied patch

Use your current working project, not a folder with unrelated uncommitted changes. Keep a separate copy of your project and emulator data first. Do not uninstall RoomKit to apply a migration.

1. Save the patch outside the repository, for example in Downloads.
2. In the project terminal, run `git status` and save/commit any existing work.
3. Check compatibility: `git apply --check "$HOME/Downloads/RoomKit-phase3.patch"`.
4. Apply: `git apply "$HOME/Downloads/RoomKit-phase3.patch"`.
5. Open/sync the project in Android Studio. Keep your existing Gradle/JDK setup.
6. Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`.
7. Run the app over the existing installation. Database Inspector `PRAGMA user_version` should return 11.
8. Run device tests with an emulator started: `./gradlew :app:connectedDebugAndroidTest`.

If the patch check fails, stop and compare your current files against the patch. Do not force apply or reset your project. This patch targets the base commit above.

## Month-close rules

- Select a completed calendar month as YYYY-MM (Cambodia time). Current and future months cannot close.
- Included: tenancies that are currently ACTIVE and moved in before the selected month ended. Ended tenancies are handled by the move-out settlement workflow; this operation does not invent historical regular invoices for them.
- Existing regular drafts are issued with their saved charges. Review drafts before confirming close. Missing invoices are prepared from tenancy rates, saved adjustments and saved monthly readings.
- Existing issued invoices are reused. A void-only regular invoice must have its replacement draft prepared explicitly before close. Unresolved drafts on ended tenancies block close.
- Closing an empty eligible month is allowed and recorded as an empty snapshot.
- New billing preparation in a closed month is blocked, including final-invoice insertion. Reopen before a backdated settlement that needs a new final invoice.
- Closed status blocks new invoice/readings/adjustments; it does not stop debt collection or refund processing.
- Reopen enables eligible preparation; it never unlocks issued financial snapshots or edits monthly meter facts.
- Previously billed months start without a close record after migration; the migration does not guess that they were closed.

## Backup rules

- The `.json` file contains a versioned envelope with encrypted data (AES-256-GCM; PBKDF2-HMAC-SHA256, 210,000 iterations). Use at least ten characters and keep the password separately. Lost passwords cannot be recovered.
- Restore replaces all rental/billing data; it does not merge. Save a current backup first.
- Only this schema 11 backup format is accepted. Older arbitrary JSON exports and SQLite files are not accepted.
- Before touching live data, restore imports into a separate private Room database and checks schema, relationships, invoice items, balances, property/tenancy rules and settlement allocations.
- Live replacement and trigger restoration run in one transaction. A validation/import failure leaves existing records unchanged.
- Limits: 20 MiB decrypted JSON, 29 MiB envelope, 100,000 records, 20,000 characters per text cell. Files exceeding these limits are rejected.
- Device settings and notification delivery history are not exported. Preserving the current device's notification ledger prevents repeating previously delivered reminders. Restored reminders are reconciled after commit.
- Legacy inconsistent data (for example multiple properties or mismatched payment caches) is rejected rather than silently discarded or repaired.
- The password is held only in memory while choosing a file. After process death, start the operation again.

## Manual acceptance checks on a copied emulator

Record actual results and screenshots; the steps below are not claims that they have passed on your device.

1. Upgrade: start from a populated v10 database, install this build without uninstalling, confirm v11 and unchanged tenants/invoices/payments/meter history.
2. Prepare a completed month with two active tenancies and readings. Review draft charges, close, then close again. Confirm one regular current invoice per tenancy/period and identical invoice IDs on the second close.
3. Rollback: leave the second tenancy's reading missing. Close must fail with no invoices/close record created by that attempt. Tap the month subtitle on Meter Reading, select the missing billing month, add its reading, then retry. Backfilling is blocked if later history exists.
4. Reopen: empty reason must fail; a valid reason records REOPEN. Existing issued values/readings remain locked. Closing again records one further CLOSE without duplicating invoices.
5. Payments: record 200,000 KHR against a 460,000 KHR closed-month invoice. Confirm paid 200,000/balance 260,000; void the payment and confirm balance returns to 460,000.
6. Meter replacement: saved monthly 100 → 130; replace with old closing 140/new opening 0. The saved monthly stays 130. Next-month reading 5 must bill 15 units (10 carry + 5 new), without rewriting the issued invoice.
7. Backup: save an encrypted JSON backup with a password. Change a fictional tenant name, restore, and confirm the original name and financial/audit records return. Relaunch and verify persistence.
8. Failure protection: try a wrong password and a truncated file. Both must fail with existing data unchanged.
9. Lifecycle: rotate before file selection, cancel the picker, rotate during a database operation, and restart after completion. Verify no duplicate close and no lost committed records. A process-death test during restore must leave either the old complete state or the restored complete state.
10. Recheck pending refunds and Tenant A → Tenant B in the same room/month, invoice detail/PDF, dashboard and reminders. Their existing workflows must remain usable.

## Automated verification

See `PHASE3_VERIFICATION.md` for commands actually executed and remaining device checks.
