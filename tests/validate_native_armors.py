"""Read-only native armor GUI: five sets and navigable 54-slot pages."""
from pathlib import Path
import base64
import json
import re
import yaml

root = Path(__file__).resolve().parents[1]
resource = root / "src/main/resources/ui/armaduras.yml"
raw = resource.read_text(encoding="utf-8")
menu = yaml.safe_load(raw)
sets = {"botania", "necrotica", "asterion", "ultimo-juramento", "sangre-primer-dragon"}
assert menu["enabled"] is True
assert set(menu["pages"]) == sets | {"armaduras"}
for page, payload in menu["pages"].items():
    assert payload["size"] == 54, page
    assert payload["title"].startswith("<dark_gray>⧇ <gold>Armaduras"), page
    ids = payload["items"]
    slots = [item["slot"] for item in ids.values()]
    assert len(slots) == len(set(slots)), page
    assert all(type(slot) is int and 0 <= slot < 54 for slot in slots), page
    assert (sets | {"cerrar"}) <= set(ids) if page == "armaduras" else {"bonos","habilidad","volver","cerrar"} <= set(ids), page
    for id, item in ids.items():
        assert set(item) == {"slot", "texture", "name", "lore"}, (page,id)
        assert len(item["name"]) <= 256 and len(item["lore"]) <= 30
        assert not re.search(r"/(?:spectrecore|mago|[a-z]+\w*)\b", item["name"]+" ".join(item["lore"]), re.I), (page,id)
        assert not re.search(r"(?:MMOItems|SpectreCore|zMenu|MythicMobs|LuckPerms)", item["name"]+" ".join(item["lore"]), re.I), (page,id)
        encoded = item["texture"]
        if encoded:
            skin = json.loads(base64.b64decode(encoded, validate=True))["textures"]["SKIN"]["url"]
            assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-fA-F0-9]{32,128}",skin),skin
    if page != "armaduras":
        assert "{bonos}" in ids["bonos"]["lore"], page
        assert "{habilidad}" in ids["habilidad"]["lore"], page

java = (root / "src/main/java/dev/hydev/spectrecore/guide/ArmorGuide.java").read_text()
plugin = (root / "src/main/java/dev/hydev/spectrecore/SpectreCorePlugin.java").read_text()
mago = (root / "src/main/java/dev/hydev/spectrecore/guide/MagoMenu.java").read_text()
assert "implements Listener" in java and "InventoryClickEvent" in java and "InventoryDragEvent" in java
assert "event.setCancelled(true)" in java and "CustomHeads.fromBase64(encoded)" in java
assert "Bukkit.createInventory(holder, 54" in java
assert "Bukkit.getScheduler().runTask(plugin" in java
assert "bonuses(selected, sets)" in java and "ability(selected)" in java
assert "dispatchCommand(" not in java and "showDialog(" not in java
assert "armorGuide.enable();" in plugin and "armorGuide.disable();" in plugin
assert "plugin.armors()" in mago
print("PASS Native Armaduras: 6 GUI pages, 5 sets, configured lore and safe navigation")
