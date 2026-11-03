# RoomKit: single-property, KHR-only source update

This archive replaces app/src/main. It is not a complete Gradle project or an APK.

## Install in Android Studio

1. Back up or commit your existing project.
2. Replace app/src/main with the main folder from this archive. Replace the folder as a whole because some layout IDs and their Kotlin references changed together.
3. Keep your existing project/app Gradle files, wrapper, dependencies, application ID and namespace.
4. Clear RoomKit's app storage on the emulator/test device (you authorized resetting the test data). This also removes old scheduled reminder work. Alternatively uninstall the test app before installing the rebuilt version.
5. Sync Gradle and build/run in Android Studio. This environment did not have your Gradle configuration, so a full Android build has not been performed.
6. Set up your property and re-enter sample tenants, rooms, and tenancies using whole riel.

## Changes

- One property: repository/DAO validation plus a SQLite trigger reject a second property. Editing the property remains available. The additional-building button disappears after setup; dashboard building filters and property search are hidden. Move-in auto-selects the sole property.
- Whole KHR throughout rent, deposits, invoice totals, payments, lists, PDFs and QR amount presentation. For 400,000 riel, enter 400000. Do not enter 100 to mean USD 100. Decimal currency input is rejected.
- No currency conversion or exchange-rate fields/rows. Utility charges and rent are added directly in KHR.
- Existing offline, no-required-login behavior is retained.
- A new database, roomkit_khr_mvp.db, starts at schema version 1. No legacy USD data is imported. Old migrations were removed from this fresh-schema version; this is not a production data-preserving upgrade.
- Invoice issuance checks that the preview still matches current billing data and rejects duplicate periods. Under the retained unique monthly invoice rule, a VOID invoice still occupies its period; replacing a void invoice requires a separate correction workflow.
- Draft/void invoices reject payments. Payment limits use the sum of payment records. Due dates use the next month's actual last day when the configured due day is out of range, rather than always clamping to 28.
- Billing arithmetic validates negative inputs, discounts and integer overflow.
- Room cards now use monthlyRent directly rather than reflection. Previously hardcoded meter dashes now show stored cumulative readings (labelled Reading), with Fixed fee for non-metered water. These are not monthly usage figures.

## Checks performed

- Compiled and executed BillingCalculator.kt and CurrencyFormatter.kt with Kotlin/JVM: 19 checks passed (metered/fixed water, total, discount, negative inputs, overflow, whole-riel parsing and payment status).
- Parsed all 117 XML files.
- Checked Kotlin resource ID references and removed legacy USD field/formatter references.
- Prepared 65 DAO SQL queries in SQLite against columns derived from the supplied entities.
- Executed the single-property trigger: first insert succeeds, second insert fails, editing succeeds.
- Kotlin type-check of repositories, entities, DAOs, models and selected ViewModels passed using minimal Android/Room/Lifecycle stand-ins plus coroutines. This checks internal signatures; it does not validate Android integration, Room-generated implementations, resources, or UI behavior.

## Android acceptance checks still required

- Create one property with capacity 2; create two rooms and verify a third is blocked.
- Edit the property; verify a second property cannot be created.
- Enter rent 400000, electricity 890 to 905 at 1000/kWh, metered water 60 to 72 at 1500/m3, and rubbish 5000. Expected utility subtotal: 38000; total: 438000 riel.
- Record payment 100000; expected remaining balance: 338000 riel. Pay the balance; expected status: PAID.
- Verify draft/void payment rejection and overpayment rejection.
- Verify one active tenancy per tenant and per room, existing-tenant navigation, move-out, and subsequent reassignment.
- Check invoice preview/detail, printed/PDF amounts, dashboard totals, payment history and reminder text.

## Pre-existing limitations retained

- The current move-in routine can replace same-room, same-month meter baselines. Multiple turnovers in a month need a tenancy-aware reading/history design; do not treat that workflow as validated.
- Final move-out invoicing, deposit settlement and rent proration were not implemented by this currency/property update.
- Existing PDF/QR features are retained. Bank acceptance of the existing QR generator and real payment integration have not been tested; payment confirmation remains manual.
- Package/folder naming inconsistencies and some existing lint warnings may remain.

Validation source is in validation/. BillingChecks.kt is a standalone Kotlin main function; check_source.py uses Python and lxml.
