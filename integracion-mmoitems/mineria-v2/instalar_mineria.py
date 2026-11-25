#!/usr/bin/env python3
"""Hydev Miner integration installer: edits only whitelisted MMOItems IDs.

Built-in Python only, no PyYAML requirement on the Minecraft machine.
Dry run by default. --aplicar creates backups, preserves unrelated lines,
and atomically updates the original MMOItems type and station YAML files.
"""
from __future__ import annotations
import argparse
import datetime
import os
from pathlib import Path
import re
import shutil
import sys
import tempfile

ROOT = Path(__file__).resolve().parent
TYPES = {
    "MATERIAL": ROOT / "MATERIAL_MINERIA_NUEVOS.yml",
    "GEM_STONE": ROOT / "GEM_STONE_MINERIA_NUEVOS.yml",
    "TOOL": ROOT / "TOOL_MINERIA_NUEVOS.yml",
}
RECIPE_SOURCE = ROOT / "RECETAS_FORJA_MINERIA.yml"
LOST_SOURCE = ROOT / "RECETA_PICO_PERDIDO_REEMPLAZO.yml"
TOP_ITEM = re.compile(r"^([A-Z][A-Z0-9_]*):[ \t]*(?:#.*)?$")
TOP_RECIPE = re.compile(r"^  ([a-z0-9-]+):[ \t]*(?:#.*)?$")
ROOT_KEY = re.compile(r"^[^ \t#][^:\n]*:[ \t]*(?:#.*)?$")

def lines_and_sections(text: str, matcher: re.Pattern, stop_at_root=False):
    lines = text.splitlines(keepends=True)
    spans = {}
    positions = [(i, m.group(1)) for i, line in enumerate(lines)
                 if (m := matcher.fullmatch(line.rstrip("\r\n")))]
    if len({name for _, name in positions}) != len(positions):
        raise ValueError("IDs duplicados en archivo de origen o destino")
    for n, (start, key) in enumerate(positions):
        end = positions[n + 1][0] if n + 1 < len(positions) else len(lines)
        if stop_at_root:
            for i in range(start + 1, end):
                if ROOT_KEY.fullmatch(lines[i].rstrip("\r\n")):
                    end = i
                    break
        spans[key] = (start, end)
    return lines, spans

def merge_items(original: str, incoming: str, label: str):
    old_lines, old_spans = lines_and_sections(original, TOP_ITEM)
    source_lines, new_spans = lines_and_sections(incoming, TOP_ITEM)
    if not new_spans:
        raise ValueError(f"{label}: no se encontraron entradas MMOItems")
    updates = {}
    additions = []
    for key, (start, end) in new_spans.items():
        block = "".join(source_lines[start:end]).rstrip() + "\n"
        if key in old_spans:
            a, b = old_spans[key]
            previous = "".join(old_lines[a:b]).rstrip() + "\n"
            if previous != block:
                updates[key] = block
        else:
            additions.append(block)
    merged = old_lines[:]
    for key, (start, end) in sorted(
            ((k, old_spans[k]) for k in updates), key=lambda x: x[1][0],
            reverse=True):
        merged[start:end] = [updates[key]]
    result = "".join(merged).rstrip() + "\n"
    if additions:
        result += "\n".join(additions)
    return result, sorted(updates), sorted(new_spans.keys() - old_spans.keys())

