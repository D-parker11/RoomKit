# Stage 3 actual UI results — 2026-11-10

Target: RoomKit-Test, emulator-5554 only. AVD name verified with adb. UI tests used taps, text input, swipes, Back, and screenshots. Test window approximately 19:03–19:18 emulator local time.

## Actual UI evidence (original run)

The UI-specific BLOCKED results below remain accurate. The read-only database and isolated automated evidence in the follow-up sections close the underlying verification gaps without claiming additional UI coverage.

| Test | Result | Observed evidence |
|---|---|---|
| Create vacant QA-301, rent 400000 KHR | PASS | Created successfully. No capacity change. Total rooms 1→2, occupied stayed 1, vacant 0→1. stage3-03-room-filled.png, stage3-04-create-result.png |
| Check phone conflicts and create Alpha/Beta | PASS | All existing profiles numbered two: Sovann Channdavith 0967336673 and babab 04949494. Neither requested phone existed. Created Test Tenant Alpha 012345671 and Test Tenant Beta 012345672. All four profiles subsequently visible in selector. stage3-07-all-tenants.png, stage3-11-alpha-filled.png, stage3-13-beta-filled.png, stage3-17-choose-alpha.png |
| Open QA-301 → Move in | PASS | Form preset Suzuki and QA-301. Date 10 Nov 2026, matching emulator today. stage3-05-move-in.png |
| Missing tenant | PASS | Review dialog allowed an empty tenant, but Confirm rejected completion: "Please select a tenant." stage3-15-missing-tenant.png, stage3-16-missing-tenant-result.png. Restored by selecting Alpha. |
| Blank rent | PASS | Cleared 400000; review showed blank rent. Confirm rejected: "Monthly rent must be greater than 0៛." Restored 400000. stage3-19-blank-rent-review.png, stage3-20-blank-rent-result.png |
| Negative deposit submission | BLOCKED | Actual UI input of -1 became positive 1: minus sign filtered by the field. Could not exercise submission of a retained negative value through this UI. Did not confirm positive 1. Restored deposit 400000. stage3-22-deposit-input-result.png |
| Due day outside 1–28 | PASS | Tested 29 and 0 separately; Review refused with inline "Due day must be between 1 and 28." Restored 5 between tests and afterward. stage3-23-due29-review.png, stage3-24-due0-result.png |
| Negative initial electricity submission | BLOCKED | Actual UI input of -1 became positive 1: minus sign filtered. Could not submit a retained negative reading. Did not confirm positive 1. Restored valid 100. stage3-26-negative-electricity.png |
| Invalid attempts create no active tenancy | PASS | After all attempts, exited the form: QA-301 remained Vacant / No active tenant / No current tenancy invoice. Reopened selector showed Alpha and Beta Available / No active room. stage3-27-still-vacant.png, stage3-28-alpha-available.png. This is UI evidence; no database inspection performed. |
| Complete Alpha move-in with requested terms | PASS | Review displayed Alpha; 10 Nov 2026; rent 400000; deposit 400000; billing 1; due 5; electricity 1000 KHR/kWh; fixed water 20000; rubbish 10000; initial electricity 100. Confirm succeeded. stage3-30-final-review.png, stage3-31-move-in-saved.png |
| Occupancy increases; total rooms unchanged by move-in | PASS | Immediately before move-in 2 total / 1 occupied / 1 vacant. After: 2 total / 2 occupied / 0 vacant. Alpha on QA-301 card. stage3-32-occupancy.png |
| Alpha tenant profile | PASS | Current tenancy Suzuki / QA-301; rent 400000; deposit 400000; moved in 10 Nov 2026; one active rental-history entry. stage3-34-alpha-search.png, stage3-35-alpha-profile.png |
| Beta cannot be assigned to occupied QA-301 | PASS | Beta profile unassigned; Assign Room opened form with Beta and Suzuki. Vacant-room selector disabled and stated "No vacant rooms available in this property"; tapping it did not open choices. QA-301 could not be selected. No Beta tenancy saved. stage3-36-beta-profile.png, stage3-37-beta-move-in.png |
| Saved terms visible after move-in | PASS | Rent/deposit/date in Alpha profile; due day 5 and electricity 100 on room card; fixed water 20000 and rubbish 10000 in meter form. stage3-35-alpha-profile.png, stage3-32-occupancy.png, stage3-41-previous-electricity.png |
| Independently verify saved billing day 1 and electricity rate 1000 | BLOCKED | Both verified in pre-confirmation review, but the inspected post-save UI (room detail, tenant profile, meter form, Manage Tenancy) did not expose these two saved values. Manage Tenancy offered only View Tenant Profile and Move Out. No readings/invoice generated to infer values. stage3-30-final-review.png, stage3-42-saved-terms.png |
| Previous electricity 100 | PASS | Opened Record Meter for inspection only. Meter readings form showed Previous reading 100 kWh. Current reading remained blank; Save readings was disabled. stage3-41-previous-electricity.png |
| Fixed water needs no meter reading | PASS | Room detail: fixed monthly fee 20000, "No meter reading required." Meter form showed fixed water fee without water reading input. stage3-31-move-in-saved.png, stage3-41-previous-electricity.png |
| Force-stop/reopen persistence | PASS | Ran am force-stop com.example.roomkit, then launched MainActivity. Dashboard remained 2 total / 2 occupied / 0 vacant. Reopened room list retained Alpha, rent 400000, due 5, reading 100. stage3-43-reopened.png, stage3-44-persisted-card.png |
| New crashes | PASS | Crash buffer queried from 19:03:00 through end of testing; empty output. stage3-crash-logcat.log. Logs not cleared. |

