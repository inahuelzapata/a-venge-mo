#!/usr/bin/env python3
"""Builds docs/design/comparisons/ from the iOS captures and the Android renders.

For every capture in docs/design/ios-captures/ it writes:
  <name>.png          iOS (left) | Android (right), same width (1206 px = 402 pt/dp at 3x)
  <name>-overlay.png  Android at 50% over iOS, for alignment checks

Android images come from the ParityScreenshotTest Paparazzi goldens
(android/app/src/test/snapshots/images), which render at the iPhone's geometry,
and are copied to docs/design/android-captures/<name>.png.

Usage (Pillow required: pip install pillow), from the repo root:
  (cd android && ./gradlew recordPaparazziDebug --tests '*ParityScreenshotTest*')
  python3 docs/design/capture/compare.py
"""
import glob
import os
import shutil
import sys

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
IOS = os.path.join(ROOT, "docs/design/ios-captures")
ANDROID = os.path.join(ROOT, "docs/design/android-captures")
OUT = os.path.join(ROOT, "docs/design/comparisons")
GOLDENS = os.path.join(ROOT, "android/app/src/test/snapshots/images")
GAP = 24
LABEL = 64


def android_golden(name):
    matches = glob.glob(os.path.join(GOLDENS, f"*ParityScreenshotTest_*_{name}.png"))
    return matches[0] if matches else None


def main():
    os.makedirs(ANDROID, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    missing = []
    for ios_path in sorted(glob.glob(os.path.join(IOS, "*.png"))):
        name = os.path.splitext(os.path.basename(ios_path))[0]
        golden = android_golden(name)
        if golden is None:
            missing.append(name)
            continue
        android_path = os.path.join(ANDROID, f"{name}.png")
        shutil.copyfile(golden, android_path)

        ios = Image.open(ios_path).convert("RGB")
        android = Image.open(android_path).convert("RGB")
        if android.size != ios.size:
            android = android.resize((ios.width, round(android.height * ios.width / android.width)))
            android = android.crop((0, 0, ios.width, ios.height))

        side = Image.new("RGB", (ios.width * 2 + GAP, ios.height + LABEL), "white")
        side.paste(ios, (0, LABEL))
        side.paste(android, (ios.width + GAP, LABEL))
        draw = ImageDraw.Draw(side)
        draw.text((24, 18), f"iOS  {name}", fill="black", font_size=36)
        draw.text((ios.width + GAP + 24, 18), f"Android  {name}", fill="black", font_size=36)
        side.save(os.path.join(OUT, f"{name}.png"), optimize=True)

        Image.blend(ios, android, 0.5).save(os.path.join(OUT, f"{name}-overlay.png"), optimize=True)
        print(f"{name}: ok")
    if missing:
        print("No Android render for: " + ", ".join(missing), file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
