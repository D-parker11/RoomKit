# Stage 7 — Previous debt and historical billing

## Target, version and scope

Read [Stage 5](stage5-invoice-ui-results-2026-11-11.md) and [Stage 6](stage6-payment-ui-results-2026-11-11.md) first. Verified `adb devices -l`: **emulator-5554**, sdk_gphone64_arm64, transport1; serial-targeted `emu avd name`: **RoomKit-Test**. All device commands targeted this serial. Target tenancyId2, roomId2 / QA-301 / Test Tenant Alpha (tenantId3). Actual taps, input, swipes, month selectors, Settings date controls, screenshots and force-stop/reopen used. No direct activity/database shortcut used for billing writes.

Workspace HEAD: **167b552419e2d4852356cc2eb2f5a91a7c9bd25f**. Tested the installed debug APK from Stage 4 fix; APK has no verified embedded commit identifier. Preserved RoomsFragment initialization-order fix, RoomDetailFragment legacy-comment cleanup and usage-display fix; existing untracked MoveInValidationDatabaseTest, RoomDetailUsageTest, InvoiceSnapshotDatabaseTest, PaymentProtectionDatabaseTest and QA artifacts. [Comparison](stage7-final-comparison.json) confirms tracked production diff remains byte-identical to [prior preserved diff](stage5-preserved-production-changes.patch). No source changes, tests added/run, build, installation, commit or push in Stage 7.

Test clock: started November11 at approximately10:27; emulator temporarily December1 at10:31–10:39; restored November11 at10:39. Finished about10:41 Asia/Phnom_Penh. No banking apps, QR scanning or money transfers.

## Actual UI evidence

