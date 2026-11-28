# Color selector audit

Baseline: `77884ee47f001063cd00cbcce5e977b5b6347cb2`.

- Reviewed all **15** XML selectors in `app/src/main/res/color/`.
- No exact XML duplicates were found.
- Resolved palette references and evaluated all **16** combinations of the four state flags present (`enabled`, `checked`, `focused`, `hovered`), preserving first-matching-item order. No selectors have identical output across these combinations.
- Removed two unreferenced selectors: `roomkit_form_button.xml` and `roomkit_radio_info.xml`.
- **13 selectors remain**.

## Why similar selectors remain

| Selectors | Difference |
| --- | --- |
| `roomkit_button_background` / `roomkit_button_background_subtle` | Different disabled colors: border versus track |
| `roomkit_form_outline` / `roomkit_input_outline` | The form outline also changes color while hovered |
| `roomkit_filter_background` / `roomkit_filter_text` | Checked and unchecked colors are reversed for background versus text |
| `roomkit_button_text` / `roomkit_danger_button_text` | Different disabled text colors |

The removed names have no references anywhere else under `app/src`, including XML, Kotlin, Java and test sources. No dynamic color-name loading was found. All other app source/resource files remain byte-for-byte unchanged; remaining XML files parse successfully.

This check does not replace an Android build or device review. Gradle/Android SDK verification remains pending in Android Studio.
