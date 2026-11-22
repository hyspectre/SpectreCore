"""All SpectreCore modules are unconditional; old switches are migrated away."""
from pathlib import Path
import yaml

root = Path(__file__).resolve().parents[1]
resources = root / "src/main/resources"

def read(name):
    return yaml.safe_load((resources / name).read_text(encoding="utf8"))

config = read("config.yml")
assert config == {"config-version": 2}, "Global module flags must not exist"
retired = {
    "modules/accesorios/config.yml": ("enabled",),
    "modules/accesorios/catalogo.yml": ("enabled",),
    "modules/tesoros/mineria.yml": ("enabled",),
    "modules/tesoros/combate.yml": ("enabled",),
    "modules/tesoros/agricultura.yml": ("enabled",),
    "modules/dungeons/config.yml": ("jefes.enabled", "portal.enabled"),
    "modules/trabajos/config.yml": ("minero.enabled", "granjero.enabled", "cazador.enabled", "contratos.habilitados"),
    "modules/actividades/config.yml": ("cygnus.enabled", "entrada.enabled",
        "cofre.enabled", "polvos.enabled", "desbloqueos.enabled", "telefono.enabled"),
    "modules/equipamiento/config.yml": (
        "skills.botania.enabled", "skills.necrotica.enabled",
        "skills.asterion.enabled", "skills.ultimo-juramento.enabled",
        "skills.sangre-primer-dragon.enabled", "libro.enabled"),
}

def nested(obj, dotted):
    for key in dotted.split("."):
        if not isinstance(obj, dict) or key not in obj:
            return False
        obj = obj[key]
    return True

for name, flags in retired.items():
    data = read(name)
    for flag in flags:
        assert not nested(data, flag), (name, flag)

for name in ("armaduras", "misiones", "recetario", "trabajos", "relicario"):
    assert "enabled" not in read(f"ui/{name}.yml"), name

module = (resources.parent / "java/dev/hydev/spectrecore/config/ModuleConfiguration.java").read_text(encoding="utf8")
router = (resources.parent / "java/dev/hydev/spectrecore/command/CommandRouter.java").read_text(encoding="utf8")
plugin = (resources.parent / "java/dev/hydev/spectrecore/SpectreCorePlugin.java").read_text(encoding="utf8")
relic = (resources.parent / "java/dev/hydev/spectrecore/relicario/RelicarioModule.java").read_text(encoding="utf8")

for name in ("equipamiento", "cocina", "tesoros", "resonancia",
             "trabajos", "slayer", "dungeons", "actividades", "accesorios"):
    assert f'"{name}"' in module, name
assert "for(String name:MODULES)flags.put(name,true)" in module
assert 'index.set("modules",null)' in module
assert "removeModuleSwitches(path,yaml)" in module
assert "backups/always-on-v1/" in module
assert "public boolean restartPending(String name){return false;}" in module
assert "private boolean enabled(CommandRoutes.Route route)" not in router
assert "&&enabled(" not in router and "if(!enabled(" not in router
assert 'Siempre activo' in plugin
assert 'getBoolean("enabled", false)' not in relic

print("PASS modules always-on: 9 mandatory modules, child systems, no player-facing menu switches")