| Check | Result | Observed result and screenshots |
|---|---|---|
| 7.1 Starting state | PASS | Invoice#0002/ID2, November2026, Alpha/QA-301, total445000, paid145000/balance300000. Two received payments100000 Cash and45000 Bank Transfer, one retained voided300000 payment with prior reason. [Starting history](stage7-01-start.png), [Billing baseline](stage7-02-root.png), [invoice baseline](stage7-03-november-baseline.png). Read-only baseline independently verifies identity/counts. |
| 7.2 December access/date | PASS with coverage limit | Tapped Billing next-month arrow: December2026 selected, empty list. Dashboard month selector also offers December viewing. Room preparation remains tied to current emulator month; November room had existing invoice and no meter-entry button. No December preparation action exists on historical Billing view, so no submitted future-preparation rejection dialog was exercised. Used authorized emulator-only Settings date workaround; see clock evidence below. This is not a claimed backend future-date validation pass. [December view](stage7-09-future-month.png), [dashboard picker](stage7-11-dashboard-month.png), [December dashboard](stage7-12-december-view.png). |
| 7.2 Duplicate/terms gate | PASS (DB supplement) | Before writes, no December reading/invoice. Same active tenancy, rent400000, electricity1000, FIXED water20000, rubbish10000, deposit400000. No terms changed. [Baseline DB](stage7-db-baseline.json). |
| 7.2 Save readings | PASS | December meter form explicitly QA-301/Alpha, previous115; entered125, usage10. Fixed water20000 with no input. Saved once, room shows115→125/10kWh. [Form](stage7-17-december-meter.png), [ready](stage7-18-reading-ready.png), [saved](stage7-19-reading-saved.png). |
| 7.3 Draft | PASS | Preview rent400000/electricity10000/fixed water20000/rubbish10000, total440000, dueJan5. Saved no-adjustment Draft#0003, ID3, same tenancy. No previous-debt charge item. [Preview](stage7-20-december-preview.png), [zero adjustments](stage7-21-preview-account.png), [saved](stage7-22-draft-saved.png), [items](stage7-23-draft-items.png), [bottom](stage7-24-draft-bottom.png). DB confirms November debt300000 and total collectible issued debt300000; draft balance0. |
| 7.3 Projected account field | NOT DISPLAYED | Inspected preview and saved draft: no projected740000 account figure shown. Draft explicitly says no payment is due until issue. No misleading740000 draft charge observed. Actual collectible issued-debt sum verified separately in DB. |
| 7.4 Issue | PASS | Issued SAME draftID3 as Invoice#0003, distinct from#0002. Exactly one regular active December invoice. December total/balance440000, paid0; dueJan5,2027. Account section: previous300000/current440000/total740000; previous debt explicitly not charged again. November total445000/balance300000 unchanged. [Confirmation](stage7-25-issue-confirm.png), [issued](stage7-26-december-issued.png), [account](stage7-27-issued-account.png), [Billing list](stage7-28-billing-december.png). |
| 7.5 Pay November | PASS | Used Billing left-month arrow and opened November#0002 explicitly. Entered100000 Bank Transfer, dedicated referenceTEST-OLD-DEBT-001, payment dateDec1. Saved once as paymentID4, invoiceId2. November paid245000/balance200000; December stillpaid0/balance440000. Reopened December account shows previous200000/current440000/total640000; charges unchanged440000. [Selected November](stage7-29-november-selected.png), [ready](stage7-31-nov-payment-ready.png), [December reopened](stage7-32-december-after-old-payment.png), [live debt](stage7-33-updated-previous-debt.png). |
| 7.6 Pay December | PASS | Opened verified December#0003 through Billing month selector. Payment form saysDec2026/total440000/paid0/remaining440000. Entered40000 Cash, saved once as paymentID5, invoiceId3. December paid40000/balance400000; November stays200000. Account600000. [Ready](stage7-34-dec-payment-ready.png), [saved](stage7-35-dec-payment-saved.png). |
| 7.7 History/restart | PASS | Force-stop/reopen. Billing inspected December and November via arrows. November total445000/balance200000, December440000/balance400000. November previous debt0, separately labeled **Later invoice balances400000**, account600000. December previous200000/current400000/account600000. Details/IDs/readings/rates/due dates and payments persisted without duplicates. [December list](stage7-37-december-history-list.png), [November list](stage7-38-november-history-list.png), [November account](stage7-39-november-account.png), [December after restart](stage7-40-december-restarted.png), [December account/history](stage7-41-december-export-actions.png). |
| 7.8 Actual PDF | PASS | Export PDF generated fresh December PDF; opened it in Android PDF viewer and inspected actual page. Total440000/paid40000/currentbalance400000. Separate account section previous200000/account600000. All requested account fields DISPLAYED. See PDF evidence below. [Export confirmation](stage7-42-pdf-export-ui.png), [actual PDF viewer](stage7-43-open-pdf.png). |
| 7.8 Share chooser | PASS | Tapped share control labeled Telegram; it launches generic Android chooser for this PDF. Captured chooser, then Back cancelled, no recipient/app selected and nothing sent. [Chooser](stage7-44-share-chooser.png). |
| Restore clock/access | PASS | Restored original automatic time via Settings, verified November11 actual current date, automatic zone still enabled/Asia Phnom Penh. Force-stop/reopen again. November and December invoices both accessible through Billing arrows even though December is now future. No future-period visibility restriction observed for saved invoice access. [Settings restored](stage7-46-date-restored.png), [November](stage7-48-restored-november.png), [December list](stage7-49-future-december-list-restored.png), [December](stage7-50-december-access-restored.png), [account](stage7-51-account-restored.png), [November reference/history](stage7-52-november-reference-persisted.png). |
| Logcat | PASS | No new crash-buffer entries or AndroidRuntime exceptions from originalNov11 10:27 through completion, including temporaryDec1 testing. [Crash buffer](stage7-crash-logcat.log), [runtime](stage7-android-runtime.log), both empty. |

No balance failures or dependent payment steps stopped. Future-preparation validation itself was not submitted: historical viewing worked, and current-month room workflow was used after the permitted clock change. No claim of a future-preparation rejection dialog.

## Emulator date/settings evidence

[Original observation](stage7-original-clock.txt): WedNov11 10:27:44+07 2026; auto_time1, auto_time_zone1, timezoneAsia/Phnom_Penh. [Original Android Settings](stage7-13-original-time-settings.png) confirms Automatic date/time ON, Automatic time zone ON, location-for-zone OFF, 24-hour format OFF.

Through Settings taps only, disabled Automatic date/time, opened Date, moved calendar November→December and selectedDecember1,2026. [Picker](stage7-14-date-picker.png), [selected Dec1](stage7-15-dec1-selected.png). `adb shell date` confirmed TueDec1 10:31:21+07 2026. Did not change time-of-day, time zone, format, Mac clock or tenancy dates.

After PDF/share, cancelled chooser and returned to Android Date & time. [Before restore](stage7-45-date-before-restore.png); re-enabled Automatic date/time. [Restored settings](stage7-46-date-restored.png), [read-only clock/settings check](stage7-restored-clock.txt): WedNov11 10:39:54+07 2026, auto_time1/auto_time_zone1/Asia Phnom Penh. Actual date returned automatically. Saved December records retain their intentionally simulatedDecember1 timestamps after restoration.

## Read-only database evidence

