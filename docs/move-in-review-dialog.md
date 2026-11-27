# Move-in review dialog

Replaces the plain text confirmation with a compact Material dialog:

- Tenant identity, property/room context and move-in date.
- A soft summary card showing monthly rent and deposit with thousands separators and clear KHR labels.
- Billing schedule, utility charges and starting readings in aligned summary rows.
- Separate fixed/metered water labels; initial water is shown only for metered water.
- Scrollable details and native dialog actions: **Edit details** and **Confirm move-in**.

The dialog uses existing palette and dimension tokens, text appearances, the standard card style, shared button styles, existing building icon and `item_summary_row.xml`. Dialog rows use compact spacing only for this instance; existing summary-row consumers are unchanged. `ThemeOverlay.RoomKit.Dialog` and `ShapeAppearance.RoomKit.Dialog` can be reused for future dialogs.

All existing input guards, single-open-dialog handling and lifecycle cleanup are retained. Confirmation calls the original `viewModel.confirmMoveIn()` once, and the ViewModel/repository/database code is unchanged. Review formatting does not change stored numbers or validation. Invalid or empty numeric input is displayed as “Not set”; existing validation remains responsible for rejecting it on confirmation.

## Verification

Static checks cover XML validity, resource name uniqueness, new resource references, all seven included summary-row binding IDs, fixed/metered conditional visibility, and preservation of existing fragment behavior outside the review presentation. Existing string resource values are preserved after the user's manual string consolidation.

Android resource compilation, ViewBinding generation, device rendering and accessibility behavior still require verification in Android Studio; this environment has no configured Android SDK and cannot download Gradle.

Run `./gradlew :app:processDebugResources :app:assembleDebug`, then check:

1. Rent and deposit of 400000 display as **400,000**; zero deposit remains **0**.
2. Fixed water shows its monthly fee and no initial water row.
3. Metered water shows its rate per m³ and the initial water reading.
4. Long tenant/property names and large amounts wrap without truncation.
5. Small screens, landscape and larger system fonts allow scrolling to all details while dialog actions remain available.
6. **Edit details**, Back and outside-tap dismissal preserve the form and create no tenancy.
7. Confirmation still rejects invalid data or an unavailable tenant and saves a valid move-in once.
