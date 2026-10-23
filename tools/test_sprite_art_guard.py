#!/usr/bin/env python3
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import struct
import tempfile
import unittest

import check_sprite_art as guard


class SpriteArtGuardTest(unittest.TestCase):
    def test_world_and_hud_are_different_png_contracts(self):
        def metadata(size, mode=3, alpha=b'\x00\xff', colors=16):
            # Metadata-only fixture, not an artwork generator.
            def chunk(name, value):
                return struct.pack('>I', len(value)) + name + value + b'\x00' * 4
            return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', *size, 4, mode, 0, 0, 0))
                    + chunk(b'PLTE', b'\x00' * (3 * colors)) + chunk(b'tRNS', alpha))
        self.assertFalse(guard.png_contract('torch.png', metadata((8, 16)), (8, 16), 16, True))
        self.assertTrue(guard.png_contract('torch.png', metadata((32, 32)), (8, 16), 16, True))
        self.assertTrue(guard.png_contract('torch.png', metadata((8, 16), colors=256), (8, 16), 16, True))
        self.assertTrue(guard.png_contract('torch.png', metadata((8, 16), alpha=b'\x00\x80'), (8, 16), 16, True))
        self.assertFalse(guard.png_contract('chicken.png', metadata((128, 128), colors=256, alpha=b'\x00\x80'),
                                            (128, 128), 256, False))
        self.assertTrue(guard.png_contract('torch.png', metadata((8, 16), mode=6), (8, 16), 16, True))

    def test_rejects_original_holder_and_renamed_or_relocated_art(self):
        for path in ['entity/items/Carryable.java', 'effects/Torch.java', 'ui/NewTorch.java']:
            for name in ['renderHolder', 'render', 'paintThing']:
                code = f'public void {name}(Batch batch) {{ var p = Shaders.whiteQuad(); batch.draw(p, 0, 0, 8, 12); }}'
                self.assertTrue(guard.violations(path, code), (path, name))

    def test_particle_permission_does_not_authorize_torch_body_quads(self):
        self.assertFalse(guard.violations('effects/Torch.java', 'public void render(Batch batch) { var p = SoftDot.region(); }'))
        self.assertTrue(guard.violations('effects/Torch.java', 'public void render(Batch batch) { var p = Shaders.whiteQuad(); }'))

    def test_sprite_rendering_and_comment_text_pass(self):
        code = '''public void render(Batch batch) {
            // old renderHolder used Shaders.whiteQuad()
            String example = "new Pixmap(10, 10)";
            batch.draw(sprite, x, y, w, h);
        }'''
        self.assertFalse(guard.violations('entity/items/Torch.java', code))

    def test_sprite_guard_cannot_be_evaded_by_new_helper_names_or_shapes(self):
        for primitive in ['SoftDot.glowRegion()', 'new Pixmap(8, 8, format)', 'new ShapeRenderer()', 'renderer.triangle(0,0,1,1,2,2)']:
            self.assertTrue(guard.violations('entity/NewItem.java', f'private void helper() {{ {primitive}; }}'))

    def test_legitimate_gpu_storage_and_selection_ui_are_distinct(self):
        self.assertFalse(guard.violations('effects/Shaders.java', 'public static TextureRegion whiteQuad() { return new Pixmap(1, 1); }'))
        self.assertFalse(guard.violations('ui/InteractionHighlight.java', 'private static void corners() { Shaders.whiteQuad(); }'))
        self.assertTrue(guard.violations('ui/InteractionHighlight.java', 'private static void objectArtwork() { Shaders.whiteQuad(); }'))

    def test_offline_export_may_resize_art_but_not_paint_it(self):
        self.assertFalse(guard.python_art_violations('tools/export.py', "from PIL import Image\nim=Image.open('master.png')\nim.resize((32,32)).save('torch.png')"))
        self.assertTrue(guard.python_art_violations('tools/torch.py', "from PIL import ImageDraw as D\nD.Draw(canvas).rectangle((0,0,4,8))"))
        self.assertTrue(guard.python_art_violations('tools/torch.py', "im.putpixel((0,0), (255,0,0))"))

    def test_hook_checks_real_files_written_by_any_tool_and_blocks_finish(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / guard.BASE / 'entity/items/Bad.java'
            source.parent.mkdir(parents=True)
            source.write_text('public void render(Batch b) { b.draw(SoftDot.region(), 0, 0, 5, 5); }')
            for event in ['Stop', 'PostToolUse']:
                result = subprocess.run([sys.executable, guard.__file__, '--root', directory, '--hook'],
                    input=json.dumps({'hook_event_name': event, 'tool_name': 'Bash'}), text=True, capture_output=True)
                self.assertEqual(2, result.returncode)
                self.assertIn('entity/items/Bad.java', result.stderr)
            source.write_text('public void render(Batch b) { b.draw(sprite, 0, 0, 5, 5); }')
            result = subprocess.run([sys.executable, guard.__file__, '--root', directory, '--hook'],
                input='{"hook_event_name":"Stop"}', text=True, capture_output=True)
            self.assertEqual(0, result.returncode)
            self.assertEqual({}, json.loads(result.stdout))


if __name__ == '__main__':
    unittest.main()
