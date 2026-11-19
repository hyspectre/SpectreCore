#!/usr/bin/env python3
"""Apply the 16 Slayer equipment use-permissions to a *copy* of actual MMOItems YAML.

No character recoding, reformatting, item replacement or network access. Preview by
default. The explicit --apply flag creates backups before atomic writes.

Usage: python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems
       python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems --apply
       python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems --check
"""
from __future__ import annotations

import argparse
import datetime as dt
import os
import re
import shutil
import stat
import sys
import tempfile
from pathlib import Path

# MMOItems type/ID and the Slayer level needed to gain the matching LuckPerms node.
ITEMS = (
    ("SWORD", "FILOPODRIDO", 1),
    ("SWORD", "COLMILLO_PUTREFACTO", 5),
    ("SWORD", "SABLE_NECROTICO", 8),
    ("SWORD", "NECROESPADA", 11),
    ("ARMOR", "ZOMBIEARMOR_1", 2), ("ARMOR", "ZOMBIEARMOR_2", 2),
    ("ARMOR", "ZOMBIEARMOR_3", 3), ("ARMOR", "ZOMBIEARMOR_4", 3),
    ("ARMOR", "RENACIDO_1", 6), ("ARMOR", "RENACIDO_2", 6),
    ("ARMOR", "RENACIDO_3", 7), ("ARMOR", "RENACIDO_4", 7),
    ("ARMOR", "NECROTICO_1", 9), ("ARMOR", "NECROTICO_2", 9),
    ("ARMOR", "NECROTICO_3", 10), ("ARMOR", "NECROTICO_4", 10),
)


def node(kind: str, item_id: str) -> str:
    return f"hydev.slayer.item.{kind.lower()}.{item_id.lower()}"


class PreflightError(Exception):
    pass


def _find_unique(lines: list[str], regex: str, label: str) -> int:
    matches = [i for i, line in enumerate(lines) if re.fullmatch(regex, line.rstrip("\r\n"))]
    if len(matches) != 1:
        raise PreflightError(f"{label}: expected exactly one declaration; found {len(matches)}")
    return matches[0]


def patch_type(source: str, kind: str, expected: tuple[tuple[str, str, int], ...]) -> tuple[str, int]:
    """Insert only MMOItems 'base.permission' entries, preserving all other bytes."""
    newline = "\r\n" if "\r\n" in source else "\n"
    trailing_newline = source.endswith(("\n", "\r"))
    # Splitlines without normalization so other fields, comments, custom lore remain untouched.
    lines = source.splitlines(keepends=True)
    modifications = 0
    for _kind, item_id, _level in expected:
        if _kind != kind:
            continue
        position = _find_unique(lines, rf"{re.escape(item_id)}:\s*(?:#.*)?", f"{kind}/{item_id}")
        # Scan to the next top-level item. The target must be a root-level MMOItems entry.
        end = next((j for j in range(position + 1, len(lines))
                    if re.match(r"^[^\s#][^:\r\n]*:", lines[j])), len(lines))
        base_hits = [j for j in range(position + 1, end)
                     if re.fullmatch(r"  base:\s*(?:#.*)?", lines[j].rstrip("\r\n"))]
        if len(base_hits) != 1:
            raise PreflightError(f"{kind}/{item_id}: missing or ambiguous 'base:' section")
        base = base_hits[0]
        # The base body ends when another 2-space key begins.
        base_end = next((j for j in range(base + 1, end)
                         if re.match(r"^  [^\s#-][^\r\n]*:", lines[j])), end)
        perm = [j for j in range(base + 1, base_end)
                if re.match(r"^    permission\s*:", lines[j])]
        if len(perm) > 1:
            raise PreflightError(f"{kind}/{item_id}: duplicate permission keys")
        if perm and not re.fullmatch(r"    permission:\s*(?:#.*)?", lines[perm[0]].rstrip("\r\n")):
            raise PreflightError(f"{kind}/{item_id}: permission must be a YAML list; refusing scalar or inline replacement")
        target = node(kind, item_id)
        if perm:
            j = perm[0]
            k = j + 1
            entries = []
            bullet_prefix = None
            while k < base_end:
                text = lines[k].rstrip("\r\n")
                if not text.strip() or text.lstrip().startswith("#"):
                    k += 1
                    continue
                bullet = re.match(r"^( {4,6})-\s+(.*?)\s*(?:#.*)?$", text)
                if not bullet:
                    break
                if bullet_prefix is None:
                    bullet_prefix = bullet.group(1)
                elif bullet_prefix != bullet.group(1):
                    raise PreflightError(f"{kind}/{item_id}: inconsistent permission list indentation")
                entries.append(bullet.group(2).strip().strip("'\""))
                k += 1
            if target in entries:
                continue
            if k == j + 1 or not entries:
                raise PreflightError(f"{kind}/{item_id}: existing permission value is not a YAML list")
            # Keep the original sequence indentation instead of reformatting the item.
            lines.insert(k, f'{bullet_prefix}- "{target}"{newline}')
        else:
            lines[base + 1:base + 1] = [
                f"    permission:{newline}", f'    - "{target}"{newline}'
            ]
        modifications += 1
    result = "".join(lines)
    if not trailing_newline and result.endswith(newline):
        result = result[:-len(newline)]
    return result, modifications


