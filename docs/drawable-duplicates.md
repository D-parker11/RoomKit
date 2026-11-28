# Duplicate drawable cleanup

Baseline: `72454b5364fc946d277eaa79e998a26c35c23053`.

The preceding unused-resource cleanup left **89 drawable files**. This follow-up merges **five pairs of identical XML background shapes**, leaving **84 drawable files**.

| Previous names | Shared file |
| --- | --- |
| `pv_drag_handle.xml` + `bg_bottom_sheet_handle.xml` | `bg_bottom_sheet_handle.xml` |
| `replacement_draft_pill.xml` + `replacement_info_bg.xml` | `bg_info_soft.xml` |
| `bg_rm_icon.xml` + `rkm_icon_background.xml` | `bg_icon_soft.xml` |
| `bg_rvi_info.xml` + `rkm_notice_background.xml` | `bg_notice_neutral.xml` |
| `bg_badge_tenant_previous.xml` + `bg_badge_vacant_pill.xml` | `bg_badge_neutral_pill.xml` |

Comparison includes element types, all attribute values, child order and meaningful text, ignoring only XML formatting, comments and attribute order. Every pair has the same shape definition. Vector dimensions, viewport, paths, colors and stroke properties were also checked for exact duplicates; no additional exact XML duplicate groups were found. Similar names or purposes alone are not a reason to merge different icons.

All XML and Kotlin references to the removed names now use the shared resource. The project uses no dynamic name-based drawable lookup. Checks verify that the retained shared drawables have the same XML definitions as their originals, other source/resource changes are solely the reference replacements, XML remains well-formed and references to removed drawables are gone.

Android resource compilation and device rendering remain unverified in this environment. Run `./gradlew :app:processDebugResources :app:assembleDebug` in Android Studio and check the affected screens before merging.