def patch_permission(text: str, name: str, permission: str) -> tuple[str, bool]:
    lines, spans = lines_and_sections(text, TOP_ITEM)
    if name not in spans:
        print(f"ADVERTENCIA: objeto original {name} no encontrado: verificarlo en el servidor.")
        return text, False
    a, b = spans[name]
    fragment = "".join(lines[a:b])
    entries = fragment.splitlines(keepends=True)
    start = next((i for i, line in enumerate(entries)
                 if re.match(r"^  base:\s*$", line)), None)
    if start is None:
        raise ValueError(f"{name}: entrada sin base; no se modifica")
    existing = next((i for i in range(start + 1, len(entries))
                     if re.match(r"^    item-permissions:", entries[i])), None)
    if existing is not None:
        line = entries[existing].rstrip("\r\n")
        header, _, comment = line.partition("#")
        value = header.split(":", 1)[1].strip()
        if value:
            if not re.fullmatch(r"\[\s*(?:[\w.'\x22*-]+(?:\s*,\s*[\w.'\x22*-]+)*)?\s*\]", value):
                raise ValueError(f"{name}: formato de item-permissions no reconocido")
            permissions = [entry.strip().strip("'\x22") for entry in value[1:-1].split(",")]
            if permission in permissions:
                return text, False
            separator = ", " if value[1:-1].strip() else ""
            entries[existing] = "    item-permissions: " + value[:-1] + separator + permission + "]" + (" #" + comment if comment else "") + "\n"
        else:
            index = existing + 1
            indent = "    "
            for entry in entries[index:]:
                if not entry.strip() or entry.lstrip().startswith("#"):
                    continue
                match = re.match(r"^( {4,})-\s+(.*)", entry)
                if not match:
                    break
                indent = match[1]
                if match[2].split("#", 1)[0].strip().strip("'\x22") == permission:
                    return text, False
            entries.insert(index, f"{indent}- {permission}\n")
    else:
        entries.insert(start + 1, f"    item-permissions:\n    - {permission}\n")
    lines[a:b] = ["".join(entries)]
    return "".join(lines), True

def recipe_area(text: str):
    rows = text.splitlines(keepends=True)
    roots = [i for i, line in enumerate(rows)
             if ROOT_KEY.fullmatch(line.rstrip("\r\n"))]
    begin = next((i for i in roots if rows[i].strip() == "recipes:"), None)
    if begin is None:
        raise ValueError("La Forja no contiene sección recipes:")
    end = next((i for i in roots if i > begin), len(rows))
    return rows, begin, end

def read_recipes(source: Path):
    rows, start, end = recipe_area(source.read_text(encoding="utf-8"))
    local = "".join(rows[start + 1:end])
    content, spans = lines_and_sections(local, TOP_RECIPE)
    if not spans:
        raise ValueError(f"Sin recetas: {source.name}")
    return {name: "".join(content[a:b]).rstrip() + "\n"
            for name, (a,b) in spans.items()}

def merge_recipes(original: str, additions: dict, lost: dict):
    rows, start, end = recipe_area(original)
    old_lines = rows[start + 1:end]
    old_text = "".join(old_lines)
    lines, spans = lines_and_sections(old_text, TOP_RECIPE, stop_at_root=True)
    to_add = []
    to_replace = {}
    for key, block in {**additions, **lost}.items():
        if key in spans:
            a, b = spans[key]
            current = "".join(lines[a:b]).rstrip() + "\n"
            if current != block:
                to_replace[key] = block
        else:
            to_add.append(block)
    result = lines[:]
    for key, (a,b) in sorted(((key, spans[key]) for key in to_replace),
                              key=lambda x: x[1][0], reverse=True):
        result[a:b] = [to_replace[key]]
    result_text = "".join(result).rstrip() + "\n"
    if to_add:
        result_text += "\n".join(to_add)
    rows[start + 1:end] = [result_text]
    return "".join(rows), sorted(to_replace), sorted((set(additions) | set(lost)) - set(spans))

def detect_forge(plugin: Path, requested: str | None):
    if requested:
        target = Path(requested).expanduser().resolve()
        if not target.is_file():
            raise ValueError(f"Forja inexistente: {target}")
        return target
    location = plugin / "crafting-stations"
    candidates = []
    if location.is_dir():
        for path in location.glob("*.yml"):
            if re.search(r"(?m)^  picoperdido:[ \t]*(?:#.*)?$", path.read_text(encoding="utf-8")):
                candidates.append(path)
    if len(candidates) != 1:
        raise ValueError(f"No se identificó una Forja única ({len(candidates)} coincidencias); "
                         "indica --forja <archivo.yml>.")
    return candidates[0]

