"""Supply board contract: existing treasures, no fabricated recipes or rewards."""
from pathlib import Path
import re
import yaml

root = Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"
layout = yaml.safe_load((resources / "ui/abastecimiento.yml").read_text(encoding="utf-8"))
jobs = yaml.safe_load((resources / "ui/trabajos.yml").read_text(encoding="utf-8"))
mining = yaml.safe_load((resources / "modules/tesoros/mineria.yml").read_text(encoding="utf-8"))
combat = yaml.safe_load((resources / "modules/tesoros/combate.yml").read_text(encoding="utf-8"))
farming = yaml.safe_load((resources / "modules/tesoros/agricultura.yml").read_text(encoding="utf-8"))
source = (root / "src/main/java/dev/hydev/spectrecore/guide/SupplyGuide.java").read_text(encoding="utf-8")
routes = (root / "src/main/java/dev/hydev/spectrecore/command/CommandRoutes.java").read_text(encoding="utf-8")
module = (root / "src/main/java/dev/hydev/spectrecore/systems/JobsModule.java").read_text(encoding="utf-8")

assert layout["size"] == 54 and isinstance(layout["title"], str)
assert set(layout["items"]) == {
    "mineria", "mitrilo", "combate", "agricultura-1", "agricultura-2",
    "agricultura-3", "pedido-mitrilo", "volver", "cerrar", "guia"
}
assert set(layout["estados"]) == {
    "sin-hallazgos", "sin-acceso", "disponible", "recurso-no-disponible",
    "consulta-no-disponible", "sin-contrato", "faltan-materiales", "entrega-lista", "error",
}
slots = [item["slot"] for item in layout["items"].values()]
assert len(slots) == len(set(slots)) and all(type(slot) is int and 0 <= slot < 54 for slot in slots)
for item in layout["items"].values():
    assert set(item) == {"slot", "material", "nombre", "lore"}
    assert 1 <= len(item["nombre"]) <= 256
    assert isinstance(item["lore"], list) and len(item["lore"]) <= 16
    assert all(isinstance(line, str) and len(line) <= 256 for line in item["lore"])

assert "abastecimiento" not in jobs["pages"]["trabajos"]["items"]
assert "tesoros.id" in source and "tesoros.tipo" in source
assert "botin.id" in source and "botin.tipo" in source
assert "botin.tier" in source and "probabilidades." in source
assert mining["tesoros"]["id"] == "PALADIO" and mining["mitrilo"]["id"] == "MITRILO"
assert combat["botin"]["id"] == "ALMA_1"
assert farming["botin"] == {f"tier{i}": f"AGRICULTURA_T{i}" for i in range(1, 4)}
assert "inventoryCounts(Player player)" in source
assert 'jobs.fulfillMithrilOrder(player)' in source and 'pedido-mitrilo' in source
assert "getStorageContents()" in source and "mmo.id(item)" in source and "mmo.type(item)" in source
assert "hasPermission(\"tesoros.\"" in source
assert "Bukkit.getScheduler().runTask(plugin" in source
assert "event.setCancelled(true)" in source
assert "jobs.openOverview(player)" in source
assert 'candidate.set("pages.trabajos.items.abastecimiento", null);' in (
    root / "src/main/java/dev/hydev/spectrecore/guide/JobsMenu.java"
).read_text(encoding="utf-8")
assert "supplyGuide" not in module or "supplies.enable()" in module
assert "supplies.enable()" in module and "supplies.disable()" in module
assert 'route("trabajos abastecimiento","trabajo","trabajos",Access.PUBLIC' in routes
# Read-only: no crafting, item grants, privileged command execution or item debits.
for forbidden in (
    ".withdraw(", ".deposit(", "addItem(", "setAmount(", "dispatchCommand(",
    ".getInventory().remove", "dropItem(", "world.dropItem("
):
    assert forbidden not in source, forbidden
forbidden_technical = re.compile(r"MMOItems|MythicMobs|Vault|LuckPerms|Skript", re.I)
for section in layout["items"].values():
    assert not forbidden_technical.search(section["nombre"] + " ".join(section["lore"]))
print("PASS Supply Board: existing treasures, Mithril and supply orders, 10 distinct safe slots")
