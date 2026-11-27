"""Static native Jobs GUI acceptance: visuals, genuine heads, config-derived payouts, safe Java handlers."""
from pathlib import Path
import base64
import json
import re
import yaml

root = Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"
gui = yaml.safe_load((resources / "ui/trabajos.yml").read_text(encoding="utf-8"))
jobs = yaml.safe_load((resources / "modules/trabajos/config.yml").read_text(encoding="utf-8"))
treasures = yaml.safe_load((resources / "modules/tesoros/config.yml").read_text(encoding="utf-8"))
expected = {
    "minero": ((10, 1, 4), (7, 4, 6), (4, 8, 12)),
    "granjero": ((8, 1, 3), (5, 3, 6), (3, 6, 10)),
    "cazador": ((12, 2, 6), (8, 6, 12), (5, 12, 24)),
}
assert "enabled" not in gui
assert set(gui["pages"]) == {"trabajos", *expected}
assert "habilitados" not in jobs["contratos"]
assert jobs["contratos"]["precios"] == {"novato": 50, "experimentado": 1500, "profesional": 3500}
assert len(gui["material-names"]) >= 30
for page, payload in gui["pages"].items():
    assert payload["size"] == 54 and payload["title"].startswith("<dark_gray>⧇ <gold>Trabajos")
    items = payload["items"]
    assert len(items) == (7 if page == "trabajos" else 6)
    slots = [entry["slot"] for entry in items.values()]
    assert len(set(slots)) == len(slots) and all(0 <= slot < 54 for slot in slots)
    for entry in items.values():
        assert set(entry) == {"slot", "texture", "name", "lore"}
        assert isinstance(entry["name"], str)
        assert len(entry["lore"]) <= 28
        parsed = json.loads(base64.b64decode(entry["texture"], validate=True))
        skin = parsed["textures"]["SKIN"]["url"]
        assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-fA-F0-9]{32,128}", skin), skin

assert [gui["pages"]["trabajos"]["items"][key]["slot"] for key in expected] == [20, 22, 24]
assert gui["pages"]["trabajos"]["items"]["abastecimiento"]["slot"] == 31
assert gui["pages"]["trabajos"]["items"]["taller"]["slot"] == 33
for job, rows in expected.items():
    page = gui["pages"][job]["items"]
    assert [page[key]["slot"] for key in ("novato", "experimentado", "profesional")] == [19, 22, 25]
    assert page["info"]["slot"] == 49 and page["cerrar"]["slot"] == 48 and page["cerrar_menu"]["slot"] == 50
    domain = {"minero": "trabajos-mineria", "granjero": "agricultura", "cazador": "combate"}[job]
    for level, tier, (seconds, min_amount, max_amount) in zip(
        ("novato", "experimentado", "profesional"), range(1, 4), rows
    ):
        assert jobs["cooldowns"][job][level] == seconds
        assert jobs[job][level]["dinero"] == {"minimo": min_amount, "maximo": max_amount}
        assert treasures["clasificacion"][domain][f"tier{tier}"]
        lore = " ".join(page[level]["lore"])
        if job == "minero":
            assert "{requisito-mineria}" in lore
        for token in ("{actividades}", "{minimo}", "{maximo}", "{espera}", "{precio}", "{estado}"):
            assert token in lore, (job, level, token)

java = (resources.parent / "java/dev/hydev/spectrecore/guide/JobsMenu.java").read_text(encoding="utf-8")
module = (resources.parent / "java/dev/hydev/spectrecore/systems/JobsModule.java").read_text(encoding="utf-8")
bridge = (resources.parent / "java/dev/hydev/spectrecore/integration/LuckPermsContractBridge.java").read_text(encoding="utf-8")
routes = (resources.parent / "java/dev/hydev/spectrecore/command/CommandRoutes.java").read_text(encoding="utf-8")
assert "InventoryClickEvent" in java and "InventoryDragEvent" in java
assert "event.setCancelled(true)" in java and "Bukkit.getScheduler().runTask(plugin" in java
assert "CustomHeads.fromBase64" in java
assert "TextService.component(source)" in java and ".decoration(TextDecoration.ITALIC, false)" in java
assert "ModuleConfiguration.module(plugin, \"trabajos/config.yml\")" in java
assert "ModuleConfiguration.module(plugin, \"tesoros/config.yml\")" in java
assert "jobs.purchase(player, job, rank)" in java
assert "economy.withdraw(player, price)" in module
assert "MiningProgression.contractStage" in module and "MiningProgression.contractStage" in java
assert "lockDetail" in module and "lockDetail" in java
assert "JobsContractRules.validate" in module and "purchasesInProgress" in module
assert "LuckPermsProvider.get()" in bridge and "saveUser(user)" in bridge
assert "Bukkit.dispatchCommand" not in java and "dispatchCommand" not in module
assert 'route("trabajos mineria","trabajo"' in routes
assert 'route("trabajos agricultura","trabajo"' in routes
assert 'route("trabajos caza","trabajo"' in routes
assert 'route("trabajos abastecimiento","trabajo"' in routes
for banned in ("player-command-as-op", "console-command", "type: withdraw", "%vault_eco_balance%"):
    assert banned not in (resources / "ui/trabajos.yml").read_text(encoding="utf-8")
print("PASS Jobs GUI: index with sourcing shortcut, 3 contracts, safe tiers and route")

assert "jobs.paymentWaitMillis(player, page, tier)" in java
assert "{s}" in gui["labels"]["estados"]["pago-espera"]
