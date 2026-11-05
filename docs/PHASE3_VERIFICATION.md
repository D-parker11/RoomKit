# Actual verification record — 4 November 2026

## Passed

| Check | Actual result |
|---|---|
| Debug APK assembly | PASS (`:app:assembleDebug`) |
| Kotlin/Room generation and compilation | PASS |
| Device-test source compilation | PASS (`:app:compileDebugAndroidTestKotlin`) |
| Calculator JUnit tests | 12 passed, zero failures/errors |
| Backup encryption JUnit tests | 6 passed, zero failures/errors |
| Room/Robolectric integration tests | 12 passed, zero failures/errors |
| Existing example JUnit test | 1 passed |
| Total Gradle JUnit result | 31 passed, zero failures/errors/skips |
| Standalone JVM calculator/encryption run | 18 passed, no Android runtime |
| Whitespace/error check | `git diff --check` passed |

The successful combined Gradle run completed in 33 seconds with 61 tasks (1 executed, 60 up-to-date), after compilation and dependency provisioning in preceding runs. The test task was executed, not skipped. Earlier test-run attempts failed while downloading Robolectric's Android runtime; those infrastructure failures were resolved before the passing run.

## Database scenarios actually exercised

1. Close twice: same saved invoice IDs and only one CLOSE event.
2. Competing close calls: one resulting invoice set.
3. A missing second-tenancy reading: all invoices and the period record roll back.
4. Reopen requires a reason; existing issued snapshots remain unchanged; closing again records CLOSE/REOPEN/CLOSE without duplicate invoices.
5. Updating a saved monthly reading before issue is rejected.
6. A closed month accepts 200,000 KHR against a 460,000 KHR invoice, leaving 260,000 KHR.
7. Encrypted JSON round trip restores tenant details, invoice amounts, closed status and audit events.
8. Wrong password leaves current data unchanged.
9. Malformed JSON leaves current data unchanged.
10. Authenticated but inconsistent JSON (negative rent) is rejected without replacing live records.
11. The 10→11 migration preserves an existing tenant, creates the new tables/guards and reports schema version 11.
12. A replacement after monthly closing 130, with old meter closing 140 and new opening 0, preserves monthly 130 and carries 10 units; new reading 5 yields 15 unbilled units.

## Validation environment

Linux, OpenJDK 17, Android SDK 35/build tools 35.0.0, Gradle 8.13, project AGP 8.13.2 and Kotlin 2.1.0. A temporary build copy used a provisioned JDK (toolchain downloader disabled there) and local Robolectric API 30/API 35 runtime jars to accommodate this environment's network routing. Those temporary infrastructure changes are NOT included in the patch. Application sources and supplied tests match the delivered update.

The user's existing Gradle 9.5/JDK 25 wrapper/toolchain files are unchanged. Rerun the documented commands using that existing Mac setup before merging.

The migration fixture is derived from the generated schema with the two new tables removed and its version set to 10. It does not replace testing a real populated version 10 database copy or the entire historical 1→11 migration chain.

## Not yet verified on a device

- `connectedDebugAndroidTest` execution on an emulator/phone.
- Actual Android document-picker UI, screen appearance, rotation and process-death/restart behavior.
- A real existing-user database migration and full Tenant A → Tenant B/pending-refund/PDF/reminder regression pass with this update installed.

Use the acceptance checklist in PHASE3_UPDATE.md and record actual screenshots/results. Automated tests above do not certify those device checks as passed.

## Follow-up verification — 5 November 2026

Verified by the developer in RoomKit-clean-clone on macOS:

- Debug build succeeded using Gradle 9.5.
- Full JVM suite: 31 tests passed, 0 failures, 0 skipped.
  Breakdown: 12 calculator, 6 encryption, 12 database, 1 example.
- Wrong-password restore was rejected; existing tenants remained unchanged.
- Correct-password restore recovered the earlier backup state.
- October close repeated with identical invoice IDs.
- Empty reopening reason was rejected; valid reopening and closing
  produced CLOSE → REOPEN → CLOSE history.
- Partial payment, payment voiding and full payment updated balances correctly.
- Invoice PDF values matched; sharing worked.
- Invoice rotation and app restart retained the checked records.
- Chessda moved out; Room 14 became vacant.
- Preap Sovath moved into Room 14 in the same month with zero deposit.
- New tenant readings were 6 → 11 kWh, usage 5 kWh.
- New invoice was saved as a draft, then issued for 435,000 KHR.
- Chessda's 28,000 KHR refund stayed separate, was marked paid,
  and remained paid after restart.
- Preap Sovath's invoice remained unpaid at 435,000 KHR.
- Current-month close was correctly rejected.

The earlier pending-device list is superseded only for the checks above.
Manual rollback was not completed; the automated rollback test passed
its existing assertions. Connected instrumentation tests, process death
during restore, and reminder delivery remain unverified in this session.

Verified application commit: 767a047.
