#!/usr/bin/env python3
"""Personal changes for Eden TH, applied to a fresh clone of the official Eden source.

Each change looks for the exact upstream text it edits and stops the build with a clear message if
that text has moved, so an upstream change can never be silently skipped or half-applied.

Usage: python3 patches/apply.py <path-to-eden-source>
"""
import pathlib
import re
import shutil
import sys

root = pathlib.Path(sys.argv[1])
failures = []


def edit(rel, description, old, new, count=1, regex=False):
    path = root / rel
    text = path.read_text(encoding="utf-8")
    found = len(re.findall(old, text, flags=re.S)) if regex else text.count(old)
    if found != count:
        failures.append(f"{rel}: {description} - expected {count} match(es), found {found}")
        return
    text = re.sub(old, new, text, count=count, flags=re.S) if regex else text.replace(old, new, count)
    path.write_text(text, encoding="utf-8")
    print(f"ok  {rel}: {description}")


# 1. Its own app: installs beside the official Eden (and its data stays separate), named Eden TH.
#    Only inside the mainline flavour's block, whatever else upstream adds to it.
def mainline_block(text):
    m = re.search(r'create\("mainline"\) \{.*?\n        \}', text, flags=re.S)
    return m


gradle = root / "src/android/app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
m = mainline_block(text)
if not m:
    failures.append("build.gradle.kts: no mainline flavour block")
else:
    block = m.group(0)
    named = 0
    for old, new in (('resValue("string", "app_name_suffixed", "Eden")', 'resValue("string", "app_name_suffixed", "Eden TH")'),
                     ('"appNameBase" to "Eden")', '"appNameBase" to "Eden TH")')):
        if old in block:
            block = block.replace(old, new)
            named += 1
    if not named:
        failures.append("build.gradle.kts: mainline flavour sets no app name Eden TH could change")
    if 'applicationId' in block:
        failures.append("build.gradle.kts: mainline flavour already sets an applicationId")
    else:
        block = block.replace('dimension = "version"',
                              'dimension = "version"\n            applicationId = "dev.eden.eden_emulator.th"', 1)
    if 'applicationId = "dev.eden.eden_emulator.th"' in block:
        gradle.write_text(text[:m.start()] + block + text[m.end():], encoding="utf-8")
        print(f"ok  build.gradle.kts: mainline flavour is Eden TH ({named} name setting(s), own package)")

# 2. Custom GPU drivers on Mali too. Eden offers its driver manager only where KGSL (Adreno) exists;
#    the loader itself (adrenotools, custom-driver mode) does not need KGSL, and PanVK kbase ships
#    driver packages for Mali-G615 made for Eden. Without a driver installed nothing changes: the
#    phone's own Vulkan driver is used, as before.
edit(
    "src/android/app/src/main/jni/native.cpp",
    "driver manager also on Mali (/dev/mali0)",
    'return access(KgslPath, F_OK) == 0;',
    'return access(KgslPath, F_OK) == 0 || access("/dev/mali0", F_OK) == 0;  // Eden TH: Mali too',
)

# 3. Motion UI: game cards lean with the phone (gyroscope), swing with the scroll and dip under the
#    finger, all through a spring; the carousel turns its side cards (cover flow); a glow behind the
#    list drifts with the tilt. New files live in patches/files and are copied over the tree.
files = pathlib.Path(__file__).resolve().parent / "files"
for src in sorted(p for p in files.rglob("*") if p.is_file()):
    dst = root / src.relative_to(files)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)
    print(f"ok  {dst.relative_to(root)}: added")

