"""Mining Luck is a real AuraSkills statistic, not a parallel progression menu."""
from pathlib import Path
import yaml
root=Path(__file__).resolve().parents[1]
src=root/"src/main/java/dev/hydev/spectrecore"
resources=root/"src/main/resources"
cfg=yaml.safe_load((resources/"modules/tesoros/mineria.yml").read_text(encoding="utf-8"))
rewards=yaml.safe_load((root/"integracion-auraskills/rewards/mining.yml").read_text(encoding="utf-8"))
mobs=yaml.safe_load((root/"integracion-mythicmobs/HYDEV_MINERIA.yml").read_text(encoding="utf-8"))
materials=yaml.safe_load((root/"integracion-mmoitems/MATERIAL_MINERIA.yml").read_text(encoding="utf-8"))
assert rewards["patterns"] == [
    {"type":"stat","stat":"toughness","value":1,"pattern":{"interval":1}},
    {"type":"stat","stat":"luck","value":1,"pattern":{"interval":2}}
]
assert 30 not in rewards.get("levels",{})
assert "tesoromineria1" not in str(rewards)
assert set(mobs)=={"HYDEV_DUENDE_MINERO","HYDEV_PROSPECTOR_ANTIGUO"}
assert {"CHATARRA_MECANICA","CHATARRA_ANTIGUA","FRAGMENTO_PICO_PERDIDO"}==set(materials)
assert all(m["Options"]["PreventOtherDrops"] for m in mobs.values())
assert cfg["probabilidades"]["mineria"]=={"tier1":.65,"tier2":.9,"tier3":1.4}
assert cfg["tesoros"]["id"]=="PALADIO" and cfg["mitrilo"]["id"]=="MITRILO"
assert cfg["encuentros"]["max-activos"]<=16
assert cfg["encuentros"]["duende"]["mob"] in mobs
assert cfg["encuentros"]["prospector"]["mob"] in mobs
assert cfg["encuentros"]["prospector"]["botin"]["paladio"]["id"]=="PALADIO"
for key in ("hallazgos","encuentros","botin"):
    assert 0<=cfg["suerte"][key]["incremento-relativo-por-punto"]<=10
    assert 1<=cfg["suerte"][key]["multiplicador-maximo"]<=5
for key in ("paladio","mitrilo"):
    assert 0<=cfg["suerte"]["cantidad-"+key]["maximo-porcentaje"]<=100
mining=(src/"mining/MiningModule.java").read_text(encoding="utf-8")
encounters=(src/"mining/MiningEncounters.java").read_text(encoding="utf-8")
rules=(src/"mining/MiningLuckRules.java").read_text(encoding="utf-8")
aura=(src/"integration/AuraSkillsBridge.java").read_text(encoding="utf-8")
assert "getStatLevel(Stats.LUCK)" in aura
assert "new MiningEncounters(plugin, luck)" in mining
assert "MiningLuckRules.chance(" in mining and "MiningLuckRules.quantity(" in mining
assert "MithrilAutomationGuard.active()" in mining
assert "placed.consume(block)" in mining and "Material.OBSIDIAN" in mining
assert "ThreadLocalRandom.current().nextDouble(100)" in encounters
assert "MiningLuckRules.chance(" in encounters and "MiningLuckRules.quantity(" in encounters
assert "getPersistentDataContainer()" in encounters and "EntityDeathEvent" in encounters
assert "event.getDrops().clear()" in encounters and "getKiller()" in encounters
assert "maxMultiplier" in rules and "bonusCapPercent" in rules
assert "qualityRoll" in rules
assert "MiningLuckRules.qualityRoll(" in mining
assert cfg["suerte"]["calidad-gemas"] == {"puntos-por-luck":.05,"maximo-puntos":5.0}

assert not (resources/"ui/progreso-mineria.yml").exists(), "AuraSkills owns the progression menu"
progress=yaml.safe_load((resources/"modules/trabajos/config.yml").read_text(encoding="utf-8"))
levels=progress["progresion-mineria"]
assert {k:levels[k] for k in ("inicial","paladio","duendes","resonancia","mitrilo","arcana","ingenieria")}=={
    "inicial":1,"paladio":6,"duendes":12,"resonancia":15,"mitrilo":18,"arcana":22,"ingenieria":25}
