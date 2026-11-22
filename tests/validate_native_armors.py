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
    assert payload["title"].startswith("<white>⇜ <gold>Armaduras"), page
    ids = payload["items"]
    slots = [item["slot"] for item in ids.values()]
    assert len(slots) == len(set(slots)), page
    assert all(type(slot) is int and 0 <= slot < 54 for slot in slots), page
    assert (sets | {"cerrar"}) <= set(ids) if page == "armaduras" else {"bonos","habilidad","volver","cerrar"} <= set(ids), page
    for id, item in ids.items():
        assert set(item) == {"slot", "texture", "name", "lore"}, (page,id)
        assert len(item["name"]) <= 256 and len(item["lore"]) <= 30
        assert "" in item["name"] or id != "habilidad", (page,id)
        assert "⇜" in item["name"] or id not in {"volver","cerrar"}, (page,id)
        assert "⊱" not in item["name"]+" ".join(item["lore"]), (page,id)
        visible=re.sub(r"<[^>]*>", "", item["name"]+" ".join(item["lore"]))
        assert not re.search(r"/(?:spectrecore|mago|[a-z]+\w*)\b", visible, re.I), (page,id)
        assert not re.search(r"(?:MMOItems|SpectreCore|zMenu|MythicMobs|LuckPerms)", item["name"]+" ".join(item["lore"]), re.I), (page,id)
        encoded = item["texture"]
        if encoded:
            skin = json.loads(base64.b64decode(encoded, validate=True))["textures"]["SKIN"]["url"]
            assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-fA-F0-9]{32,128}",skin),skin
    if page != "armaduras":
        assert {"piezas","arma"} <= set(ids), page
        assert "{bonos}" in ids["bonos"]["lore"], page
        assert "{habilidad}" in ids["habilidad"]["lore"], page
        assert len(ids["piezas"]["lore"]) == 6, page
        assert any("ᴄʟɪᴄ ᴘᴀʀᴀ ᴠᴏʟᴠᴇʀ" in x for x in ids["volver"]["lore"]), page
    else:
        assert {"guia","rasgos","habilidades","vinculos"} <= set(ids), page
        assert all(any("ᴄʟɪᴄ ᴘᴀʀᴀ ᴇxᴘʟᴏʀᴀʀ" in x for x in ids[key]["lore"])
                   for key in sets), page

indicator = menu["indicador"]
assert "{equipadas}" in indicator["piezas"]
assert {"completo","parcial","incompleto","desactivado"} <= set(indicator)
java = (root / "src/main/java/dev/hydev/spectrecore/guide/ArmorGuide.java").read_text()
plugin = (root / "src/main/java/dev/hydev/spectrecore/SpectreCorePlugin.java").read_text()
mago = (root / "src/main/java/dev/hydev/spectrecore/guide/MagoMenu.java").read_text()
assert "implements Listener" in java and "InventoryClickEvent" in java and "InventoryDragEvent" in java
assert "event.setCancelled(true)" in java and "CustomHeads.fromBase64(encoded)" in java
assert "Bukkit.createInventory(holder, 54" in java
assert "Bukkit.getScheduler().runTask(plugin" in java
assert "bonuses(selected, sets)" in java and "ability(selected)" in java
assert "plugin.sets().countArmor(player" in java
assert 'replace("{habilidad}", named.ability)' in java
assert "compactLines" in java and "juramento-fuerza" in java
assert "bonusNames().get(key)" in java
assert "Settings.module(\"equipamiento/config.yml\")" in java
assert "dispatchCommand(" not in java and "showDialog(" not in java
assert "armorGuide.enable();" in plugin and "armorGuide.disable();" in plugin
assert "plugin.armors()" in mago
# The menu uses the five actual helmet textures from its integration source.
import collections
armor_items_source = yaml.safe_load((root/"integracion-mmoitems/ARMOR_HYDEV_20_versalitas_v3.yml").read_text(encoding="utf8"))
for family,page in {
    "VERDANA_ARMOR":"botania","NECROTICO":"necrotica",
    "MINOTAURO_ARMOR":"asterion","CABALLERO_ARMOR":"ultimo-juramento",
    "DRACONIDO_ARMOR":"sangre-primer-dragon"
}.items():
    helmet=armor_items_source[f"{family}_1"]["base"]["skull-texture"]["value"]
    assert menu["pages"]["armaduras"]["items"][page]["texture"] == helmet, page
    assert menu["pages"][page]["items"]["informacion"]["texture"] == helmet, page
assert len({menu["pages"]["armaduras"]["items"][key]["texture"] for key in sets}) == 5
print("PASS Native Armaduras: 6 GUI pages, 5 actual skulls, concise lore, configured skills and safe navigation")

# Integration deliverables: compact 20-piece lore and five complete set definitions.
integration=root/"integracion-mmoitems"
armor_items=yaml.safe_load((integration/"ARMOR_HYDEV_20_versalitas_v3.yml").read_text(encoding="utf8"))
item_sets=yaml.safe_load((integration/"ITEM_SETS_HYDEV_5_v1.yml").read_text(encoding="utf8"))
assert len(armor_items)==20 and len(item_sets)==5
expected_families={
    "VERDANA_ARMOR":"HYDEV_RAIGAMBRE",
    "NECROTICO":"NECROTICO",
    "MINOTAURO_ARMOR":"HYDEV_LABERINTO",
    "CABALLERO_ARMOR":"HYDEV_VOTO",
    "DRACONIDO_ARMOR":"HYDEV_IGNEA"
}
assert set(item_sets)==set(expected_families.values())
for prefix, set_id in expected_families.items():
    for index in range(1,5):
        key=f"{prefix}_{index}"
        base=armor_items[key]["base"]
        assert base["set"]==set_id,(key,base["set"])
        lore=base["lore"]
        assert 7<=len(lore)<=15,(key,len(lore))
        assert any("" in line and "ʜᴀʙɪʟɪᴅᴀᴅ" in line for line in lore),key
        assert any("ᴀʀᴍᴀᴅᴜʀᴀs" in line for line in lore),key
        assert not any("⊱" in line for line in lore),key
        assert not any(re.search(r"MMOItems|SpectreCore|MythicMobs|/[a-z]+",re.sub(r"</?[a-zA-Z_#][^>]*>","",line),re.I) for line in lore),key
        for line in lore:
            visible=re.sub(r"<[^>]*>","",line)
            visible=re.sub(r"(?i)[&§][0-9a-fk-or]|&#[0-9a-fA-F]{6}","",visible)
            assert len(visible)<=30,(key,visible)
assert item_sets["HYDEV_RAIGAMBRE"]["bonuses"]["5"]=={"potion-regeneration":1}
assert item_sets["NECROTICO"]["bonuses"]=={"2":{"max-health":4},"4":{"damage-reduction":5}}
assert item_sets["HYDEV_VOTO"]["bonuses"]=={"2":{"critical-strike-chance":8}}
assert item_sets["HYDEV_LABERINTO"]["bonuses"]=={"5":{"critical-strike-chance":5}}
assert item_sets["HYDEV_IGNEA"]["bonuses"]=={}
assert all(set(v)=={"name","bonuses","lore-tag"} and v["lore-tag"]==[] for v in item_sets.values())
print("PASS integration: 20 compact armor lores and 5 item sets, no double-counted native passives")
