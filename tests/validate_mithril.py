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
assert set(material) == {"MITRILO", "LINGOTE_MITRILO", "CRISTAL_MITRILO"} and material["MITRILO"]["base"]["material"] == "PRISMARINE_CRYSTALS"
assert set(tools) == {"PICO_MITRILO", "PICO_MITRILO_TEMPLADO", "BRUJULA_GEOLOGICA", "FAROL_MITRILO", "BARRENA_MITRILO"}
assert tools["PICO_MITRILO"]["base"]["material"] == "DIAMOND_PICKAXE"
assert tools["PICO_MITRILO_TEMPLADO"]["base"]["material"] == "NETHERITE_PICKAXE"
recipes = station["recipes"]
assert set(recipes) == {"pico-mitrilo", "pico-mitrilo-templado", "lingote-mitrilo", "cristal-mitrilo", "brujula-geologica", "farol-mitrilo", "barrena-mitrilo"}
assert recipes["pico-mitrilo"]["output"] == "mmoitems{type=TOOL,id=PICO_MITRILO}"
assert recipes["pico-mitrilo-templado"]["output"] == "mmoitems{type=TOOL,id=PICO_MITRILO_TEMPLADO}"
for recipe in recipes.values():
    assert any("mmoitem{type=MATERIAL,id=" in i for i in recipe["ingredients"])
    assert recipe["crafting-time"] > 0
assert recipes["brujula-geologica"]["output"] == "mmoitems{type=TOOL,id=BRUJULA_GEOLOGICA}"
assert recipes["farol-mitrilo"]["output"] == "mmoitems{type=TOOL,id=FAROL_MITRILO}"
assert recipes["barrena-mitrilo"]["output"] == "mmoitems{type=TOOL,id=BARRENA_MITRILO}"
assert recipes["cristal-mitrilo"]["output"] == "mmoitems{type=MATERIAL,id=CRISTAL_MITRILO}"
device_config = resource["herramientas"]  # Keep original passive Pico bonuses.
tool_config = mining["herramientas-mitrilo"]
assert tool_config["resonancia"] == {"maxima": 100, "por-mineral-natural": 4}
assert tool_config["cristal"]["recupera"] == 50
assert tool_config["pico"]["max-bloques-adicionales"] == 3
assert tool_config["brujula"]["radio-horizontal"] == 16 and tool_config["brujula"]["radio-vertical"] == 12
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
