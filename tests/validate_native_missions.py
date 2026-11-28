"""Native SpectreCore missions GUI contract, using zMenu uploads only as visual reference.

Checks shape, Java action wiring, skull textures and unchanged tier economics without
requiring a Minecraft server. Does not claim to test runtime Paper events.
"""
from pathlib import Path
import base64
import json
import re
import yaml

root = Path(__file__).resolve().parents[1]
resource = root / "src/main/resources/ui/misiones.yml"
raw = resource.read_text(encoding="utf-8")
menu = yaml.safe_load(raw)
assert menu["enabled"] is True
assert set(menu["pages"]) == {"misiones", "revenant", "progresion"}

for name, page in menu["pages"].items():
    assert page["size"] == 54, name
    assert page["title"].startswith("&f⧇ <gold>Misiones" if name == "progresion" else "&f⧇ &6Misiones"), name
    items = page["items"]
    slots = [item["slot"] for item in items.values()]
    assert len(slots) == len(set(slots)), f"Duplicate slots {name}"
    assert all(isinstance(slot, int) and 0 <= slot < 54 for slot in slots)
    assert len(items) <= 40
    for id, item in items.items():
        assert set(item) == {"slot", "texture", "name", "lore"}, (name, id, item.keys())
        assert isinstance(item["name"], str) and item["name"]
        assert isinstance(item["lore"], list) and all(isinstance(x, str) for x in item["lore"])
        assert len(item["lore"]) <= 32
        if item["texture"]:
            payload = base64.b64decode(item["texture"], validate=True)
            skin = json.loads(payload.decode("utf-8"))["textures"]["SKIN"]["url"]
            assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-fA-F0-9]{32,128}", skin), skin

main = menu["pages"]["misiones"]["items"]
revenant = menu["pages"]["revenant"]["items"]
assert len(main) == 13 and len(revenant) == 11
assert len(menu["pages"]["progresion"]["items"]) == 14
visual = menu["progresion-visual"]
assert visual["enabled"] is True
assert visual["segmentos"] == 9
slots = list(range(visual["inicio-slot"], visual["inicio-slot"] + visual["segmentos"]))
assert slots == list(range(27,36))
assert not (set(slots) & {entry["slot"] for entry in menu["pages"]["progresion"]["items"].values()})
assert all(not re.search(r"/(?:spectrecore|mago|cocina|mi)\b", line, re.I)
           for page in menu["pages"].values() for entry in page["items"].values()
           for line in [entry["name"], *entry["lore"]]), "Command visible in a mission menu"
java = (root / "src/main/java/dev/hydev/spectrecore/guide/MissionsMenu.java").read_text(encoding="utf-8")
assert "drawProgressBar(player, inventory, choices)" in java
assert "slayer.slayerProgressSegments(player, segments)" in java
assert "slayer.slayerNextReward(player)" in java
assert main["progresion"]["slot"] == 31
assert main["1"]["slot"] == 13
assert main["telefono"]["slot"] == 50
assert main["cerrar"]["slot"] == 48
assert revenant["cerrar"]["slot"] == 48
assert revenant["cerrar_menu"]["slot"] == 50
assert revenant["cancelar"]["slot"] == 53
assert revenant["reintentar"]["slot"] == 51
for tier in ("1", "2", "3"):
    lore = " ".join(revenant[tier]["lore"])
    for key in ("{vida}", "{dano}", "{xp-slayer}"):
        assert key in lore, (tier, key)
assert "Corazón Podrido x1 &8(50%)" in " ".join(revenant["2"]["lore"])
assert "Corazón Podrido x1 &8(40%)" in " ".join(revenant["3"]["lore"])

config = yaml.safe_load((root / "src/main/resources/modules/slayer/config.yml").read_text(encoding="utf-8"))
assert config["presentacion"]["revenant"] == {
    "tier1": {"vida": 1600, "dano": 32},
    "tier2": {"vida": 3800, "dano": 48},
    "tier3": {"vida": 7800, "dano": 62},
}
assert config["progresion"]["revenant"]["recompensas"]["1"] == ["Receta: Osespina"]
assert config["progresion"]["revenant"]["recompensas"]["5"][0] == "Receta: Mordisco"
assert config["progresion"]["revenant"]["recompensas"]["8"] == ["Receta: Umbría"]
assert config["contratos"]["habilitados"] is True  # new installs: native contracts enabled by default
assert config["acceso-remoto"]["habilitado"] is True
assert config["acceso-remoto"]["precio"] == 500
assert config["acceso-remoto"]["id"] == "TELEFONO1"
assert config["contratos"]["cancelacion"]["revenant"] == 200
for tier, slot, price, required in ((1, 21, 200, 20), (2, 23, 500, 50), (3, 25, 950, 90)):
    item = revenant[str(tier)]
    assert item["slot"] == slot
    assert "{precio}" in " ".join(item["lore"])
    assert "{objetivo}" in " ".join(item["lore"])
    assert config["contratos"]["precios"]["revenant"][f"tier{tier}"] == price
    assert config["objetivos"][f"tier{tier}"] == required

for token in ("player-command-as-op", "consoleCommands:", "click-requirement:", "type: withdraw", "zmenu open"):
    assert token not in raw, token

java = (root / "src/main/java/dev/hydev/spectrecore/guide/MissionsMenu.java").read_text(encoding="utf-8")
slayer = (root / "src/main/java/dev/hydev/spectrecore/systems/SlayerModule.java").read_text(encoding="utf-8")
routes = (root / "src/main/java/dev/hydev/spectrecore/command/CommandRoutes.java").read_text(encoding="utf-8")
assert "InventoryClickEvent" in java and "InventoryDragEvent" in java
assert "event.setCancelled(true)" in java
assert "Bukkit.getScheduler().runTask(plugin" in java
assert "getTopInventory().getHolder() == expected" in java
assert "CustomHeads.fromBase64(encoded)" in java
assert "Bukkit.createInventory(holder, 54" in java
assert 'case "1" -> nextTick(player, holder, () -> open(player, "revenant"))' in java
assert "slayer.contractFromMenu(player" in java and "slayer.cancelFromMenu(player)" in java
assert "slayer.buyPhoneFromMenu(player)" in java
assert "slayer.retrySpawnFromMenu(player)" in java
assert "Bukkit.dispatchCommand(" not in java and "dispatchCommand(" not in java
assert "contractsEconomy.withdraw" in slayer and "validateStart" in slayer
assert "contractPriceLabel" in slayer and "objectiveForTier" in slayer
assert "cancellationPriceLabel" in slayer and "phonePriceLabel" in slayer
assert "resolveLore(player, page, id, line)" in java
assert ".decoration(TextDecoration.ITALIC, false)" in java
assert "TextService.component(source)" in java
assert 'route("misiones","misiones","slayer",Access.PUBLIC' in routes
assert 'route("misiones monarquia","misiones","slayer",Access.PUBLIC' in routes
print("PASS Native Misiones: 3 Paper GUIs, 38 slots, valid textures, progression, no italic and unchanged fees")
