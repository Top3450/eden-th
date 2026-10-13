#!/usr/bin/env python3
"""Personal changes for Eden TH, applied to a fresh clone of the official Eden source.

Each change looks for the exact upstream text it edits and stops the build with a clear message if
that text has moved, so an upstream change can never be silently skipped or half-applied.

Usage: python3 patches/apply.py <path-to-eden-source>
"""
import pathlib
import re
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
edit(
    "src/android/app/build.gradle.kts",
    "mainline flavour gets its own package and name",
    r'(create\("mainline"\) \{\s*dimension = "version"\s*)resValue\("string", "app_name_suffixed", "Eden"\)',
    r'\1resValue("string", "app_name_suffixed", "Eden TH")\n            applicationId = "dev.eden.eden_emulator.th"',
    regex=True,
)

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

if failures:
    print("\nUpstream Eden changed where Eden TH edits it:", file=sys.stderr)
    for f in failures:
        print("  - " + f, file=sys.stderr)
    sys.exit(1)
print("all Eden TH changes applied")