No code changes during this run. Existing code changes preserved. No existing tenant/tenancy edited, no data clearing, uninstall, record deletion, monthly reading save, commit, or push. Created only QA-301, the two requested fake profiles, and Alpha's requested tenancy. QA artifacts saved in this folder.

Observation: room-detail monthly electricity summary displayed dashes before any monthly readings, while the room card and meter-entry Previous reading correctly showed the opening value 100. Missing tenant and blank rent validation occurs at Confirm rather than before the review dialog.


## Read-only database evidence (follow-up)

**PASS — all eight requested saved tenancy terms.** Target verified as RoomKit-Test / emulator-5554. Used `adb exec-out run-as com.example.roomkit tar cf - databases` to copy database and WAL files to a local temporary snapshot. No SQL was executed on the emulator, and its database was not modified. The local snapshot was opened using SQLite `mode=ro` and `PRAGMA query_only=ON`; integrity check returned `ok`.

Query joined `tenancies`, `rooms`, and `tenants`, selecting the ACTIVE tenancy for roomNumber QA-301. Exactly one matched: tenancyId 2, roomId 2, tenantId 3, Test Tenant Alpha / 012345671, currency KHR, room OCCUPIED, initialElectricityReading 100.

| Saved field | Expected | Actual | Result |
|---|---|---|---|
| monthlyRent | 400000 | 400000 | PASS |
| deposit | 400000 | 400000 | PASS |
| billingDay | 1 | 1 | PASS |
| dueDay | 5 | 5 | PASS |
| electricityRate | 1000 | 1000 | PASS |
| waterBillingMethod | FIXED | FIXED | PASS |
| waterRate (fixed fee) | 20000 | 20000 | PASS |
| rubbishFee | 10000 | 10000 | PASS |

Full query, parameters, returned row and expected/actual checks: [stage3-saved-tenancy-readonly.json](stage3-saved-tenancy-readonly.json). This closes the saved billing-day/electricity-rate gap through database evidence, not UI evidence.

## Isolated automated-test evidence (follow-up)

**PASS — 3 tests, 0 failures, 0 errors, 0 skipped.** Added `app/src/test/java/com/example/roomkit/MoveInValidationDatabaseTest.kt`. Tests run on the host under Robolectric SDK 30. Each test creates a fresh Room in-memory database, installs the production schema guards, seeds a fictional property/room/tenant, and calls the production TenancyRepository.moveInTenant method. No emulator database or installed app was used by these tests.

Command: `./gradlew testDebugUnitTest --tests com.example.roomkit.MoveInValidationDatabaseTest` — BUILD SUCCESSFUL in 7s.

| Automated test | Result | Assertions |
|---|---|---|
| Negative deposit -1, otherwise valid input | PASS | Result fails with IllegalArgumentException / Deposit cannot be negative.; all tenancy rows = 0; all meter_readings rows = 0; no active tenancy for tenant or room; full room entity unchanged and VACANT; room count unchanged. |
| Negative initial electricity -1, otherwise valid input | PASS | Result fails with IllegalArgumentException / Initial electricity reading cannot be negative.; same zero-row and unchanged-room assertions. |
| Valid-input control | PASS | Same fixture accepts valid move-in; exactly one tenancy and one INITIAL ELECTRICITY reading; previous/current = 100; room becomes OCCUPIED. |

Evidence: [Gradle test log](stage3-negative-validation-tests.log), [JUnit XML](stage3-negative-validation-junit.xml). The two negative-value repository checks are now PASS. Actual UI submission of negative values remains BLOCKED because the input widgets filter the minus sign; automated coverage does not change that UI observation.

No production source changes, app reinstall, emulator writes, record changes, commit, or push were performed for this follow-up. Existing unrelated changes preserved.
