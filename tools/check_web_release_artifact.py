#!/usr/bin/env python3
"""Validate the exact static web directory uploaded to GitHub Pages."""

from __future__ import annotations

import argparse
import gzip
import sys
from pathlib import Path


REQUIRED_RUNTIME_FILES = (
    "index.html",
    "app.js",
    "favicon.ico",
    "scripts/gdx.wasm.js",
    "scripts/howler.js",
    "assets/audio/main_menu.mp3",
    "assets/fonts/generated/QuestSquare-20.fnt",
    "assets/fonts/generated/QuestSquare-20.png",
    "assets/fonts/generated/QuestSquare-13.fnt",
    "assets/fonts/generated/QuestSquare-13.png",
    "assets/fonts/generated/QuestSquare-15.fnt",
    "assets/fonts/generated/QuestSquare-15.png",
    "assets/fonts/generated/QuestSquare-30.fnt",
    "assets/fonts/generated/QuestSquare-30.png",
    "assets/fonts/NanoPlus.ttf",
    "assets/fonts/QuestSquare.ttf",
    "assets/images/hero/Player.png",
    "assets/localization/en.json",
    "assets/localization/de.json",
    "assets/localization/pl.json",
    "assets/localization/uk.json",
    "assets/localization/be.json",
    "assets/tiles/forest.tmx",
    "assets/tiles/forest-1.tmx",
    "assets/tiles/Forest.tsx",
)

FORBIDDEN_SUFFIXES = {
    ".class",
    ".java",
    ".map",
    ".tiled-project",
    ".tiled-session",
    ".teavmdbg",
    ".debug",
}


def validate_release_artifact(root: Path) -> list[str]:
    errors: list[str] = []
    if not root.is_dir():
        return [f"release artifact directory does not exist: {root}"]

    files = sorted(path for path in root.rglob("*") if path.is_file())
    relative_paths = {path.relative_to(root).as_posix() for path in files}
    for required in REQUIRED_RUNTIME_FILES:
        if required not in relative_paths:
            errors.append(f"missing required runtime file: {required}")

    for path in files:
        relative = path.relative_to(root)
        parts = relative.parts
        if path.name == ".DS_Store":
            errors.append(f"OS metadata must not be uploaded: {relative}")
        if path.suffix.lower() in FORBIDDEN_SUFFIXES:
            errors.append(f"non-runtime source/debug/editor file must not be uploaded: {relative}")
        if any(part.lower() in {"web-inf", "dev-server", "devserver", "livereload", "live-reload"} for part in parts):
            errors.append(f"server or development payload must not be uploaded: {relative}")
        if relative.as_posix() == "assets/images/ui/boss/README.md":
            errors.append(f"authoring documentation must not be uploaded as a runtime asset: {relative}")

    howler = root / "scripts/howler.js"
    if howler.is_file() and "MIT License" not in howler.read_text(encoding="utf-8", errors="replace")[:512]:
        errors.append("howler.js MIT license notice is missing")

    if not errors:
        raw_bytes = sum(path.stat().st_size for path in files)
        gzip_bytes = sum(len(gzip.compress(path.read_bytes(), compresslevel=9, mtime=0)) for path in files)
        print(
            f"Web release artifact passed: {len(files)} files, "
            f"{raw_bytes} raw bytes, {gzip_bytes} per-file gzip bytes."
        )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    repo_root = Path(__file__).resolve().parents[1]
    upload_directory = repo_root / "web/build/dist/js/release/webapp"
    errors = validate_release_artifact(upload_directory)
    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
