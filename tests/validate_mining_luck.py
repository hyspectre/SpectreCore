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
assert not (resources/"ui/progreso-mineria.yml").exists(), "AuraSkills owns the progression menu"
print("PASS AuraSkills Luck: no level-30 legacy permission, capped encounters and natural mineral bonuses")
