"""Mithril consistency contract: single MMOItems ID, tool recipes, safe order economics."""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
res = root / "src/main/resources"
mining = yaml.safe_load((res/"modules/tesoros/mineria.yml").read_text(encoding="utf8"))
jobs = yaml.safe_load((res/"modules/trabajos/config.yml").read_text(encoding="utf8"))
gui = yaml.safe_load((res/"ui/abastecimiento.yml").read_text(encoding="utf8"))
material = yaml.safe_load((root/"integracion-mmoitems/MATERIAL_MITRILO.yml").read_text(encoding="utf8"))
tools = yaml.safe_load((root/"integracion-mmoitems/TOOL_MITRILO.yml").read_text(encoding="utf8"))
station = yaml.safe_load((root/"integracion-mmoitems/RECETAS_FORJA_MITRILO.yml").read_text(encoding="utf8"))
resource = mining["mitrilo"]
order = jobs["encargos"]["mitrilo"]

assert (resource["tipo"], resource["id"]) == ("MATERIAL","MITRILO")
assert (order["tipo"],order["id"]) == ("MATERIAL","MITRILO")
assert resource["cantidad-minima"] == 1 and resource["cantidad-maxima"] == 2
assert resource["rango-minimo"] == 2
assert resource["probabilidades"] == {"tier2": 1.0, "tier3": 2.0}
assert resource["herramientas"]["tipo"] == "TOOL"
assert resource["herramientas"]["bonificaciones"] == {
    "PICO_MITRILO": 1.25, "PICO_MITRILO_TEMPLADO": 1.60
}
assert order == {"tipo":"MATERIAL", "id":"MITRILO", "cantidad":4, "recompensa":180, "rango-minimo":2}
assert set(material) == {"MITRILO", "AMATISTA_RESONANTE", "CARGA_RESONANTE", "LLAVE_RESONANTE"} and material["MITRILO"]["base"]["material"] == "PRISMARINE_CRYSTALS"
assert set(tools) == {"PICO_MITRILO", "PICO_MITRILO_TEMPLADO", "FAROL_MITRILO", "BARRENA_MITRILO", "PICO_AMATISTA"}
assert tools["PICO_MITRILO"]["base"]["material"] == "DIAMOND_PICKAXE"
assert tools["PICO_MITRILO_TEMPLADO"]["base"]["material"] == "NETHERITE_PICKAXE"
recipes = station["recipes"]
assert set(recipes) == {"pico-mitrilo", "pico-mitrilo-templado", "carga-resonante", "llave-resonante", "linterna-mitrilo", "barrena-mitrilo", "pico-amatista"}
assert recipes["pico-mitrilo"]["output"] == "mmoitems{type=TOOL,id=PICO_MITRILO}"
assert recipes["pico-mitrilo-templado"]["output"] == "mmoitems{type=TOOL,id=PICO_MITRILO_TEMPLADO}"
for name, recipe in recipes.items():
    assert any("mmoitem{type=MATERIAL,id=" in i for i in recipe["ingredients"])
    assert recipe["crafting-time"] == 0 if name == "pico-mitrilo-templado" else recipe["crafting-time"] > 0
assert recipes["linterna-mitrilo"]["output"] == "mmoitems{type=TOOL,id=FAROL_MITRILO}"
assert recipes["barrena-mitrilo"]["output"] == "mmoitems{type=TOOL,id=BARRENA_MITRILO}"
assert recipes["carga-resonante"]["output"] == "mmoitems{type=MATERIAL,id=CARGA_RESONANTE}"
device_config = resource["herramientas"]  # Keep original passive Pico bonuses.
tool_config = mining["herramientas-mitrilo"]
assert tool_config["resonancia"] == {"maxima": 100, "por-mineral-natural": 4}
assert tool_config["carga"]["recupera"] == 30
assert tool_config["pico"]["max-bloques-adicionales"] == 3
assert "brujula" not in tool_config and "farol" not in tool_config
assert tool_config["linterna"] == {"vision-segundos": 60, "radio": 10, "resaltado-segundos": 3,
                                  "recarga-segundos": 140, "max-menas": 24, "max-pulsos": 8}
assert "Linterna de Mitrilo" in tools["FAROL_MITRILO"]["base"]["name"]
lantern = (res.parent/"java/dev/hydev/spectrecore/mining/MithrilLantern.java").read_text()
for guard in ("setGlowing(true)", "setVisibleByDefault(false)", "player.showEntity(plugin, display)",
              "setPersistent(false)", "world.isChunkLoaded", "placed.isMarked", "regions.canBreak",
              'config("resaltado-segundos") * 20L', 'config("recarga-segundos") * 1000L',
              "player.getPersistentDataContainer()", "BlockDisplay::remove"):
    assert guard in lantern, guard
assert "setType(" not in lantern and "runTaskTimer" not in lantern
catalog = yaml.safe_load((res/"modules/accesorios/catalogo.yml").read_text())
assert catalog["accesorios"]["BOBINA_COBRE"]["bonos"] == {"lantern_radius": 4}
assert "signal_seconds" not in catalog["caps"]
assert tool_config["barrena"]["pasos"] == 8 and tool_config["barrena"]["intervalo-ticks"] == 5
assert all("ORE" not in item for item in tool_config["barrena"]["bloques"])
assert (res.parent/"java/dev/hydev/spectrecore/mining/MithrilToolModule.java").is_file()
assert (res.parent/"java/dev/hydev/spectrecore/mining/MithrilRegionAccess.java").is_file()
assert "mitrilo" in gui["items"] and "pedido-mitrilo" in gui["items"]
assert "{recompensa}" in " ".join(gui["items"]["pedido-mitrilo"]["lore"])
mining_code = (res.parent/"java/dev/hydev/spectrecore/mining/MiningModule.java").read_text(encoding="utf8")
order_code = (res.parent/"java/dev/hydev/spectrecore/systems/SupplyOrders.java").read_text(encoding="utf8")
assert "private boolean mithril(" in mining_code
assert "treasureCooldown.awarded" in mining_code and "mmo.build(type, id" in mining_code
assert "journal.begin(" in order_code and "setStorageContents(next)" in order_code
assert order_code.index("journal.begin(") < order_code.index("setStorageContents(next)") < order_code.index("economy.deposit(")
assert "journal.finish(token)" in order_code and "SupplyOrderRules.allocation" in order_code
assert "economy.deposit(player, coins, false)" in order_code
assert "dispatchCommand" not in order_code
print("PASS Mithril: valid reward tiers, durable order transaction, mining picks and Forge recipe fragment")
