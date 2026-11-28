# Move-in review dialog

Replaces the plain text confirmation with a compact Material dialog:

- Tenant identity, property/room context and move-in date.
- A soft summary card showing monthly rent and deposit with thousands separators and clear KHR labels.
- Billing schedule, utility charges and starting readings in aligned summary rows.
- Separate fixed/metered water labels; initial water is shown only for metered water.
- Scrollable details and an inset footer: an outlined **Edit details** button and a filled **Confirm** button. Both use the existing shared button styles.

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

## Fixed action footer

`dialog_move_in_review.xml` now owns both action buttons instead of using AlertDialog's native action bar. This prevents duplicate/clipped action bars. Use it together with the updated fragment listeners and the reusable `BoundedDialogLayout` custom view; replacing the XML alone is insufficient.

The root is wrap-content, capped to the current window height minus system bars, display cutouts and shared outer margins. The detail scroll view has zero layout height with weight one so it absorbs the available space while the unweighted footer remains below it. Short content stays compact. Both buttons have equal widths, at least 56dp minimum height, zero letter spacing, and bottom/side insets. Confirm uses a short visible label with the full move-in action as its accessibility description.

Static checks verify both button IDs/listeners, removal of native positive/negative actions in the review builder, single save invocation, XML/resource validity, shared values and retained review content. Actual Android measurement, large-font and landscape behavior must still be checked on an emulator/device.
