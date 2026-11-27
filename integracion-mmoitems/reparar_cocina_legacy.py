#!/usr/bin/env python3
"""Repair legacy Hydev food potion-effect fields without changing item types or IDs."""
import argparse
import copy
from datetime import datetime, timezone
import os
from pathlib import Path
import shutil
import tempfile
import yaml

FAMILIES = "ALBA BRASA CARMESI ESCUDERO ESPECTRAL ESPELEOLOGO GUARDIAN LEYENDA MARATON MAREAS MINERO SALTADOR SOMBRA VELOCIDAD VIGOR".split()
KNOWN = {f"HC_{family}_{quality}" for family in FAMILIES
         for quality in ("NORMAL", "EXQUISITO", "PASADO")}


class UniqueLoader(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        self.flatten_mapping(node)
        result = {}
        for name, value in node.value:
            key = self.construct_object(name, deep=deep)
            if not isinstance(key, (str, int, float, bool)) or key in result:
                raise ValueError(f"Clave YAML duplicada o no admitida: {key!r}")
            result[key] = self.construct_object(value, deep=deep)
        return result


def mapping(node):
    if not isinstance(node, yaml.MappingNode):
        raise ValueError("Se esperaba una sección YAML")
    return {key.value: (key, value) for key, value in node.value}


def repair(text):
    data = yaml.load(text, Loader=UniqueLoader)
    if not isinstance(data, dict):
        raise ValueError("Se esperaba un catálogo de objetos")
    expected = {key: copy.deepcopy(value) for key, value in data.items()}
    edits = []
    for item, (_, node) in mapping(yaml.compose(text, Loader=yaml.SafeLoader)).items():
        if item not in KNOWN:
            continue
        base = mapping(node).get("base")
        if base is None:
            raise ValueError(f"{item}: no tiene base")
        effects = mapping(base[1]).get("effects")
        if effects is None:
            continue
        for effect, (_, definition) in mapping(effects[1]).items():
            values = mapping(definition)
            if "level" not in values:
                continue
            if "amplifier" in values:
                raise ValueError(f"{item}/{effect}: contiene level y amplifier; revisar manualmente")
            key = values["level"][0]
            # Edit the key only. Do not reserialize the catalogue, its comments or values.
            edits.append((key.start_mark.index, key.end_mark.index, item, effect))
            target = expected[item]["base"]["effects"][effect]
            target["amplifier"] = target.pop("level")
    result = text
    for begin, end, _, _ in sorted(edits, reverse=True):
        result = result[:begin] + "amplifier" + result[end:]
    if yaml.load(result, Loader=UniqueLoader) != expected:
        raise ValueError("La reparación alteraría otros datos; no se escribirá el archivo")
    return result, sorted({item for _, _, item, _ in edits}), len(edits)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archivo", type=Path, help="Ruta al consumable.yml instalado")
    parser.add_argument("--aplicar", action="store_true", help="Escribir cambios con respaldo")
    args = parser.parse_args()
    path = args.archivo.expanduser()
    if path.is_symlink() or not path.is_file() or path.suffix.lower() != ".yml":
        parser.error("Selecciona un YAML existente (no un enlace simbólico)")
    original = path.read_bytes()
    fixed, items, changes = repair(original.decode("utf-8"))
    print(f"{len(items)} plantillas HC, {changes} campos level -> amplifier")
    for item in items:
        print(f"  {item}")
    if not changes or not args.aplicar:
        print("No se ha modificado el archivo.")
        return
    if path.read_bytes() != original:
        raise ValueError("El archivo cambió durante la revisión; no se escribirá")
    backup_dir = path.parent.parent / ".hydev-backups" / (
        "cocina-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
    backup_dir.mkdir(parents=True, exist_ok=False)
    shutil.copy2(path, backup_dir / path.name)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".hydev-cocina-", suffix=".tmp",
                                     delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(fixed.encode("utf-8"))
    try:
        shutil.copymode(path, temporary)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"Reparación completada. Respaldo: {backup_dir}")


if __name__ == "__main__":
    main()