Exported database and WAL with serial-targeted `exec-out run-as ... tar`; queried host copies using SQLite URI mode=ro and query_only=ON. No SQL writes on emulator, manual record edits, clear/uninstall or record deletions. Snapshots:

- [Baseline](stage7-db-baseline.json): existing November invoice and three payment rows; no December data; original terms.
- [Readings](stage7-db-readings.json): only new readingID11, MONTHLY electricity, tenancy2/room2/Dec2026,115→125/usage10, carried0/sequence0. No water reading inserted.
- [Draft](stage7-db-draft.json): ID3 DRAFT, total440000/balance0, six charge rows including zero OTHER/DISCOUNT. Sum of collectible issued balances remains300000. November invoice and its payments exactly unchanged.
- [Issued](stage7-db-issued.json): same ID3 ISSUED, total440000/paid0/balance440000; no debt item. Due1799168399999 = Jan5,2027 23:59:59.999+07. November unchanged.
- [After November payment](stage7-db-old-payment.json): ID4 →invoice2,100000 BANK_TRANSFER/referenceTEST-OLD-DEBT-001; November245000/200000, December0/440000.
- [After December payment](stage7-db-payments.json): ID5 →invoice3,40000 CASH, no reference/note; December40000/400000, November unchanged245000/200000.
- [Final after restarts/clock restore](stage7-db-final.json) and [comparison](stage7-final-comparison.json): final matches post-payment snapshot exactly. Two regular invoices, one for each month, no duplicate payments. All pre-run payment rows (IDs1,2,3 includingvoided3) unchanged; only IDs4,5 added. Tenants, tenancies (includingdeposit400000/rates/dates), rooms and all10 original meter rows unchanged. New readingID11 remained immutable. Issued invoice snapshot fields/items/due dates preserved; November original items unchanged. integrity_check=ok.

Final invoice raw statuses both PARTIALLY_PAID; app resolver treats them as issued documents with partial payment status. UI displays Partially Paid. No invoice/payment voided in Stage7.

| Invoice | ID | Total KHR | Active paid | Balance | Due |
|---|---|---|---|---|---|
| November#0002 | 2 | 445000 | 245000 | 200000 | Dec5,2026 |
| December#0003 | 3 | 440000 | 40000 | 400000 | Jan5,2027 |

Same-tenancy account balance **600000 KHR**. Payment4(100000 Bank Transfer) applies only toID2; payment5(40000 Cash) only toID3. Both have simulated payment dateDec1. No other tenancy has invoices/payments in these snapshots, and unrelated records are unchanged.

## Dashboard collection scope

Verified implementation in DashboardViewModel: filters official invoices by **invoice billing month/year**, then sums ACTIVE payments linked to those invoice IDs; does not filter collection by paymentDate. Actual UI confirms:

- [December dashboard](stage7-36-dec-dashboard-restart.png): billed440000, collected40000, outstanding400000. The100000 payment madeDec1 toward November is excluded from December collections.
- [November dashboard after restore](stage7-47-restored-dashboard.png): billed445000, collected245000, outstanding200000. Includes thatDec1 old-debt payment. Compared with startingNovember145000/300000, collected+100000/outstanding-100000.

Thus these are invoice-month collection totals, not December payment-date cash flow (which would140000 for this run). Monthly dashboard outstanding is not the600000 all-month tenancy account balance. No cross-scope comparison or other-tenant attribution used.

## Actual PDF evidence

Saved [generated PDF](stage7-december-invoice-0003.pdf), copied read-only from `/sdcard/Download/RoomKit/RoomKit_Invoice_INV_0003_Dec_2026.pdf` (68158bytes). Opened actual file in Android PDF viewer; additionally parsed saved bytes with host macOS PDFKit, producing [extracted text](stage7-december-pdf-text.txt), one page. This is artifact inspection, not source inference or RoomKit preview alone.

PDF number **#INV-0003** (UI label#0003), Alpha/QA-301, periodDec2026/serviceDec1–31, dueJan5. Rent400000, electricity10×1000=10000, fixedwater20000, rubbish10000; invoice total440000, paid40000, current invoice balance400000. Separate TENANCY ACCOUNT BALANCE: previous200000/current400000/total600000, with explicit previous-debt-not-charged-again note. Payment history contains only DecemberCash40000, not November payments. Account fields are DISPLAYED, none marked NOT DISPLAYED. PDF SHA256 in comparison JSON. Android chooser launched for this same filename, then cancelled.

No automated test evidence claimed for this stage. Actual UI, read-only database and PDF evidence are separated above. Final state leaves both invoices partially paid and original automatic emulator time restored. No production changes, unrelated edits, real transfers, commit or push.
