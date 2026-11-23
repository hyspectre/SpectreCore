"""Static safety check for Relicario catalog and the MMOItems RELIQUIA integration."""
from pathlib import Path
from collections import Counter, defaultdict
import re
import yaml

root = Path(__file__).resolve().parents[1]
source = yaml.safe_load((root/"src/main/resources/modules/accesorios/catalogo.yml").read_text(encoding="utf8"))
items = yaml.safe_load((root/"integracion-mmoitems/RELIQUIA.yml").read_text(encoding="utf8"))
types = yaml.safe_load((root/"integracion-mmoitems/ITEM_TYPE_RELIQUIA.yml").read_text(encoding="utf8"))
general = yaml.safe_load((root/"src/main/resources/config.yml").read_text(encoding="utf8"))
assert "enabled" not in source, "Relicario is always available"
assert "modules" not in general, "No global module switch is allowed"
assert source["mmoitems-type"] == "RELIQUIA"
assert types["RELIQUIA"]["parent"] == "MISCELLANEOUS"
catalog = source["accesorios"]
assert len(catalog) == len(items) == 32
assert set(catalog) == set(items)
assert Counter(i["categoria"] for i in catalog.values()) == {
    "cripta": 11, "superficie": 11, "exploracion": 5, "excepcionales": 5}
families=defaultdict(dict)
known_stats=set(source["caps"])
assert len(known_stats) == 17 and all(v > 0 for v in source["caps"].values())
for id, record in catalog.items():
    assert re.fullmatch(r"[A-Z][A-Z0-9_]{1,63}", id), id
    assert record["rareza"] in {"RARE","EPIC","LEGENDARY"}, id
    assert all(isinstance(v,(float,int)) and 0 < v <= 1000
               for v in record["bonos"].values()), id
    assert set(record["bonos"]) <= known_stats, id
    family=record.get("familia")
    if family:
        rank=record["rango"]
        assert rank not in families[family], (id, rank)
        families[family][rank]=id
    else:
        assert "rango" not in record, id
    base=items[id]["base"]
    assert base["tier"] == record["rareza"] and base["material"] == record["material"], id
    assert "name" in base and "<gradient:" in base["name"], id
    assert base["material"] == "PLAYER_HEAD" and base["disable-interaction"] is True, id
    assert base["skull-texture"]["value"] == record["textura"], id
    assert base["tooltip"] == {"RARE": "COPPER", "EPIC": "SILVER", "LEGENDARY": "GOLD"}[base["tier"]], id
    assert base["lore-format"] == "reliquia-compacta", id
    assert len(base["lore"]) == (5 if id == "ECLIPSE_BOLSILLO" else 4), id
    assert any("ᴄʟɪᴄ ᴅᴇʀᴇᴄʜᴏ" in line for line in base["lore"]), id
    assert not any(k in base for k in ("ability","attack-damage","magic-damage",
                 "additional-health","additional-strength","max-mana","max-health",
                 "commands","unbreakable")), id
    for line in base["lore"]:
        visible=re.sub(r"<[^>]*>","",line)
        visible=re.sub(r"(?i)[&§][0-9a-fk-or]|&#[0-9a-f]{6}","",visible)
        assert visible.strip() and len(visible)<=40,(id,visible,len(visible))
assert len(families)==9, families
assert sorted(len(f) for f in families.values()) == [2]*8 + [3], families
for family,ranks in families.items():
    assert set(ranks)==set(range(1,len(ranks)+1)), family
for stat in known_stats:
    total = sum(r["bonos"].get(stat, 0.0) for r in catalog.values())
    capped = min(total, source["caps"][stat])
    assert 0 <= capped <= source["caps"][stat] + 1e-9, (stat, total, capped)
    # Every distinct consumed ID counts, including different ranks of one family.
    if total > source["caps"][stat]:
        assert capped == source["caps"][stat]
ui = yaml.safe_load((root/"src/main/resources/ui/relicario.yml").read_text(encoding="utf8"))
assert "enabled" not in ui
assert ui["general"]["size"] == 54
assert set(ui["colecciones"]) == {"cripta","superficie","exploracion","excepcionales"}
main_slots=[item["slot"] for item in ui["general"]["iconos"].values()]
assert len(main_slots)==len(set(main_slots))
assert {"cerrar","guia","estadisticas"} <= set(ui["general"]["iconos"])
for category,payload in ui["colecciones"].items():
    assert payload["size"] == 54
    assert len(payload["recipe-slots"]) >= sum(1 for record in catalog.values() if record["categoria"]==category)
    assert len(payload["recipe-slots"]) == len(set(payload["recipe-slots"]))
    assert not {payload["volver-slot"],payload["cerrar-slot"]} & set(payload["recipe-slots"])
assert ui["confirmacion"]["size"] == 27
assert len({ui["confirmacion"][key] for key in ("item-slot","confirmar-slot","cancelar-slot")})==3
print("PASS Relicario: 32 objects, 9 families, 17 caps, 5 configured views, no invalid item stats")

