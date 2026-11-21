"""Cooking expansion: 15 playable ordered recipes, 45 compatible outputs and native read-only UI."""
from pathlib import Path
import re
import yaml
import json
import base64

root=Path(__file__).resolve().parents[1]
resources=root/"src/main/resources"
recipes=yaml.safe_load((resources/"modules/cocina/recetas.yml").read_text())["recetas"]
outputs=yaml.safe_load((root/"integracion-mmoitems/CONSUMABLE_Cocina.yml").read_text())
ui=yaml.safe_load((resources/"ui/recetario.yml").read_text())
assert len(recipes)==15 and len(outputs)==45
assert all(isinstance(k,str) for k in recipes)
assert set(("guardian","alba","carmesi","espectral")).issubset(recipes)

first=[]
all_ids=set()
first_names=set()
tiers=set()
for id, r in recipes.items():
    assert re.fullmatch(r"[a-z_]+",id)
    assert len(r["ingredientes"])==3,(id,r["ingredientes"])
    assert all(re.fullmatch(r"[A-Z_]+",mat) for mat in r["ingredientes"])
    assert r["ingredientes"][0] not in first, ("Impossible second starter",id)
    first.append(r["ingredientes"][0])
    assert 1<=r["nivel-ingredientes"]<=5
    tiers.add(r["nivel-ingredientes"])
    assert r["nombre"] not in first_names
    first_names.add(r["nombre"])
    assert r["uso"] and r["pista"] and r["efecto-descripcion"]
    assert re.fullmatch(r"[A-Z_]+",r["icono"])
    times=[r[k] for k in ("revolver-desde","revolver-hasta","listo-desde","listo-hasta","quemado")]
    assert 0<=times[0]<times[1]<=times[2]<times[3]<times[4],(id,times)
    ids=[r["resultado-"+q] for q in ("normal","exquisito","pasado")]
    assert len(set(ids))==3
    for key in ids:
        assert key not in all_ids,key
        all_ids.add(key)
        assert key in outputs,key
        item=outputs[key]["base"]
        assert item["vanilla-eating"] is True and item["material"] in {"MUSHROOM_STEW","RABBIT_STEW","BEETROOT_SOUP"}
        assert item["max-stack-size"]==1
        assert item["item-cooldown"]>=3
        assert item["effects"] and all(
            1<=e["level"]<=2 and 6<=e["duration"]<=600 for e in item["effects"].values())
    for effect in outputs[ids[0]]["base"]["effects"]:
        normal=outputs[ids[0]]["base"]["effects"][effect]
        exquisite=outputs[ids[1]]["base"]["effects"][effect]
        overcooked=outputs[ids[2]]["base"]["effects"][effect]
        assert overcooked["duration"]<normal["duration"]<exquisite["duration"],(id,effect)
        assert normal["level"]==exquisite["level"]==overcooked["level"],id

assert all_ids==set(outputs)
assert tiers=={1,2,3,4,5},tiers
assert recipes["guardian"]["resultado-normal"]=="HC_GUARDIAN_NORMAL"
assert recipes["alba"]["resultado-normal"]=="HC_ALBA_NORMAL"
assert recipes["carmesi"]["resultado-normal"]=="HC_CARMESI_NORMAL"
assert recipes["espectral"]["resultado-normal"]=="HC_ESPECTRAL_NORMAL"
assert "Prisa minera I" in recipes["minero"]["efecto-descripcion"]
assert "HASTE" in outputs["HC_MINERO_NORMAL"]["base"]["effects"]
assert "SPEED" in outputs["HC_VELOCIDAD_NORMAL"]["base"]["effects"]
assert outputs["HC_VELOCIDAD_NORMAL"]["base"]["effects"]["SPEED"]["level"]==2
assert "ABSORPTION" in outputs["HC_LEYENDA_NORMAL"]["base"]["effects"]

assert ui["enabled"] is True
assert ui["catalogo"]["espacios"]==54
assert ui["catalogo"]["ocultar-desconocidas"] is False
assert "pendiente" in ui["catalogo"]
assert ui["detalle"]["espacios"]==54
slots=ui["catalogo"]["posiciones"]
assert len(slots)==15 and len(set(slots))==15
reserved=[ui["catalogo"][k] for k in ("resumen-slot","anterior-slot","siguiente-slot","cerrar-slot")]
assert len(set(slots+reserved))==19 and all(0<=x<54 for x in slots+reserved)
detail=ui["detalle"]
other=[detail[k] for k in ("cabecera-slot","preparacion-slot","resultado-slot","volver-slot","cerrar-slot")]
assert len(set(other+detail["ingrediente-slots"]))==8
for key,item in ui["iconos"].items():
    if key=="recetas": continue
    if item:
        skin=json.loads(base64.b64decode(item,validate=True))["textures"]["SKIN"]["url"]
        assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-f0-9]{32,128}",skin),skin
assert all(x in ui["materiales"] for r in recipes.values() for x in r["ingredientes"])
for where in ("catalogo.conocido","catalogo.pendiente","catalogo.desconocido","catalogo.resumen","detalle.cabecera",
              "detalle.ingrediente","detalle.preparacion","detalle.resultado","detalle.desconocido"):
    obj=ui
    for key in where.split("."):obj=obj[key]
    assert isinstance(obj["nombre"],str) and isinstance(obj["lore"],list),where
    assert not re.search(r"/(?:mago|spectrecore)\b|MMOItems|MythicMobs",str(obj),re.I),where

java=(root/"src/main/java/dev/hydev/spectrecore/cooking/CookingModule.java").read_text()
assert "RecipeBookHolder implements InventoryHolder" in java
assert "Bukkit.createInventory(holder, 54" in java
assert "CustomHeads.fromBase64(texture)" in java
assert "event.setCancelled(true)" in java
assert "Bukkit.getScheduler().runTask(plugin" in java
assert "openRecipeDetail(player," in java
assert "claimJournal.begin" in java and "resolveMMOItem(rewardId)" in java
assert "firstIngredients.add(ingredients.getFirst())" in java
assert "loadRecipeBookLayout()" in java
assert "discoveries.getOrDefault(player.getUniqueId(), Set.of())" in java
print("PASS native cooking: 15 distinct starters, 45 tiered quality outputs, safe discovery book and ingredient costs")
