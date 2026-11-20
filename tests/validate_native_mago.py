"""Independent native El Mago knowledge GUI, with configurable content and safe navigation."""
from pathlib import Path
import base64
import json
import re
import yaml

root = Path(__file__).resolve().parents[1]
resource = root / "src/main/resources/ui/mago.yml"
data = yaml.safe_load(resource.read_text(encoding="utf8"))
visual = data["visual"]
assert {"portada", "volver", "cerrar", "accion", "iconos"} <= set(visual)
assert "inicio" in data["pages"] and "recetario_info" in data["pages"]
assert len(data["pages"]) >= 18

for name, page in data["pages"].items():
    assert page["title"].startswith("<dark_gray>⧇ <gold>El Mago"), name
    assert isinstance(page["resumen"], str) and page["resumen"], name
    assert 2 <= len(page["puntos"]) <= 5, name
    assert all(isinstance(v,str) and len(v) <= 110 for v in page["puntos"]), name
    for link in page.get("buttons", []):
        assert link["page"] in data["pages"], (name, link)
        assert link["page"] not in {"armaduras", "recetas"}, (name, link)
        assert "descripcion" in link, (name, link)
        assert re.search(r"/(?:mago|spectrecore)\b", link["label"] + link["descripcion"]) is None, (name,link)

assert len(visual["iconos"]) >= 15
for icon in [visual["portada"],visual["volver"],visual["cerrar"],*visual["iconos"].values()]:
    if icon:
        url = json.loads(base64.b64decode(icon,validate=True))["textures"]["SKIN"]["url"]
        assert re.fullmatch(r"https?://textures\.minecraft\.net/texture/[a-fA-F0-9]{32,128}",url),url

java = (root / "src/main/java/dev/hydev/spectrecore/guide/MagoMenu.java").read_text(encoding="utf8")
assert "implements Listener" in java
assert "Bukkit.createInventory(holder, 54" in java
assert "CustomHeads.fromBase64(encoded)" in java
assert "event.setCancelled(true)" in java
assert "Bukkit.getScheduler().runTask(plugin" in java
assert "holder.history" in java
assert "mergeVisuals(installed, candidate)" in java
assert "player.showDialog" not in java and "DialogAction" not in java
assert "plugin.armors()" in java  # Preserved explicit legacy command access.
print("PASS Native El Mago: independent pages, accessible tips, Base64 icons and secure 54-slot navigation")
