"""Static zMenu migration contract: no OP buttons or separate payments.
This checks YAML and references, not live zMenu/Paper compatibility.
"""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
folder = root / "integracion-zmenu"
menus = {}
for name in ("misiones", "slayer_zombie"):
    file = folder / f"{name}.yml"
    data = yaml.safe_load(file.read_text(encoding="utf-8"))
    assert data["size"] == 54, name
    assert data["name"].startswith("&8⧇ &6Misiones &8•"), name
    items = data["items"]
    assert len(items) == len({v["slot"] for v in items.values()}), f"Duplicate slot: {name}"
    assert all(0 <= v["slot"] < 54 for v in items.values()), f"Out of range slot: {name}"
    assert all("url" in v["item"] and len(v["item"]["url"]) > 40 for v in items.values()), name
    literal = file.read_text(encoding="utf-8")
    for forbidden in ("player-command-as-op", "type: withdraw", "eco take", "mmoitems give"):
        assert forbidden not in literal, f"Unsafe action in {name}: {forbidden}"
    menus[name] = data
main = menus["misiones"]["items"]
zombie = menus["slayer_zombie"]["items"]
assert main[1]["type"] == "INVENTORY" and main[1]["inventory"] == "slayer_zombie"
assert zombie["cerrar"]["type"] == "INVENTORY" and zombie["cerrar"]["inventory"] == "misiones"
assert main["cerrar"]["slot"] == 48 and zombie["cerrar"]["slot"] == 48
assert zombie["cerrar_menu"]["slot"] == 50
assert main["telefono"]["actions"] == [
    {"type": "player_command", "commands": ["spectrecore slayer comprar telefono"]}
]
assert "500" in " ".join(main["telefono"]["item"]["lore"])
for tier, slot, price, required in ((1,21,200,20),(2,23,500,50),(3,25,950,90)):
    button = zombie[tier]
    assert button["slot"] == slot and button["actions"] == [
        {"type": "player_command", "commands": [f"spectrecore slayer contratar revenant {tier}"]}
    ]
    lore = " ".join(button["item"]["lore"])
    assert str(price) in lore and str(required) in lore, f"Tier {tier} changed price or kills"
assert zombie["cancelar"]["actions"] == [
    {"type": "player_command", "commands": ["spectrecore slayer abandonar revenant"]}
]
assert "200" in " ".join(zombie["cancelar"]["item"]["lore"])
config = yaml.safe_load((root/"src/main/resources/modules/slayer/config.yml").read_text(encoding="utf-8"))
assert config["contratos"]["habilitados"] is False
assert config["acceso-remoto"]["habilitado"] is False
assert config["acceso-remoto"]["precio"] == 500
assert config["acceso-remoto"]["id"] == "TELEFONO1"
assert config["contratos"]["precios"]["revenant"] == {"tier1":200,"tier2":500,"tier3":950}
assert config["contratos"]["cancelacion"]["revenant"] == 200
print("PASS zMenu: 2 coherent 54-slot menus, safe commands, valid prices, disabled staging gates")
