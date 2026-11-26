"""Player-facing Hydev copy has no implementation brands or command hints in GUI items."""
from pathlib import Path
import re
import yaml

root = Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"
implementation_names = re.compile(
    r"\b(?:MMOItems|MythicMobs|ItemsAdder|zMenu|LuckPerms|WorldGuard|"
    r"Vault|Skript|SpectreCore|SpectreSkills|FancyNPCs|FancyHolograms|"
    r"AuraSkills|MythicLib|FastAsyncWorldEdit|Dungeons\+)\b",
    re.IGNORECASE,
)
command = re.compile(r"/[a-z][a-z0-9_-]*(?:\s+[a-z0-9_-]+)?", re.IGNORECASE)


# Audit all visible fields, including the Spanish keys and nested labels
# used in El Mago, Cocina, Armaduras and Trabajos.
VISIBLE_FIELDS = {
    "name", "title", "body", "lore", "label", "description",
    "descripcion", "resumen", "puntos", "intro", "nombre", "titulo",
    "labels", "materiales", "accion", "pista", "indicador",
    "nombre-volver", "nombre-cerrar", "nombre-maximo",
    "descripcion-volver", "descripcion-cerrar"
}
INTERNAL_UI = re.compile(
    r"\b(?:API|UUID|YAML|JSON|Skript|Dialogs|staging|callback|"
    r"plugin|plugins|m[oó]dulo|m[oó]dulos|cooldown|cooldowns|RPG)\b",
    re.IGNORECASE,
)

def visible_strings(value, path=()):
    if isinstance(value, dict):
        for key, child in value.items():
            yield from visible_strings(child, path + (key,))
    elif isinstance(value, list):
        for child in value:
            yield from visible_strings(child, path)
    elif isinstance(value, str) and any(part in VISIBLE_FIELDS for part in path):
        yield value


def check(text: str, where: str, no_commands: bool):
    assert not implementation_names.search(text), f"Implementation brand in {where}: {text}"
    if no_commands:
        # MiniMessage closing tags (e.g. </gold>) are not slash-commands.
        visible = re.sub(r"</?[a-zA-Z_#][^>]*>", "", text)
        assert not command.search(visible), f"Command leaked into menu text in {where}: {text}"


for path in sorted((resources / "ui").glob("*.yml")):
    data = yaml.safe_load(path.read_text(encoding="utf8"))
    for text in visible_strings(data):
        check(text, str(path), no_commands=True)
        assert not INTERNAL_UI.search(text), f"Internal jargon in {path}: {text}"

for path in sorted((resources / "modules").glob("*/mensajes.yml")):
    data = yaml.safe_load(path.read_text(encoding="utf8"))
    def message_values(x):
        if isinstance(x, dict):
            for value in x.values():
                yield from message_values(value)
        elif isinstance(x, str):
            yield x
    for text in message_values(data):
        # Command usage is legitimate in chat/help; plugin names are not.
        # The /spectrecore root is a command, not a plugin credit.
        decommanded = re.sub(r"/spectrecore\b", "[comando]", text, flags=re.I)
        check(decommanded, str(path), no_commands=False)

# Help descriptions are visible to players and must not advertise vendors
# or implementation details. Explicit command syntax in 'uso' is allowed.
routes = yaml.safe_load((resources/"modules/nucleo/comandos.yml").read_text(encoding="utf8"))
def descriptions(data):
    if isinstance(data, dict):
        for key, value in data.items():
            if key == "descripcion" and isinstance(value, str):
                yield value
            else:
                yield from descriptions(value)
for desc in descriptions(routes):
    check(desc, "command help", no_commands=False)
    assert not INTERNAL_UI.search(desc), f"Technical command description: {desc}"

# Integration vendors, persistent-storage flags and performance internals may
# be printed to the console but must never be sent to players (even admins).
core = (root/"src/main/java/dev/hydev/spectrecore/SpectreCorePlugin.java").read_text(encoding="utf8")
assert "Integraciones.solo-consola" in core
assert "Diagnostico.solo-consola" in core
assert core.index("Integraciones.solo-consola") < core.index("integrations.status().forEach")
assert core.index("Diagnostico.solo-consola") < core.index('Settings.text("nucleo", "Diagnostico.estado"')

settings = yaml.safe_load((resources/"modules/nucleo/ajustes.yml").read_text(encoding="utf8"))
presets = settings["toasts"]["presets"]
assert len(presets) == 9
for id, preset in presets.items():
    check(preset["titulo"], f"toast {id} titulo", no_commands=True)
    check(preset["descripcion"], f"toast {id} descripcion", no_commands=True)
    for field in ("icon", "titulo", "descripcion", "sonido", "volumen", "tono", "intervalo-segundos"):
        assert field in preset, (id, field)
print("PASS Hydev presentation: no plugin names in messages or GUI, no commands in menu items, configurable toast presets")

# Hydev uses native bars, contextual text and toasts, never the actionbar.
java_root = root / "src/main/java"
for source in java_root.rglob("*.java"):
    assert not re.search(r"\bsendActionBar\s*\(", source.read_text(encoding="utf8")), f"Actionbar in {source}"
assert "show-fury-actionbar" not in (resources / "modules/equipamiento/config.yml").read_text()
