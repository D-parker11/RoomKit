# Stage 4 — Meter readings

Follow-up: 4.7 and 4.8 were subsequently executed with newly authorized UI-created fixtures. See the follow-up section at the end. The original NOT TESTED entries below describe the first run; they are superseded by the follow-up results. One room-summary display failure was found in 4.8.

Fix verification: the 4.8 room-summary failure has now been corrected and retested as PASS; see the final section. Earlier FAIL evidence is retained as the original observation.

Date: 2026-11-10, approximately 19:30–19:37 emulator local time. Target verified before interaction: `adb devices -l` listed `emulator-5554`; `adb -s emulator-5554 emu avd name` returned `RoomKit-Test`. All device commands targeted that serial.

## Build and local changes

Workspace HEAD: `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`. Tested the already installed debug app updated during the earlier RoomsFragment fix verification; no build or install during Stage 4. This is not a clean-commit-only test. The installed APK does not embed a verified commit identifier; HEAD is the workspace baseline reference.

Existing local changes preserved:

- RoomsFragment.kt: moved LinearLayoutManager initialization ahead of header inflation (1 insertion / 1 deletion).
- RoomDetailFragment.kt: pre-existing removal of a commented legacy implementation (1 insertion / 615 deletions).
- Untracked MoveInValidationDatabaseTest.kt from the Stage 3 follow-up; not run as Stage 4 UI evidence.
- Untracked docs/qa-results artifacts.

Tracked source diff captured in [stage4-tested-local-changes.patch](stage4-tested-local-changes.patch). No application code modified during this test.

## Existing history and fixture eligibility

Before saving, read-only database inspection found QA-301 / Test Tenant Alpha, tenancy 2, with one INITIAL ELECTRICITY record 100→100 and no MONTHLY record. No invoices existed. The UI history also showed that initial reading before the save (stage4-09-portrait.png).

The only other active tenancy was babab in Room 01, fixed water, electricity initial 0. It was not an eligible separate fake fixture for either 4.7 or 4.8. Beta had no tenancy. No new fixture or changes to unrelated tenancy terms were made.

## Actual UI evidence

| Subtest | Result | Actions and observed result | Screenshots |
|---|---|---|---|
| 4.1 Starting values | PASS | Opened QA-301 → Record Meter through UI. Correct Alpha, November 2026 (current emulator month), previous electricity 100 kWh. Fixed water 20000 KHR/month, no water input. | stage4-02-start-values.png |
| 4.2 Empty | PASS | Empty input had Save disabled; tapping Save did not navigate or save. | stage4-02-start-values.png |
| 4.2 Current 99 | PASS | Entered 99; Invalid meter reading; Save disabled; tapping did not save. | stage4-03-electricity99.png |
| 4.2 Negative | INPUT PREVENTED | Entered -1; field stripped the minus and displayed positive 1. This was not counted as negative-value rejection. No save. | stage4-04-negative-input.png |
| 4.2 Current 100 | PASS | Entered 100; usage 0 kWh, Save enabled. Did not save. | stage4-05-electricity100.png |
| 4.2 Current 115 | PASS | Entered 115; usage 15 kWh. Did not save until all unsaved-input tests finished. | stage4-06-electricity115.png |
| 4.3 Rotation | PASS | Rotated with `cmd window user-rotation lock 1`. Landscape retained current 115 and previous 100; scrolled to verify usage 15. Returned to portrait with lock 0; 115 and usage 15 persisted. | stage4-07-landscape.png, stage4-08-landscape-usage.png, stage4-09-portrait.png |
| 4.4 Change selection then Cancel | PASS | Picker selection changed November→October; Cancel left November 2026 and unsaved current 115 / previous 100 / usage 15. | stage4-10-month-picker.png, stage4-11-picker-changed.png, stage4-12-picker-cancel.png |
| 4.4 Before tenancy start month | PASS | Applied October 2026, entered 115, tapped Save. Rejected: “The billing period cannot be before this tenancy began.” Returned to November; input cleared on applied month change, then re-entered 115. | stage4-13-previous-month.png, stage4-14-before-start-result.png, stage4-16-current-month.png |
| 4.5 Save once | PASS | Saved November 115 once. Returned to room detail showing previous 100, current 115, usage 15. No invoice created. | stage4-17-save-result.png |
| 4.5 Read-only and duplicates | PASS | Reopened Record Meter: read-only notice, disabled current input with 115, Save disabled. Tapped current field and attempted text 999; value remained 115 (also verified after restart). Repeated Save taps did nothing. UI history showed exactly one monthly 100→115 / 15 kWh and original INITIAL 100→100. | stage4-18-readonly.png, stage4-19-history.png, stage4-20-restarted.png |
| 4.6 Restart persistence | PASS | Force-stopped app, launched MainActivity, navigated through Property to QA-301 and Record Meter. Values stayed read-only 100→115 / 15; both history entries retained. | stage4-20-restarted.png, stage4-21-restarted-history.png |
| 4.7 Metered-water validation, save and history | NOT TESTED | No existing separate fake tenancy with metered water initial 50 / rate 2000. Did not alter QA-301 or unrelated terms. Therefore 49/50/56, water history 50→56 and later 12000 KHR charge were not exercised. | Fixture eligibility from database, not a UI pass. |
| 4.8 Replacement validation, save, carried usage and restart | NOT TESTED | No different fake tenancy with initial electricity 100, no monthly reading and no issued invoice. QA-301 reserved for Stage 5. Closing 99 rejection, replacement 110→0, reason, current 5, carried usage and replacement persistence were not exercised. | Fixture eligibility from database, not a UI pass. |

