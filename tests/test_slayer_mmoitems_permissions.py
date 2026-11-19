"""Regression for the non-destructive MMOItems Slayer permission installer."""
from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.apply_slayer_mmoitems_permissions import (
    ITEMS, PreflightError, apply, check_enabled, node, patch_type, preview,
)


def fixtures(root: Path, newline: str = "\n", existing: bool = False) -> dict[str, str]:
    folder = root / "item"
    folder.mkdir(parents=True)
    (root / "config.yml").write_text("permissions:\n    abilities: false\n    items: true\n", encoding="utf-8")
    originals = {}
    for kind in ("SWORD", "ARMOR"):
        bits = ["# Preserve original YAML and item statistics"]
        for k, item_id, _level in ITEMS:
            if k != kind:
                continue
            bits += [f"{item_id}:", "  base:", "    material: NETHERITE_SWORD" if kind == "SWORD"
                     else "    material: NETHERITE_CHESTPLATE"]
            if existing and item_id == ("FILOPODRIDO" if kind == "SWORD" else "ZOMBIEARMOR_1"):
                bits += ["    permission:", '    - "existing.example.permission"']
            bits += ["    name: '<gradient:#AABBCC:#112233>Objeto</gradient>'",
                     "    lore:", "    - '&7 Test de presentación'", "    damage: 12.5",
                     f"    custom-model-data: {_level + 100}.0"]
        content = newline.join(bits) + newline
        file = folder / f"{kind}.yml"
        file.write_bytes(content.encode("utf-8"))
        originals[kind] = content
    return originals


class SlayerMmoItemsPermissions(unittest.TestCase):
    def test_ids_and_nodes_are_unique_and_covered_in_java(self):
        self.assertEqual(len(ITEMS), 16)
        self.assertEqual(len({(k, i) for k, i, _ in ITEMS}), 16)
        java = (ROOT / "src/main/java/dev/hydev/spectrecore/systems/SlayerProgressionRules.java").read_text()
        for kind, item_id, level in ITEMS:
            self.assertIn(f'nodes.add("{node(kind, item_id)}")', java)
            self.assertIn(f"level >= {level}", java)

    def test_preview_then_atomic_apply_and_backup(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = fixtures(root)
            before = preview(root)
            self.assertEqual(sum(n for _, _, n in before.values()), 16)
            # Preview never writes server files.
            for kind in originals:
                self.assertEqual((root / "item" / f"{kind}.yml").read_text(), originals[kind])
            backup = apply(root, before)
            self.assertIsNotNone(backup)
            for kind in originals:
                current = (root / "item" / f"{kind}.yml").read_text()
                self.assertEqual((backup / f"{kind}.yml").read_text(), originals[kind])
                parsed = yaml.safe_load(current)
                for k, item_id, _ in ITEMS:
                    if k == kind:
                        self.assertIn(node(kind, item_id), parsed[item_id]["base"]["permission"])
                        self.assertEqual(parsed[item_id]["base"]["damage"], 12.5)
                        self.assertIn("gradient:", parsed[item_id]["base"]["name"])
            # A second application has no change and creates no backups.
            again = preview(root)
            self.assertEqual(sum(n for _, _, n in again.values()), 0)
            self.assertIsNone(apply(root, again))

    def test_existing_permissions_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixtures(root, existing=True)
            apply(root, preview(root))
            for kind, item_id in (("SWORD", "FILOPODRIDO"), ("ARMOR", "ZOMBIEARMOR_1")):
                doc = yaml.safe_load((root / "item" / f"{kind}.yml").read_text())
                self.assertEqual(doc[item_id]["base"]["permission"], [
                    "existing.example.permission", node(kind, item_id)
                ])

    def test_indented_permission_list_keeps_original_format(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = fixtures(root, existing=True)
            sword = root / "item" / "SWORD.yml"
            sword.write_text(originals["SWORD"].replace(
                '    - "existing.example.permission"',
                '      - "existing.example.permission"'))
            apply(root, preview(root))
            doc = yaml.safe_load(sword.read_text())
            self.assertEqual(doc["FILOPODRIDO"]["base"]["permission"], [
                "existing.example.permission", node("SWORD", "FILOPODRIDO")
            ])

    def test_missing_id_aborts_before_writing_any_file(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = fixtures(root)
            sword = root / "item" / "SWORD.yml"
            sword.write_text(originals["SWORD"].replace("NECROESPADA:", "OTHER_SWORD:"))
            with self.assertRaises(PreflightError):
                preview(root)
            self.assertEqual((root / "item" / "ARMOR.yml").read_text(), originals["ARMOR"])

    def test_permissions_feature_must_be_enabled(self):
        with self.assertRaises(PreflightError):
            check_enabled("permissions:\n  abilities: true\n  items: false\n")

    def test_existing_wrong_stat_shape_refuses_to_edit(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = fixtures(root)
            sword = root / "item" / "SWORD.yml"
            sword.write_text(originals["SWORD"].replace("    material: NETHERITE_SWORD",
                           '    permission: "not-a-list"\n    material: NETHERITE_SWORD', 1))
            with self.assertRaises(PreflightError):
                preview(root)

    def test_crlf_preserved(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            fixtures(root, newline="\r\n")
            apply(root, preview(root))
            for kind in ("SWORD", "ARMOR"):
                raw = (root / "item" / f"{kind}.yml").read_bytes()
                self.assertNotIn(b"\n", raw.replace(b"\r\n", b""))

    def test_check_mode_is_read_only(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            originals = fixtures(root)
            script = ROOT / "tools/apply_slayer_mmoitems_permissions.py"
            outcome = subprocess.run([sys.executable, str(script), "--mmoitems-dir",
                                     str(root), "--check"], capture_output=True, text=True)
            self.assertNotEqual(outcome.returncode, 0)
            self.assertEqual((root / "item" / "ARMOR.yml").read_text(), originals["ARMOR"])
            apply(root, preview(root))
            success = subprocess.run([sys.executable, str(script), "--mmoitems-dir",
                                     str(root), "--check"], capture_output=True, text=True)
            self.assertEqual(success.returncode, 0, success.stderr)


if __name__ == "__main__":
    unittest.main()