VIEWS = "src/android/app/src/main/java/org/yuzu/yuzu_emu/views"
edit(
    f"{VIEWS}/GradientBorderCardView.kt",
    "gradient rim on every card, full strength when pressed",
    """        if (showGradientBorder) {
            canvas.drawPath(borderPath, borderPaint)
        }""",
    """        // Eden TH: a soft glowing rim all the time, full strength when pressed or focused.
        borderPaint.alpha = if (showGradientBorder) 255 else 120
        canvas.drawPath(borderPath, borderPaint)""",
)
edit(
    f"{VIEWS}/GradientBorderCardView.kt",
    "cards tilt, swing and shine (TiltCard)",
    """    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateThemeState()
    }
}""",
    """    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateThemeState()
        tilt.onAttached()
    }

    // Eden TH: motion effects
    private val tilt = TiltCard(this)

    /** The list's own turn of this card (carousel side cards); the tilt is added on top. */
    var baseRotationY: Float
        get() = tilt.baseRotationY
        set(value) {
            tilt.baseRotationY = value
        }

    override fun onDetachedFromWindow() {
        tilt.onDetached()
        super.onDetachedFromWindow()
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        tilt.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        tilt.cornerRadius = radius
        tilt.drawShine(canvas)
    }
}""",
)
edit(
    f"{VIEWS}/CarouselRecyclerView.kt",
    "cover flow: side cards turn away from the centre",
    "        child.scaleY = scale\n",
    """        child.scaleY = scale

        // Eden TH: cover flow - the further from the centre, the more a card turns away.
        val coverFlow = (-Math.toDegrees(angle.toDouble()).toFloat() * 0.9f).coerceIn(-45f, 45f)
        if (child is org.yuzu.yuzu_emu.views.GradientBorderCardView) {
            child.baseRotationY = coverFlow
        } else {
            child.rotationY = coverFlow
        }
""",
)
edit(
    f"{VIEWS}/CarouselRecyclerView.kt",
    "cover flow reset when leaving the carousel",
    "                child?.alpha = 1f\n",
    """                child?.alpha = 1f
                if (child is org.yuzu.yuzu_emu.views.GradientBorderCardView) {
                    child.baseRotationY = 0f
                } else {
                    child?.rotationY = 0f
                }
""",
)
edit(
    "src/android/app/src/main/res/layout/fragment_games.xml",
    "tilting glow behind the game list",
    """    <LinearLayout
        android:id="@+id/header\"""",
    """    <org.yuzu.yuzu_emu.views.TiltAuroraView
        android:id="@+id/eden_th_aurora"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        />

    <LinearLayout
        android:id="@+id/header\"""",
)
edit(
    "src/android/app/src/main/res/menu/menu_game_views.xml",
    "motion effects switch in the view menu",
    "    </group>\n</menu>",
    """    </group>
    <item
        android:id="@+id/view_tilt_effects"
        android:checkable="true"
        android:title="@string/eden_th_tilt_effects" />
</menu>""",
)
GAMES = "src/android/app/src/main/java/org/yuzu/yuzu_emu/ui/GamesFragment.kt"
edit(
    GAMES,
    "motion effects switch shows its state",
    "        popup.menuInflater.inflate(R.menu.menu_game_views, popup.menu)\n",
    """        popup.menuInflater.inflate(R.menu.menu_game_views, popup.menu)
        popup.menu.findItem(R.id.view_tilt_effects)?.isChecked =
            org.yuzu.yuzu_emu.views.TiltSensor.isEnabled(requireContext())
""",
)
edit(
    GAMES,
    "motion effects switch toggles",
    "                R.id.view_carousel -> {\n",
    """                R.id.view_tilt_effects -> {
                    val on = !org.yuzu.yuzu_emu.views.TiltSensor.isEnabled(requireContext())
                    org.yuzu.yuzu_emu.views.TiltSensor.setEnabled(requireContext(), on)
                    item.isChecked = on
                    true
                }

                R.id.view_carousel -> {
""",
)

if failures:
    print("\nUpstream Eden changed where Eden TH edits it:", file=sys.stderr)
    for f in failures:
        print("  - " + f, file=sys.stderr)
    sys.exit(1)
print("all Eden TH changes applied")