def check_enabled(config: str) -> None:
    # Confirm MMOItems itself will check permission requirements. Fail closed.
    lines = config.splitlines()
    hits = [i for i, line in enumerate(lines)
            if re.fullmatch(r"permissions:\s*(?:#.*)?", line)]
    if len(hits) != 1:
        raise PreflightError("config.yml: missing or ambiguous 'permissions:' section")
    i = hits[0] + 1
    while i < len(lines) and (lines[i].startswith((" ", "\t")) or not lines[i].strip()):
        if re.fullmatch(r"\s+items:\s*true\s*(?:#.*)?", lines[i], re.IGNORECASE):
            return
        i += 1
    raise PreflightError("MMOItems config.yml must have 'permissions: items: true'")


def preview(root: Path) -> dict[Path, tuple[str, str, int]]:
    if root.is_symlink() or not root.is_dir():
        raise PreflightError(f"MMOItems directory missing or unsafe symlink: {root}")
    config = root / "config.yml"
    if not config.is_file():
        raise PreflightError(f"Missing MMOItems config: {config}")
    check_enabled(config.read_text(encoding="utf-8"))
    output: dict[Path, tuple[str, str, int]] = {}
    for kind in ("SWORD", "ARMOR"):
        path = root / "item" / f"{kind}.yml"
        if path.is_symlink() or not path.is_file():
            raise PreflightError(f"Missing or unsafe MMOItems item file: {path}")
        before = path.read_bytes().decode("utf-8")
        after, changed = patch_type(before, kind, ITEMS)
        output[path] = before, after, changed
    return output


def apply(root: Path, changes: dict[Path, tuple[str, str, int]]) -> Path | None:
    candidates = [(path, old, new) for path, (old, new, _) in changes.items() if old != new]
    if not candidates:
        return None
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup = root / "backups" / f"spectrecore-slayer-{stamp}"
    if backup.exists():
        raise PreflightError(f"Backup folder already exists: {backup}")
    backup.mkdir(parents=True)
    for path, _, _ in candidates:
        shutil.copy2(path, backup / path.name)
    completed: list[Path] = []
    try:
        for path, _, new in candidates:
            fd, tmp = tempfile.mkstemp(prefix=".spectrecore-", dir=path.parent)
            try:
                with os.fdopen(fd, "wb") as out:
                    out.write(new.encode("utf-8"))
                    out.flush()
                    os.fsync(out.fileno())
                os.chmod(tmp, stat.S_IMODE(path.stat().st_mode))
                os.replace(tmp, path)
            finally:
                if os.path.exists(tmp):
                    os.unlink(tmp)
            completed.append(path)
    except OSError:
        for changed in completed:
            shutil.copy2(backup / changed.name, changed)
        raise
    return backup


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mmoitems-dir", required=True, type=Path,
                        help="Server's plugins/MMOItems directory (not the plugin JAR)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--apply", action="store_true", help="Back up and apply edits")
    mode.add_argument("--check", action="store_true", help="Require all 16 nodes already installed")
    args = parser.parse_args(argv)
    try:
        results = preview(args.mmoitems_dir)
        missing = sum(changed for _, (_, _, changed) in results.items())
        for path, (_, _, changed) in results.items():
            print(f"{path.name}: {changed} new permission requirements")
        if args.check:
            if missing:
                raise PreflightError(f"{missing} Slayer permissions are not yet installed")
            print("PASS: all 16 Slayer equipment permission requirements installed")
            return 0
        if args.apply:
            backup = apply(args.mmoitems_dir, results)
            print(f"Applied. Backups: {backup}" if backup else "Already applied. Nothing changed.")
        else:
            print(f"Preview only: {missing} requirements would be added. Use --apply to commit.")
        return 0
    except (OSError, UnicodeError, PreflightError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