All unsaved tests preceded the one successful November save. Portrait screenshots are 1080×2400; landscape screenshots are 2400×1080 (rendered previews may be scaled).

## Read-only database evidence

Copied database/WAL files with `adb exec-out run-as com.example.roomkit tar cf - databases` into temporary host folders. Queried only local snapshots using SQLite `mode=ro` and `PRAGMA query_only=ON`; no database write commands executed on the emulator.

- [Before snapshot query results](stage4-db-before.json): two active tenancies, only INITIAL readings, no invoices. Establishes history and unavailable separate fixtures.
- [After-save and after-restart query results](stage4-db-after.json): integrity check `ok`; QA-301 has exactly one MONTHLY ELECTRICITY row for November 2026, previous 100, current 115, carriedUsage 0, usage 15. INITIAL row unchanged. No October record. No invoices. All tenancy terms unchanged.
- After duplicate taps and after restart, the queried tenancies, rooms, readings and invoices were identical. Other pre-existing reading rows remained unchanged.
- Expected later electricity charge is **15 × 1000 = 15000 KHR** based on saved usage and rate. This is a calculated expectation, not an issued-invoice UI result. No invoice was issued.

## Crashes and final state

No crash observed. Crash buffer from 19:30:00 through test completion was empty: [stage4-crash-logcat.log](stage4-crash-logcat.log). Logs were not cleared.

QA-301 remains occupied by Alpha with exactly the saved November 115 reading, INITIAL history intact, unchanged tenancy terms and no invoice, ready for Stage 5. No data clearing, uninstall, deletion, unrelated record edit, commit or push. Only the authorized monthly electricity record was added by UI.

## Follow-up — Stage 4.7 and 4.8 completed through actual UI

2026-11-10, approximately 19:41–19:57 emulator local time. Before interaction, `adb devices -l` listed emulator-5554 and `adb -s emulator-5554 emu avd name` returned RoomKit-Test. Same installed app and workspace baseline/local changes described above; no build, install or code edits during follow-up.

### UI-created fixtures and conflict checks

Created QA-302 and QA-303 through Add Room, each rent 400000 KHR. Both succeeded without a capacity change. UI tenant searches for 012345673 and 012345674 each returned No tenants found before profile creation. Created Test Tenant Water / 012345673 and Test Tenant Meter / 012345674, respectively. Existing profiles were not edited.

Both move-in review dialogs showed today (10 Nov 2026), rent 400000, deposit 400000, billing day 1, due day 5, rubbish 10000 and electricity rate 1000. QA-302: initial electricity 100, metered water initial 50/rate 2000. QA-303: initial electricity 100, fixed water 20000. Confirmed each through the UI.

Fixture evidence: stage4-extra-03-create302-result.png, stage4-extra-04-create303-result.png; conflict searches stage4-extra-06-phone673.png and stage4-extra-07-phone674.png; profile inputs stage4-extra-08-water-tenant.png and stage4-extra-09-meter-tenant.png; tenancy reviews stage4-extra-14-water-review.png and stage4-extra-25-meter-review.png.

### Actual UI results

