# RoomKit UI resources

Use the existing resources when adding a screen. Do not copy colors or styles into a new file named after the screen.

| Resource | Location | Purpose |
| --- | --- | --- |
| Colors | `app/src/main/res/values/colors.xml` | One name per exact color value; no screen aliases |
| State colors | `app/src/main/res/color/roomkit_*.xml` | Disabled, focused, checked and selected colors |
| Typography | `app/src/main/res/values/typography.xml` | Reusable headings, body text, captions and labels |
| Components | `app/src/main/res/values/styles.xml` | Cards, buttons, text fields, chips and navigation |
| Dimensions | `app/src/main/res/values/dimens.xml` | Text sizes (sp), spacing, padding, corner radii and component sizes (dp) |
| App theme | `app/src/main/res/values/themes.xml` | Maps the palette to Material theme roles |

## New screen examples

```xml
<TextView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/page_title"
    android:textAppearance="@style/TextAppearance.RoomKit.ScreenTitle" />

<com.google.android.material.button.MaterialButton
    style="@style/Widget.RoomKit.Button.Primary"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="@string/save" />

<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.RoomKit.Input"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <com.google.android.material.textfield.TextInputEditText
        style="@style/Widget.RoomKit.EditText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</com.google.android.material.textfield.TextInputLayout>
```

The examples require your screen's string resources and the usual Android XML namespace. Keep IDs, labels, input types and click handling in the screen. Put reusable appearance in these shared files.

Prefer `android:textAppearance` for a text appearance. A few existing section styles also contain layout margins: use those with `style` to retain their layout behavior.

## Shared base styles

Common input geometry, edit-text sizing/padding, and filled/outlined button settings are defined in `Base.Widget.RoomKit.*` styles. Public `Widget.RoomKit.*` styles inherit those settings and declare their differences. Use the public styles in layouts; edit the base when the shared appearance should change.

All standard outlined cards use `Widget.RoomKit.Card`. The former `Card.Padded` and `Card.Outlined` names were removed because their appearance was identical. Their width, height and margins are now declared on the views. Card variants such as `Card.Medium` and `Card.Form` inherit the standard card and retain their distinct radius or border.

Material exposed dropdowns retain their dedicated Material parent so dropdown defaults continue to work. Their shape sizes reference the same shared dimensions. Variants with different actual properties remain separate.

In Kotlin, use `ContextCompat.getColor(context, R.color.roomkit_text_primary)` for an explicit palette color, or resolve a Material theme attribute when the component should follow the theme. Do not add `Color.parseColor` literals to app screens.

For a text view created in Kotlin, import `com.example.roomkit.Date.Ui.Common.setRoomKitTextSize` and call `view.setRoomKitTextSize(R.dimen.roomkit_text_14)`. This reads the same font-scale-aware dimension as XML. Programmatic tenant-picker labels also take these dimension resource IDs.

## Editing a design

- Change a palette entry to update every reference to that exact color. White and black also have one definition each; changing them affects all uses.
- Change a typography style to update that text role. Change `dimens.xml` only when every use of the size should change.
- Reuse an existing component style before creating a variant. Add a variant only for a real visual difference, and name it for that difference rather than a screen.
- Keep deliberate state differences. Disabled button backgrounds and error colors have different values and must not be merged simply because they look similar.

This cleanup preserves existing exact colors, sizes and inherited style properties. It does not normalize similar-but-different colors or redesign layouts. Invoice PDF rendering and canvas chart sizing maintain their independent rendering rules.

## Validation

From a machine with Android Studio and the Android SDK installed, run:

```sh
./gradlew :app:processDebugResources :app:assembleDebug
```

Review the main screens in light/dark device settings and check focused inputs, disabled buttons, dropdowns and navigation selection before merging.
