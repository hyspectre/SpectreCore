"""Amethyst pickaxe acceptance: genuine MMOItems gems and protected temporary nodes."""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"
def load(path):
    return yaml.safe_load(path.read_text(encoding="utf-8"))

mining = load(resources / "modules/tesoros/mineria.yml")
messages = load(resources / "modules/tesoros/mensajes.yml")
tools = load(root / "integracion-mmoitems/TOOL_MITRILO.yml")
recipes = load(root / "integracion-mmoitems/RECETAS_FORJA_MITRILO.yml")["recipes"]
gems = load(root / "integracion-mmoitems/GEM_STONE_AMATISTA.yml")
rule = mining["herramientas-mitrilo"]["pico-amatista"]
assert rule == {
    "activacion": 75, "recarga-segundos": 120, "duracion-segundos": 30,
    "bloques": 5, "minimo-bloques": 3, "max-sesiones": 8,
    "golpes": 3, "intervalo-golpe-ms": 300,
    "calidad": {"basica":75, "intermedia":22, "superior":3},
    "gemas": {"basica":"GEMA_AMATISTA_BASICA", "intermedia":"GEMA_AMATISTA_INTERMEDIA",
              "superior":"GEMA_AMATISTA_SUPERIOR"}
}
assert sum(rule["calidad"].values()) == 100
assert set(gems) == set(rule["gemas"].values())
assert all("base" in value and "material" in value["base"] for value in gems.values())
assert "PICO_AMATISTA" in tools
assert tools["PICO_AMATISTA"]["base"]["material"] == "NETHERITE_PICKAXE"
craft = recipes["pico-amatista"]
assert craft["output"] == "mmoitems{type=TOOL,id=PICO_AMATISTA}"
assert craft["ingredients"] == [
    "mmoitem{type=MATERIAL,id=MITRILO,amount=30}",
    "mmoitem{type=MATERIAL,id=PALADIO,amount=12}",
    "vanilla{type=AMETHYST_SHARD,amount=16}",
]
assert "AmethystField" in messages and "resonante" in messages["AmethystField"]
assert "campo-ametista" in messages["MithrilTools"]
source_dir = root/"src/main/java/dev/hydev/spectrecore/mining"
source = (source_dir/"AmethystResonanceField.java").read_text(encoding="utf-8")
ability = (source_dir/"MithrilToolModule.java").read_text(encoding="utf-8")
assert "PrePlayerAttackEntityEvent" in source
assert "instanceof Interaction target" in source
assert "BlockDisplay" in source and "setInteractionWidth" in source
assert "setPersistent(false)" in source and "cleanupOrphans" in source
assert "regions.canBuild(player" in source and "owner.equals(player.getUniqueId())" in source
assert "toolSerial.equals(devices.serial(held))" in source
assert "node.claimed = true" in source and source.index("node.claimed = true") < source.index("addItem(reward)")
assert "hitboxes.remove(target.getUniqueId())" in source
assert "getItemInMainHand()" in source and 'items.build("MATERIAL", "AMATISTA_RESONANTE", 1)' in source
assert "setType(" not in source and "breakNaturally(" not in source
assert "data.identify(main)" in ability and "amethystField.summon(" in ability
assert "MithrilDeviceData.AMETHYST" in ability
assert "amethystField.disable()" in ability
print("PASS Amethyst: resonant amethyst, exact recipe, one-time owner-only nodes and no world block mutation")
