"""Native Miner workshop: preserve MMOItems IDs, 64:1, UX, transaction safety."""
from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[1]
res=root/"src/main/resources"
config=yaml.safe_load((res/"modules/trabajos/config.yml").read_text(encoding="utf-8"))
layout=yaml.safe_load((res/"ui/minero.yml").read_text(encoding="utf-8"))
contracts=yaml.safe_load((res/"ui/trabajos.yml").read_text(encoding="utf-8"))
rules=config["taller-minero"]
assert rules["nivel-minimo"]==3
assert rules["compactar-unidades"]==64
assert 1<=rules["max-operaciones"]<=16
expected={"cobre":("COPPER_INGOT","COBRE",12),"hierro":("IRON_INGOT","HIERRO",30),
          "oro":("GOLD_INGOT","ORO",55),"lapis":("LAPIS_LAZULI","LAPIS",40),
          "redstone":("REDSTONE","REDSTONE",35),"diamante":("DIAMOND","DIAMANTE",135)}
assert set(rules["recetas"])==set(expected)
for name,(origin,id,payout) in expected.items():
    item=rules["recetas"][name]
    assert item["origen"]==origin and item["resultado-id"]==id and item["pago"]==payout
assert layout["size"]==54
assert set(layout["minerales"])==set(expected)
assert {"compactar","pulverizar","vista-previa","una","cuatro","maximo","confirmar","cerrar"}<=set(layout["items"])
all_items={**layout["items"],**layout["minerales"]}
slots=[v["slot"] for v in all_items.values()]
assert len(set(slots))==len(slots)
assert all(0<=slot<54 and isinstance(data["lore"],list) for slot,data in zip(slots,all_items.values()))
assert "taller" in contracts["pages"]["trabajos"]["items"]
java=root/"src/main/java/dev/hydev/spectrecore"
server=(java/"systems/MinerWorkshop.java").read_text(encoding="utf-8")
gui=(java/"guide/MinerWorkshopMenu.java").read_text(encoding="utf-8")
jobs=(java/"systems/JobsModule.java").read_text(encoding="utf-8")
aura=(java/"integration/AuraSkillsBridge.java").read_text(encoding="utf-8")
legacy=(java/"activities/LegacyActivities.java").read_text(encoding="utf-8")
routes=(java/"command/CommandRoutes.java").read_text(encoding="utf-8")
assert "getSkillLevel(Skills.MINING)" in aura
assert 'route("minero","trabajo","trabajos",Access.PUBLIC' in routes
assert 'route("trabajos taller","trabajo","trabajos",Access.PUBLIC' in routes
assert "MinerWorkshopMenu(plugin, minerWorkshop)" in jobs
assert "volver" not in layout["items"] and '"volver"' not in gui
assert "jobs.openOverview" not in gui
assert "MinerWorkshopRules.selected" in server and "MinerWorkshopRules.payout" in server
assert "journal.begin(" in server and "journal.finish(token)" in server
assert "journal.blocked(\"job:\"" in server
assert "setStorageContents(projected)" in server
assert 'mmo.build("MATERIAL", id, 1)' in server
assert 'mmo.type(stack)' in server and 'mmo.id(stack)' in server
assert "economy.deposit(player, money, false)" in server
assert "player.getInventory().getStorageContents()" in server
assert "InventoryClickEvent" in gui and "InventoryDragEvent" in gui
assert "event.setCancelled(true)" in gui and "getRawSlot()" in gui
assert "Bukkit.getScheduler().runTask(plugin" in gui
assert "runTask" not in server and "Bukkit.dispatchCommand" not in server
legacy_branch=legacy.split("else if (MONEY.containsKey(name))",1)[1].split("else if (UNLOCKS.containsKey(name))",1)[0]
assert "economy.deposit" not in legacy_branch and "taller" in legacy_branch
print("PASS Miner workshop: no station, 6 real MMOItems IDs, native GUI, level-3 gate and durable payouts")
