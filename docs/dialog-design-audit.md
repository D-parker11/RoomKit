# Shared dialog design

## Audit

Audited the tracked Android source at d8d0873. Found 26 MaterialAlertDialogBuilder construction sites across 13 files. No framework AlertDialog.Builder or AppCompat AlertDialog.Builder construction sites remain in that source. Date pickers, bottom sheets, and the custom tenant/month pickers are separate controls.

| Screen | Existing builder sites | Treatment |
| --- | ---: | --- |
| Backup / month tools | 5 | Shared native alert theme; password/reason validation and secure-window flag retained |
| Invoice details | 2 | Shared native alert theme for history; existing custom void form retained |
| Invoice preview | 1 | Shared native alert theme; issue confirmation retained |
| Record payment | 1 | Structured amount/allocation review with fixed actions |
| Replacement invoice | 4 | Shared native alert theme; issue, discard and delete guards retained |
| Meter readings | 3 | Structured replacement review; shared native alerts for reload and history |
| Move-in | 2 | Existing structured review retained; error alert uses shared theme |
| Move-out | 1 | Shared native alert theme; refund reference field retained |
| Payment setup | 2 | Shared native alert theme for remove/discard |
| Room editing | 2 | Shared native alert theme; destructive action color retained |
| Settings | 1 | Shared native alert theme for privacy |
| Add tenant | 1 | Shared native choice dialog theme |
| Tenant details | 1 | Shared native invoice list/empty message theme |

The original multiline move-in confirmation from the screenshot was already replaced by dialog_move_in_review.xml. This update addresses remaining inconsistent default alert appearances and the two other long confirmation summaries.

## Reuse

- Theme.RoomKit now supplies materialAlertDialogTheme. New MaterialAlertDialogBuilder instances inherit rounded corners, shared title/body typography, sentence-case actions, and 48dp minimum action targets.
- Simple alerts retain the native measured/stacking button bar. They use text actions, including the positive action, rather than inserting a large filled button into the native button bar. This preserves list, neutral-button and getButton validation behavior.
- ReviewDialogs.create uses dialog_review.xml and item_dialog_review_section.xml for longer summaries. Supply display-only ReviewSection data and a confirmation callback; keep validation and database work in the existing ViewModel/repository.
- Reviews use existing item_summary_row.xml, shared colors, text appearances and dimensions. No new palette values or size tokens.
- BoundedDialogLayout limits height to the available window. Details scroll; full-width primary and secondary actions stay outside the scroll area. Short reviews wrap their content.
- Move-in keeps its tailored review layout and existing fixed footer.

## Behavior and validation

Payment allocation order, balance fingerprint check, amount formatting, save arguments and dismissal cleanup are preserved. Meter replacement still invokes the existing ViewModel operation only on confirmation. Its review is dismissed on view destruction and guards repeated opens. The shared review disables both actions before invoking its callback to prevent repeated confirmation.

Static checks passed: XML parsing, resource-name uniqueness, changed resource references, generated binding ID consistency, unchanged payment save arguments and meter callback/lifecycle checks. The old meter replacement message resource was removed after its final use was replaced; user-consolidated strings are preserved.

Android compilation and device rendering are not verified in this environment: Gradle distribution/Android SDK are unavailable. Before merging or releasing, build in Android Studio and inspect:

1. Move-in, meter replacement and payment allocation reviews at normal and large font sizes, portrait and landscape.
2. A long allocation list and long replacement reason: scroll details and keep both actions reachable.
3. Cancel/Edit: no save. Confirm: one save, correct amounts and original validation errors.
4. Backup password/reopen validation: invalid input leaves the dialog open; password window remains secure.
5. Archive/delete/remove/discard alerts, privacy and history lists: native action wrapping and destructive colors.

## Dialog inflation regression fix

The November 28 crash log reports InflateException in mtrl_alert_dialog with “You must supply a layout_width attribute.” The shared overlay incorrectly supplied TextAppearance.RoomKit.SectionTitle directly as materialAlertDialogTitleTextStyle. Material 1.10.0's mtrl_alert_dialog_title layout relies on that view style for both layout_width and layout_height; a text appearance does not supply them.

Widget.RoomKit.Dialog.Title now inherits MaterialAlertDialog.MaterialComponents.Title.Text and applies the existing shared text appearance. The body uses the corresponding native body view style with the shared body text appearance. This fixes the shared source of the reported inflation failure without changing invoice writes, calculations or navigation.

A source-level inheritance check against Material 1.10.0 confirmed that both title dimensions were absent before the fix and resolve to wrap_content afterward. RoomKitDialogInflationTest adds runtime regression coverage for showing/confirming a native issue alert and inflating the invoice detail layout. The Gradle test command was attempted but could not start because the Gradle 9.5.0 distribution is unavailable locally and its download failed with Network is unreachable. These runtime tests and device verification remain pending.
