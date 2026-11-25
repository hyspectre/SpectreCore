"""Integration smoke tests: real rewards, behavior, safe MMOItems installer."""
import importlib.util
import tempfile
from pathlib import Path
import yaml

root=Path(__file__).resolve().parents[1]
pack=root/"integracion-mmoitems/mineria-v2"
source=pack/"instalar_mineria.py"
spec=importlib.util.spec_from_file_location("hydev_mineria_installer",source)
module=importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
perms=yaml.safe_load((root/"integracion-auraskills/rewards/mining.yml").read_text("utf-8"))
assert perms["patterns"][0]["stat"]=="toughness"
assert perms["patterns"][1]["stat"]=="luck"
grants={level:entry[0]["permission"] for level,entry in perms["levels"].items()}
expected={1:"inicial",3:"hechizados",6:"paladio",12:"duendes",15:"resonancia",
          18:"mitrilo",22:"arcana",25:"ingenieria"}
assert grants=={n:"hydev.mineria."+s for n,s in expected.items()}
assert all(p[0]["menu_message"].strip() and p[0]["chat_message"].strip()
           for p in perms["levels"].values())
assert 30 not in perms["levels"] and "tesoromineria1" not in str(perms)
mobs=yaml.safe_load((root/"integracion-mythicmobs/HYDEV_MINERIA.yml").read_text("utf-8"))
skills=yaml.safe_load((root/"integracion-mythicmobs/HYDEV_MINERIA_SKILLS.yml").read_text("utf-8"))
assert len(mobs)==2 and len(skills)==7
for mob in mobs.values():
    assert mob["Options"]["NoAI"] is False
    assert "meleeattack" in mob["AIGoalSelectors"]
    assert "players" in mob["AITargetSelectors"]
    for line in mob["Skills"]:
        identifier=line.split("{s=")[1].split("}")[0]
        assert identifier in skills,identifier

with tempfile.TemporaryDirectory() as tmp:
    plugin=Path(tmp)/"plugins"/"MMOItems"
    (plugin/"item").mkdir(parents=True)
    (plugin/"crafting-stations").mkdir()
    (plugin/"item/MATERIAL.yml").write_text("""# Materiales de Hydev
PALADIO:
  base:
    name: 'Paladio original'
    material: IRON_INGOT
COMPACTADOR:
  base:
    name: 'Compactador original'
    material: IRON_NUGGET
OTHER:
  base:
    material: STONE
""",encoding="utf-8")
    (plugin/"item/TOOL.yml").write_text("""TALADRO:
  base:
    material: STONE_PICKAXE
    name: 'Taladro original'
PICO_GEMAS:
  base:
    material: DIAMOND_PICKAXE
    name: 'Pico Perdido original'
""",encoding="utf-8")
    (plugin/"item/GEM_STONE.yml").write_text("""GEMA_ANTIGUA:
  base:
    material: AMETHYST_SHARD
""",encoding="utf-8")
    station=plugin/"crafting-stations/forja.yml"
    station.write_text("""recipes:
  picoperdido:
    output: 'mmoitems{type=TOOL,id=PICO_GEMAS}'
    crafting-time: 10
    ingredients:
    - 'vanilla{type=IRON_INGOT,amount=1}'
  otro-original:
    output: 'vanilla{type=IRON_PICKAXE}'
gui-layout:
  name: 'Forja Original'
  slots: 54
""",encoding="utf-8")
    originals={p:p.read_bytes() for p in [*sorted((plugin/"item").glob("*.yml")),station]}
    assert module.detect_forge(plugin,None)==station
    module.install(plugin,station,False)
    assert all(p.read_bytes()==old for p,old in originals.items()),"dry run altered files"
    module.install(plugin,station,True)
    assert (plugin/".hydev-backups").exists()
    for kind in ("MATERIAL","TOOL","GEM_STONE"):
        loaded=yaml.safe_load((plugin/"item"/f"{kind}.yml").read_text("utf-8"))
        assert loaded
        source=yaml.safe_load((pack/f"{kind}_MINERIA_NUEVOS.yml").read_text("utf-8"))
        assert set(source)<=set(loaded)
    material=yaml.safe_load((plugin/"item/MATERIAL.yml").read_text("utf-8"))
    tool=yaml.safe_load((plugin/"item/TOOL.yml").read_text("utf-8"))
    assert material["PALADIO"]["base"]["name"]=="Paladio original"
    assert material["COMPACTADOR"]["base"]["name"]=="Compactador original"
    assert "hydev.mineria.hechizados" in material["COMPACTADOR"]["base"]["item-permissions"]
    assert tool["TALADRO"]["base"]["name"]=="Taladro original"
    assert "hydev.mineria.paladio" in tool["TALADRO"]["base"]["item-permissions"]
    assert tool["PICO_GEMAS"]["base"]["name"]=="Pico Perdido original"
    assert "hydev.mineria.resonancia" in tool["PICO_GEMAS"]["base"]["item-permissions"]
    definitions=yaml.safe_load(station.read_text("utf-8"))
    assert definitions["gui-layout"]["name"]=="Forja Original"
    assert definitions["recipes"]["otro-original"]["output"]=="vanilla{type=IRON_PICKAXE}"
    assert "FRAGMENTO_PICO_PERDIDO" in str(definitions["recipes"]["picoperdido"])
    assert "hydev.mineria.resonancia" in str(definitions["recipes"]["picoperdido"])
    assert "hydev.mineria.ingenieria" in str(definitions["recipes"]["brujula-geologica"])
    assert "hydev.mineria.duendes" in str(definitions["recipes"]["taladro-reforzado"])
    assert "hydev.mineria.resonancia" in str(definitions["recipes"]["taladro-calibrado"])
    snapshot={p:p.read_bytes() for p in originals}
    module.install(plugin,station,True)
    assert all(p.read_bytes()==data for p,data in snapshot.items()),"second install changed YAML"
print("PASS Hydev mining integration: live reward nodes, 7 MM skills and idempotent backed-up MMOItems install")

for permissions in ("    item-permissions: [otra] # permiso original", "    item-permissions: # original\n      - otra"):
    sample = "TALADRO:\n  base:\n" + permissions + "\n"
    changed, _ = module.patch_permission(sample, "TALADRO", "hydev.mineria.paladio")
    assert changed.count("item-permissions:") == 1
    assert set(yaml.safe_load(changed)["TALADRO"]["base"]["item-permissions"]) == {"otra", "hydev.mineria.paladio"}
    assert module.patch_permission(changed, "TALADRO", "hydev.mineria.paladio")[0] == changed
