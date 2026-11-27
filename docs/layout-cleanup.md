# Layout cleanup audit

Baseline: `c38e8fc3831925c518b6c95891873120cbee51d3` on `refactor/shared-ui-resources`.

- Before: **56 layout XML files**.
- Removed: **1 unused layout**, `app/src/main/res/layout/item_room_card.xml`.
- After: **55 layout XML files**.

## Why the old room row was removed

No app or test source references `R.layout.item_room_card`, `@layout/item_room_card`, the bare layout name, or the generated `ItemRoomCardBinding`. The current `RoomsAdapter` inflates `ItemRoomOccupiedBinding` and `ItemRoomVacantBinding` instead.

Nine IDs declared only by the removed layout have no mentions elsewhere in app/src, so deleting it does not remove IDs needed by remaining code or layouts. Its referenced drawables are still referenced by other resources/code and were retained.

## Coverage

Checked all 366 tracked source/resource text files under `app/src` against their baseline Git blob hashes. Inventory includes main, tests, Android instrumentation tests, shared tests and validation sources. Checked explicit layout IDs, generated ViewBinding class names, exact-name mentions, navigation XML, layout includes and ViewStubs. Followed layout-to-layout dependencies. No dynamic name-based resource lookup, reflective layout loading or `tools:keep` usage was found.

Retained every layout referenced by any existing code or resource, even if the calling screen's runtime reachability was not established. The 55 retained layouts are statically referenced; this is not a claim that every navigation path has been tested on a device.

## Validation

After removal, all other tracked source/resource bytes are unchanged, remaining XML files parse, and no reference to the removed layout/binding remains. Unique-ID references were checked before deletion. No current screen or dialog was removed.

Android resource compilation, generated ViewBinding compilation and device rendering remain pending because this environment cannot download Gradle and has no configured Android SDK. In Android Studio run `./gradlew :app:processDebugResources :app:assembleDebug`, then review the room list and main navigation before merging.
