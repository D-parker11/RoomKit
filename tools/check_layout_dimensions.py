#!/usr/bin/env python3
"""Check XML view LayoutParams and the shared Material dialog title contract.

Run from any directory with Python 3. This is a source check, not an Android
resource linker, inflater, or replacement for device tests.
"""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"
ANDROID = "{http://schemas.android.com/apk/res/android}"
DIMENSIONS = ("android:layout_width", "android:layout_height")
# Material 1.10.0 defines both dimensions on this native title view style.
# Its mtrl_alert_dialog_title.xml deliberately omits inline dimensions.
NATIVE_TITLE = "Base.MaterialAlertDialog.MaterialComponents.Title.Text"
NATIVE_STYLES = {
    NATIVE_TITLE: dict(zip(DIMENSIONS, ("wrap_content", "wrap_content"))),
    "MaterialAlertDialog.MaterialComponents.Title.Text":
        dict(zip(DIMENSIONS, ("wrap_content", "wrap_content"))),
}
SKIP_TAGS = {"include", "merge", "requestFocus", "tag", "data", "variable", "import"}


def audit():
    styles = {}
    variants = {"values"}
    for folder in sorted(RES.glob("values*")):
        for path in sorted(folder.glob("*.xml")):
            for node in ET.parse(path).getroot():
                if node.tag == "style":
                    styles[folder.name, node.attrib["name"]] = node
                    variants.add(folder.name)

    def properties(name, variant="values", chain=()):
        if name in chain:
            raise ValueError("Style inheritance cycle: " + " -> ".join(chain + (name,)))
        node = styles.get((variant, name), styles.get(("values", name)))
        if node is None:
            return dict(NATIVE_STYLES.get(name, {}))
        parent = node.get("parent", name.rpartition(".")[0])
        result = properties(parent, variant, chain + (name,)) if parent else {}
        result.update({item.attrib["name"]: (item.text or "").strip() for item in node})
        return result

    failures = []
    views = 0
    layouts = sorted(RES.glob("layout*/*.xml"))
    for path in layouts:
        for node in ET.parse(path).iter():
            if node.tag in SKIP_TAGS:
                continue
            views += 1
            style = node.get("style", "").removeprefix("@style/")
            # Check all values variants too: a night style may replace a base style.
            for variant in sorted(variants):
                attrs = properties(style, variant)
                for attr in DIMENSIONS:
                    inline = node.get(ANDROID + attr.removeprefix("android:"))
                    if inline is None and attr not in attrs:
                        failures.append(f"{path.name} ({variant}): {node.tag} "
                                        f"{node.get(ANDROID + 'id', '')} needs {attr}")

    for variant in sorted(variants):
        overlay = properties("ThemeOverlay.RoomKit.Dialog", variant)
        title = overlay.get("materialAlertDialogTitleTextStyle", "")
        if not title.startswith("@style/"):
            failures.append(f"{variant}: shared dialog title style is not explicit")
            continue
        attrs = properties(title.removeprefix("@style/"), variant)
        for attr in DIMENSIONS:
            if attr not in attrs:
                failures.append(f"{variant}: shared dialog title style needs {attr}")

    if failures:
        print("FAIL: required view dimensions are missing or unresolved:", file=sys.stderr)
        print("\n".join(sorted(set(failures))), file=sys.stderr)
        return 1
    print(f"PASS: {len(layouts)} layouts, {views} view elements; dimensions resolve.")
    print(f"PASS: shared dialog title dimensions resolve in {len(variants)} values variants.")
    print("Android build, runtime inflation, and device testing remain separate checks.")
    return 0


if __name__ == "__main__":
    sys.exit(audit())