def install(plugin: Path, forge: Path, apply: bool):
    changes: dict[Path, str] = {}
    for typ, src in TYPES.items():
        dest = plugin / "item" / f"{typ}.yml"
        if not dest.is_file():
            raise ValueError(f"Falta el tipo original {dest}; no se creará otro tipo.")
        old = dest.read_text(encoding="utf-8")
        new, replaced, added = merge_items(old, src.read_text(encoding="utf-8"), typ)
        for item, permission in (
                ([("COMPACTADOR", "hydev.mineria.hechizados")]
                 if typ == "MATERIAL" else
                 [("TALADRO","hydev.mineria.paladio"),
                  ("PICO_GEMAS","hydev.mineria.resonancia")]
                 if typ == "TOOL" else [])):
            new, _ = patch_permission(new, item, permission)
        changes[dest] = new
        print(f"{typ}: nuevos {len(added)}, actualizados {len(replaced)}; "
              f"objetos originales preservados")
    old_forja = forge.read_text(encoding="utf-8")
    new_forja, replaced, added = merge_recipes(
        old_forja, read_recipes(RECIPE_SOURCE), read_recipes(LOST_SOURCE))
    changes[forge] = new_forja
    print(f"Forja: nuevas {len(added)}, actualizadas {len(replaced)}")
    modified = {path: new for path, new in changes.items()
                if path.read_text(encoding="utf-8") != new}
    if not modified:
        print("Todos los archivos ya están actualizados.")
        return
    for path in modified:
        print("  " + ("ACTUALIZAR" if apply else "SIMULAR") + " " + str(path))
    if not apply:
        print("Simulación completa. Vuelve a ejecutar con --aplicar para realizar copias de seguridad y guardar.")
        return
    name = datetime.datetime.now().strftime("mineria_%Y%m%d_%H%M%S")
    backup = plugin / ".hydev-backups" / name
    backup.mkdir(parents=True, exist_ok=False)
    for path in modified:
        # Preserve relative paths, including custom forja path when external.
        if path.is_relative_to(plugin):
            relative = path.relative_to(plugin)
        else:
            relative = Path("forja_externa") / path.name
        destination = backup / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, destination)
    completed = []
    try:
        for path, data in modified.items():
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", newline="\n",
                                              dir=path.parent, prefix=".hydev-", suffix=".tmp",
                                              delete=False) as tmp:
                tmp.write(data)
                temporary = Path(tmp.name)
            try:
                os.replace(temporary, path)
                completed.append(path)
            finally:
                temporary.unlink(missing_ok=True)
    except OSError:
        for path in completed:
            relative = path.relative_to(plugin) if path.is_relative_to(plugin) \
                else Path("forja_externa") / path.name
            shutil.copy2(backup / relative, path)
        raise
    print(f"Instalación completada. Respaldo íntegro: {backup}")
    print("Reinicia MMOItems y verifica las recetas y permisos con un jugador de prueba.")

def main():
    parser=argparse.ArgumentParser(description="Integración segura de Minería Hydev en MMOItems")
    parser.add_argument("plugin",help="Ruta a plugins/MMOItems del servidor")
    parser.add_argument("--forja",help="Ruta al YAML de la Forja original (si no contiene picoperdido)")
    parser.add_argument("--aplicar",action="store_true",help="Aplicar cambios con respaldo")
    args=parser.parse_args()
    plugin=Path(args.plugin).expanduser().resolve()
    if not (plugin / "item").is_dir():
        parser.error("No existe plugins/MMOItems/item")
    try:
        install(plugin,detect_forge(plugin,args.forja),args.aplicar)
    except (ValueError,OSError,UnicodeError) as error:
        print(f"Error: {error}",file=sys.stderr)
        sys.exit(2)

if __name__=="__main__":
    main()
