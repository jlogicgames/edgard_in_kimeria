from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from check_web_release_artifact import REQUIRED_RUNTIME_FILES, validate_release_artifact


class WebReleaseArtifactTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        for relative in REQUIRED_RUNTIME_FILES:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"runtime")
        (self.root / "scripts/howler.js").write_text("/*! MIT License */\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_accepts_runtime_assets_and_preserved_license_notice(self) -> None:
        self.assertEqual([], validate_release_artifact(self.root))

    def test_rejects_missing_runtime_map_or_localization(self) -> None:
        (self.root / "assets/tiles/forest.tmx").unlink()
        (self.root / "assets/localization/uk.json").unlink()
        errors = validate_release_artifact(self.root)
        self.assertIn("missing required runtime file: assets/tiles/forest.tmx", errors)
        self.assertIn("missing required runtime file: assets/localization/uk.json", errors)

    def test_rejects_debug_editor_os_and_server_payloads(self) -> None:
        forbidden = (
            "app.js.map",
            "app.teavmdbg",
            "assets/tiles/level.tiled-session",
            "assets/tiles/.DS_Store",
            "WEB-INF/web.xml",
            "scripts/devserver/bootstrap.js",
            "assets/images/ui/boss/README.md",
        )
        for relative in forbidden:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"unexpected")
        errors = validate_release_artifact(self.root)
        for relative in forbidden:
            self.assertTrue(any(relative in error for error in errors), relative)

    def test_preserves_license_and_notice_markdown(self) -> None:
        for relative in ("LICENSE.md", "NOTICE.md", "assets/LICENSE.md", "assets/README.md"):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("third-party license notice", encoding="utf-8")
        self.assertEqual([], validate_release_artifact(self.root))

    def test_rejects_missing_howler_license_notice(self) -> None:
        (self.root / "scripts/howler.js").write_text("// bundled runtime\n", encoding="utf-8")
        self.assertIn("howler.js MIT license notice is missing", validate_release_artifact(self.root))


if __name__ == "__main__":
    unittest.main()