assert progress["taller-minero"]["nivel-minimo"]==3
assert not (resources/"ui/progreso-mineria.yml").exists()
resonance=yaml.safe_load((resources/"modules/resonancia/config.yml").read_text(encoding="utf-8"))
assert resonance["gemas"]["pico"]=="PICO_GEMAS"
assert resonance["gemas"]["identificar-por"]=="id"
gem_items=yaml.safe_load((root/"integracion-mmoitems/GEM_STONE_PICO_PERDIDO.yml").read_text(encoding="utf-8"))
assert {gem for pool in resonance["gemas"]["ids"].values() for gem in pool}==set(gem_items)
drill=yaml.safe_load((root/"integracion-mmoitems/TOOL_TALADRO_MEJORAS.yml").read_text(encoding="utf-8"))
assert set(drill)=={"TALADRO_REFORZADO","TALADRO_CALIBRADO"}
recipes=yaml.safe_load((root/"integracion-mmoitems/RECETAS_FORJA_TALADRO.yml").read_text(encoding="utf-8"))["recipes"]
assert set(recipes)=={"taladro-reforzado","taladro-calibrado"}
lost=yaml.safe_load((root/"integracion-mmoitems/RECETA_PICO_PERDIDO.yml").read_text(encoding="utf-8"))["recipes"]["picoperdido"]
assert "id=PICO_GEMAS" in lost["output"] and any("FRAGMENTO_PICO_PERDIDO" in x for x in lost["ingredients"])
assert "TOOL:PICO_PERDIDO" not in (root/"integracion-mmoitems/RECETA_PICO_PERDIDO.yml").read_text(encoding="utf-8")
assert (src/"mining/MiningProgression.java").exists()
progress_java=(src/"mining/MiningProgression.java").read_text(encoding="utf-8")
assert "SkillLevelUpEvent" not in progress_java
assert 'player.hasPermission(permission(stage))' in progress_java
assert 'return "hydev.mineria."+stage;' in progress_java
assert sorted(rewards["levels"])==[1,3,6,12,15,18,22,25]
for level, stage in [(1,"inicial"),(3,"hechizados"),(6,"paladio"),(12,"duendes"),
                     (15,"resonancia"),(18,"mitrilo"),(22,"arcana"),(25,"ingenieria")]:
    prize=rewards["levels"][level][0]
    assert prize["type"]=="permission" and prize["value"] is True
    assert prize["permission"]=="hydev.mineria."+stage
    assert prize["menu_message"] and prize["chat_message"]
assert "hydev.mineria.hechizados" in (src/"systems/MinerWorkshop.java").read_text(encoding="utf-8")
assets=root/"integracion-mmoitems/mineria-v2"
newtypes={name:yaml.safe_load((assets/name).read_text(encoding="utf-8")) for name in
        ("MATERIAL_MINERIA_NUEVOS.yml","GEM_STONE_MINERIA_NUEVOS.yml","TOOL_MINERIA_NUEVOS.yml")}
assert "AMATISTA_RESONANTE" in newtypes["MATERIAL_MINERIA_NUEVOS.yml"]
assert len(newtypes["GEM_STONE_MINERIA_NUEVOS.yml"])==3
assert len(newtypes["TOOL_MINERIA_NUEVOS.yml"])==8
assert "PICO_GEMAS" not in newtypes["TOOL_MINERIA_NUEVOS.yml"]
assert "TALADRO" not in newtypes["TOOL_MINERIA_NUEVOS.yml"]
assert "PALADIO" not in newtypes["MATERIAL_MINERIA_NUEVOS.yml"]
assert (root/"integracion-mythicmobs/HYDEV_MINERIA_SKILLS.yml").is_file()
skills=yaml.safe_load((root/"integracion-mythicmobs/HYDEV_MINERIA_SKILLS.yml").read_text(encoding="utf-8"))
assert len(skills)>=7
for mob in mobs.values():
    assert len(mob["Skills"])>=3 and mob["Options"]["NoAI"] is False
    assert "meleeattack" in mob["AIGoalSelectors"] and "players" in mob["AITargetSelectors"]
assert "progression.meets(player,\"mitrilo\")" in mining
assert "progression.meets(player,\"resonancia\")" in mining
assert "progression.meets(player,\"paladio\")" in mining
assert "progression.meets(player,\"ingenieria\")" in (src/"mining/MithrilLantern.java").read_text(encoding="utf-8")
drill_src=(src/"mining/StoneDrill.java").read_text(encoding="utf-8")
assert 'tier==3?"resonancia":tier==2?"duendes":"paladio"' in drill_src
assert "progression.meets(player,requiredStage)" in drill_src
print("PASS AuraSkills Luck: levels 1/3/6/12/15/18/22/25, old Pico identity, native encounters and stone drill")
