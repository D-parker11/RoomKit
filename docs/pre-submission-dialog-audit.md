# Pre-submission check: missing dialog/view layout dimensions

Audited branch `refactor/shared-ui-resources` at `791c0e261fe20930f9e12551e87fe1fae274db05`, including the dialog inflation fix. Local audited Android files matched the remote commit by Git blob hash.

## Result

- **0 additional missing width/height cases found by the source check.**
- **57 layout files**, containing **1,813 view elements**, checked for required dimensions, including values supplied by local style inheritance.
- Shared dialog title dimensions checked against the Material 1.10.0 native title style contract in both default and night values variants. Both resolve after the fix.
- **13 screens/features** use the shared alert theme directly or through the shared review helper. The original defect was shared by these entry points; it was not a separate bug in each screen.
- **25 MaterialAlertDialogBuilder construction sites** remain: 24 in screen files and one in ReviewDialogs, reused by payment and meter reviews.

| Screen / feature | Dialogs to check on the installed build |
| --- | --- |
| Invoice preview | Issue invoice: cancel, confirm, then open details |
| Replacement / saved draft invoice | About, issue, delete draft, unsaved changes |
| Invoice details | Payment void history and invoice void dialog |
| Record payment | Received amount and allocation review |
| Meter readings | Replacement review, reload, history details |
| Move-in | Review and tenant assignment error |
| Move-out | Record refund and reference input |
| Room editing | Archive and delete confirmation |
| Payment setup | Discard and remove QR confirmation |
| Settings | Privacy message |
| Backup / month tools | Close, reopen, restore, password input and completion |
| Add tenant | Duplicate tenant choice |
| Tenant details | Historical invoice list / empty state |

## Repeatable source guard

Run from the project root:

```sh
python3 tools/check_layout_dimensions.py
```

This standard-library checker reads the checked-out resources, follows local style inheritance, checks required view dimensions across values variants, and checks the shared Material dialog title view style. Its known native title parent contract is pinned to the Material 1.10.0 version used by this project. It intentionally treats unresolved dimensions as a failure. If upgrading Material, recheck the native title style contract.

The guard was run successfully on all 57 current layout files. Negative checks confirmed that it rejects both the original crashing title theme and a view with a removed layout_width. Those negative checks used temporary copies, not project changes.

## Runtime coverage added (execution pending)

`RoomKitDialogInflationTest` covers default and explicit dialog themes, a confirmation callback, choice/list/input dialog variants, and invoice detail layout inflation.

`RoomKitLayoutsInflationTest` covers all 56 screen/dialog/sheet/item layouts. It excludes activity_main because inflating its NavHost requires real activity/navigation startup, which is a separate integration test. The test inflates views without performing database writes or simulating full screen behavior.

Run both in Android Studio or from the project root:

```sh
./gradlew :app:testDebugUnitTest --tests '*RoomKit*InflationTest'
```

**These Android runtime tests have not passed in this environment:** the Gradle wrapper needs an unavailable Gradle 9.5.0 distribution and the download fails with `Network is unreachable`. Adding tests does not mean they have executed. This audit establishes no further occurrences of the known missing-dimension defect in source; it does not establish that the app has no other crashes.

## Before submitting

Pull the branch, sync/build successfully, run the runtime checks above, and install that updated build. An already installed old APK still contains the old dialog theme until replaced. Test the 13 feature entry points above using test data, including issue invoice and record payment end-to-end, with Logcat open. Confirm large text does not hide actions and that Cancel/Edit performs no write. Do not uninstall or clear app storage as part of this check.
