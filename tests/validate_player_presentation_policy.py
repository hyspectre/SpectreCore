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


def visible_strings(value, key=""):
    if isinstance(value, dict):
        for k, item in value.items():
            if k in ("name", "title", "body", "lore", "label", "description", "intro"):
                yield from visible_strings(item, k)
            else:
                yield from visible_strings(item, key)
    elif isinstance(value, list):
        for entry in value:
            yield from visible_strings(entry, key)
    elif isinstance(value, str) and key in ("name", "title", "body", "lore", "label", "description", "intro"):
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

settings = yaml.safe_load((resources/"modules/nucleo/ajustes.yml").read_text(encoding="utf8"))
presets = settings["toasts"]["presets"]
assert len(presets) == 8
for id, preset in presets.items():
    check(preset["titulo"], f"toast {id} titulo", no_commands=True)
    check(preset["descripcion"], f"toast {id} descripcion", no_commands=True)
    for field in ("icon", "titulo", "descripcion", "sonido", "volumen", "tono", "intervalo-segundos"):
        assert field in preset, (id, field)
print("PASS Hydev presentation: no plugin names in messages or GUI, no commands in menu items, configurable toast presets")
