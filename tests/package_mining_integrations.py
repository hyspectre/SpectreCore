"""Reproducible distribution ZIP for Hydev Mining integration."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from sys import argv

root=Path(__file__).resolve().parents[1]
destination=Path(argv[1]) if len(argv)>1 else root/"build/Hydev_Mineria_Integraciones_v2.zip"
assets={
    "integracion-auraskills/rewards/mining.yml":"AuraSkills/rewards/mining.yml",
    "integracion-mythicmobs/HYDEV_MINERIA.yml":"MythicMobs/Mobs/HYDEV_MINERIA.yml",
    "integracion-mythicmobs/HYDEV_MINERIA_SKILLS.yml":"MythicMobs/Skills/HYDEV_MINERIA_SKILLS.yml",
}
folder=root/"integracion-mmoitems/mineria-v2"
for name in ("MATERIAL_MINERIA_NUEVOS.yml","TOOL_MINERIA_NUEVOS.yml","TOOL_EXISTENTES_PRESENTACION.yml",
             "GEM_STONE_MINERIA_NUEVOS.yml","RECETAS_FORJA_MINERIA.yml",
             "RECETA_PICO_PERDIDO_REEMPLAZO.yml","PERMISOS_OBJETOS_EXISTENTES.yml",
             "GEM_STONE_RAMAS_MINERAS.yml","RELIQUIA_MINERIA.yml","forja-minera.yml","gema-minera.yml","reliquia-compacta.yml","herramienta-minera.yml",
             "instalar_mineria.py","LEEME_INSTALACION.md"):
    assets[f"integracion-mmoitems/mineria-v2/{name}"]=f"MMOItems/mineria-v2/{name}"
destination.parent.mkdir(parents=True,exist_ok=True)
with ZipFile(destination,"w",compression=ZIP_DEFLATED,compresslevel=9) as archive:
    for relative,target in sorted(assets.items()):
        source=root/relative
        if not source.is_file():raise FileNotFoundError(source)
        archive.write(source,target)
print(f"PASS package {destination} ({len(assets)} files)")
