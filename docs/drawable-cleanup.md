# Drawable cleanup

Audit baseline: `dd78e14e34a87ed8ae8fdedecde6bc631237fec6` on `refactor/shared-ui-resources`.

- Before: **135 drawable files**.
- Removed: **46 unreferenced drawable files**.
- After: **89 drawable files**.

## Scope and method

Scanned all 419 tracked XML, Kotlin, Java and other text source/resource files under `app/src`, including main, test, androidTest, testShared and validation source sets. Their local bytes were checked against the baseline Git blob hashes before analysis. The application source-set configuration has no additional external main-source directories.

A drawable is retained when any non-drawable source/resource mentions its resource ID or exact name. References inside drawable resources are followed transitively. This deliberately retains drawables referenced from any existing layout or test, even if that layout/test may itself be unused. Manifest, menus, themes, styles, adaptive launcher icons and notification code are included.

No `getIdentifier`, resource-name URI loading, drawable reflection or `tools:keep` references were found in the scanned source files. `mtrl_dropdown_arrow` is a pre-existing Material library resource reference and is not an app drawable being removed.

## Removed files

All files below were under `app/src/main/res/drawable/`:

- `bg_badge_notification.xml`
- `bg_badge_notification_circle.xml`
- `bg_badge_occupied.xml`
- `bg_badge_overdue.xml`
- `bg_badge_paid.xml`
- `bg_badge_partial_pill.xml`
- `bg_badge_unpaid.xml`
- `bg_badge_unpaid_pill.xml`
- `bg_badge_vacant.xml`
- `bg_border_gray.xml`
- `bg_bottom_sheet_rounded.xml`
- `bg_btn_assign_tenant.xml`
- `bg_btn_black_rounded.xml`
- `bg_btn_outline.xml`
- `bg_btn_secondary.xml`
- `bg_btn_secondary_outline.xml`
- `bg_dark_menu_popup.xml`
- `bg_meter_card_header.xml`
- `bg_meter_input_field.xml`
- `bg_more_card.xml`
- `bg_more_icon_container.xml`
- `bg_occupancy_progress.xml`
- `bg_rd_green.xml`
- `bg_rd_red.xml`
- `bg_roomkit_property_sheet.xml`
- `bg_roomkit_sheet_handle.xml`
- `bg_search_input.xml`
- `bg_tenant_card.xml`
- `bg_usage_card.xml`
- `ic_add.xml`
- `ic_close.xml`
- `ic_electricity.xml`
- `ic_launcher_background.xml`
- `ic_phone_outline.xml`
- `ic_qr_code.xml`
- `ic_rp_edit.xml`
- `ic_tenants.xml`
- `ic_trending_up.xml`
- `mr_bolt.xml`
- `mr_drop.xml`
- `mr_erorr_background.xml`
- `mr_icon_title.xml`
- `mr_notice_background.xml`
- `mr_usage_background.xml`
- `rkm_month_background.xml`
- `rkm_radio_background.xml`

## Validation and limits

After deletion, all remaining tracked source/resource bytes match the baseline; no screen, style, manifest, or code behavior was edited. XML files parse successfully and no remaining source/resource references a removed drawable. Required launcher, splash and notification resources remain.

Android Lint, resource compilation and device rendering were not run: the execution environment cannot download Gradle and has no configured Android SDK. Before merging, run `./gradlew :app:processDebugResources :app:assembleDebug` in Android Studio's terminal and review the app's main screens.

## Follow-up: duplicate backgrounds

A subsequent consolidation merged five identical background pairs, reducing the remaining 89 files to **84**. See [drawable-duplicates.md](drawable-duplicates.md) for the shared names and validation. The counts above describe the initial unused-resource audit.