| Check | Result | Evidence |
|---|---|---|
| 4.7 Create independent metered-water tenancy | PASS | QA-302 / Test Tenant Water; review and meter form showed initial electricity 100 and water 50. stage4-extra-14-water-review.png, stage4-extra-17-water-form.png |
| Water 49 rejected | PASS | Entered electricity 105 first so water was the invalid field. Water 49 displayed Invalid meter reading; Save disabled; attempted Save did not save. stage4-extra-19-water49.png |
| Water 50 gives usage 0 | PASS | Entered 50, observed usage 0 m³, did not save. stage4-extra-20-water50.png |
| Water 56 gives usage 6 | PASS | Entered 56, observed usage 6 m³. stage4-extra-21-water56.png |
| Save electricity 105 / water 56 | PASS | One successful UI save. Reopened history showed electricity 100→105 / 5 kWh and water 50→56 / 6 m³, with initial history retained. stage4-extra-22-water-history.png |
| Water history persists after force-stop/reopen | PASS | Reopened QA-302 after restarting app. Saved inputs read-only; history retained water 50→56 / 6 and INITIAL 50→50. stage4-extra-41-water-restarted.png, stage4-extra-42-water-restarted-history.png |
| 4.8 Create independent replacement tenancy | PASS | QA-303 / Test Tenant Meter; fixed water 20000, initial electricity 100. Before replacement UI history contained only INITIAL 100→100; no monthly reading had been saved. stage4-extra-25-meter-review.png, stage4-extra-26-before-replacement.png |
| Closing 99 rejected | PASS | Filled closing 99, new opening 0 and valid reason; reviewed then pressed Record Replacement. Rejected with Replacement values do not cover the old meter reading. stage4-extra-31-closing99-result.png, stage4-extra-32-closing99-rejected.png |
| Record closing 110, opening 0 and reason | PASS | Restored closing to 110; confirmed replacement with reason Damaged test meter. History displayed old closing 110 → new opening 0; INITIAL retained. stage4-extra-33-replacement-review.png, stage4-extra-34-replacement-saved.png |
| Current 5 yields total usage 15 | PASS | Monthly form displayed previous 0/current 5, Usage 15 kWh / Includes 10 kWh from the previous meter. Saved once. stage4-extra-36-total-usage.png |
| Replacement, reason, INITIAL and monthly history survive restart | PASS | Force-stopped RoomKit and launched MainActivity; reopened QA-303. Form read-only with current 5, usage 15, carried 10. History retained INITIAL 100→100, replacement 110→0, monthly 0→5 / 15 kWh. Tapped replacement row: detail dialog retained reason Damaged test meter, old closing 110, new opening 0, Rental #4 / Meter 1. stage4-extra-38-restarted-total.png, stage4-extra-39-restarted-history.png, stage4-extra-40-replacement-details.png |
| Room-detail summary includes carried usage | FAIL | Immediately after monthly save, room-detail Nov 2026 summary showed previous 0/current 5/usage 5 kWh. Meter form and history correctly show 15 including carried 10. Display inconsistency; not corrected during testing. stage4-extra-37-monthly-saved.png |

The first invalid-replacement input sequence left the form without a save because scrolling shifted field positions. Reopened the form, verified values, and executed the actual 99 rejection and 110 replacement documented above. No result is inferred from that abandoned input sequence.

### Supplemental read-only database checks

[stage4-extra-db-evidence.json](stage4-extra-db-evidence.json) contains snapshot queries before replacement and after restart. Database/WAL files copied read-only with run-as/tar, then queried only on host with SQLite mode=ro and query_only=ON. Both integrity checks returned ok. No emulator SQL writes.

- QA-303 before replacement: exactly one INITIAL electricity row, no monthly row; invoices table empty.
- QA-302 after restart: METER method/rate 2000, initial water 50, one monthly water 50→56; electricity monthly 100→105. Expected later water charge **6 × 2000 = 12000 KHR**. This is a calculated expectation, not an issued invoice.
- QA-303 after restart: exactly three electricity records: INITIAL 100→100; METERREPLACEMENT previousReading 110/currentReading 0/reason Damaged test meter; MONTHLY previousReading 0/currentReading 5/carriedUsage 10. Total usage **10 + (5−0) = 15 kWh**. No invalid-99 replacement was inserted.
- Both saved tenancy terms match all requested common terms and water configuration.
- Compared QA-301 against the prior Stage 4 snapshot: room entity, tenancy and both reading rows unchanged. No invoices exist for any tenancy.

No crash observed; crash buffer from 19:41:00 to completion empty: [stage4-extra-crash-logcat.log](stage4-extra-crash-logcat.log). Logs not cleared. No app code edits, capacity changes, existing tenancy edits, invoice issuance, clearing, uninstall, deletion, commit or push. Added only the two authorized test rooms, profiles, tenancies and their requested reading/replacement events.

## Replacement usage display fix and verification

2026-11-10, approximately 20:01–20:05 local time. Baseline commit remains `167b552419e2d4852356cc2eb2f5a91a7c9bd25f`, with existing local changes preserved. Verified serial emulator-5554 and AVD RoomKit-Test before updating. Debug APK installed with `adb install -r` (Success), without uninstall or data clearing.

### Cause and smallest fix

The monthly reading DAO selects the active tenancy's MONTHLY record for the room and period. `RoomDetailRepository.observeRoomDetail()` passes `calculateUsage(reading)` into `RoomDetailUiState.electricityUsage` and `waterUsage`. That helper already uses `MeterReadingEntity.usage`, which computes `carriedUsage + currentReading - previousReading`.

The fragment then called `validUsage(previous, current, storedUsage)` for both utilities. Whenever previous/current existed, it recomputed only their difference, overriding the correct repository total. For QA-303, this changed 15 to 5. Metered water used the same flawed helper.

Small fix in `RoomDetailFragment.validUsage()`: prefer the nonnegative repository-supplied total when present; use previous/current subtraction only when that total is absent. Both electricity and metered-water summaries use this path. No repository, billing, invoice, schema or reading-write logic changed. Issued invoice snapshot rendering still reads invoice snapshot fields independently; no snapshot recalculation introduced.

### Automated evidence and build

Added `RoomDetailUsageTest` (Robolectric), exercising the actual private UI helper via reflection:

- Replacement monthly 0→5 with carriedUsage 10 renders 15 for both electricity and metered water.
- Ordinary electricity 100→115 renders 15; ordinary water 50→56 renders 6.
- Missing usage fallback and invalid/missing values remain handled.

Ran `./gradlew testDebugUnitTest --tests com.example.roomkit.RoomDetailUsageTest --tests com.example.roomkit.Phase3DatabaseTest assembleDebug`: **BUILD SUCCESSFUL in 9s**. **19 tests passed, 0 failures/errors/skipped** (3 focused usage + 16 existing database checks, including invoice snapshot preservation checks). No automated tests used the emulator database.

Evidence: [build/test log](stage4-usage-fix-build-tests.log), [focused JUnit results](stage4-usage-fix-RoomDetailUsageTest.xml), [database JUnit results](stage4-usage-fix-Phase3DatabaseTest.xml). APK SHA-256 and counts are in [verification JSON](stage4-usage-fix-verification.json).

### Actual UI retest

| Check | Result | Screenshot |
|---|---|---|
| QA-303 room-detail summary includes replacement usage | PASS — previous 0, current 5, usage 15 kWh | stage4-fix-01-qa303.png |
| QA-301 ordinary electricity usage unchanged | PASS — previous 100, current 115, usage 15 kWh | stage4-fix-02-qa301.png |
| QA-302 ordinary metered water usage unchanged | PASS — previous 50, current 56, usage 6 m³ | stage4-fix-03-qa302.png |
| QA-303 after force-stop/reopen | PASS — room-detail usage 15 kWh | stage4-fix-07-restarted-summary.png |
| Meter history remains intact | PASS — INITIAL 100→100, replacement 110→0, MONTHLY 0→5 / usage 15 including carried 10 | stage4-fix-08-history.png |
| New crashes | PASS — no entries from 20:01:00 through completion | stage4-usage-fix-crash-logcat.log |

The first restart navigation sequence ran before the activity was ready and screenshots showed the launcher. No crash entries existed. Relaunched with `am start -W`, inspected the loaded dashboard, then repeated actual UI navigation successfully. Launcher screenshots stage4-fix-04 and stage4-fix-05 are not counted as passes.

### Read-only data preservation evidence

Copied database/WAL files to temporary host snapshots before update and after UI verification, queried snapshots with SQLite mode=ro / query_only=ON. Compared every table row: **all tables identical**, including all 10 meter readings, 4 tenancies and 4 rooms. No invoices or invoice items existed on this emulator, and none were created. See stage4-usage-fix-verification.json. No records were changed to resolve this display issue.

No commit or push. Existing RoomsFragment fix, pre-existing RoomDetailFragment cleanup, Stage 3 tests and QA artifacts preserved. The only new production change in this fix is the three-line repository-usage preference in the fragment.
