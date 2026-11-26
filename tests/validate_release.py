from pathlib import Path
import yaml
import re

root = Path(__file__).resolve().parents[1]
resources = root / 'src/main/resources'
def load(path):
    return yaml.safe_load((resources / path).read_text(encoding='utf-8'))
index = load('config.yml')
assert index['config-version'] == 2
assert 'modules' not in index, 'Modules must have no configurable enable switches'
assert not {'skills','cooking','engine'} & set(index)
code = (root/'src/main/java/dev/hydev/spectrecore/config/ModuleConfiguration.java').read_text(encoding='utf-8')
for name in ('equipamiento','cocina','tesoros','resonancia','trabajos','slayer','dungeons','actividades','accesorios'):
    assert f'"{name}"' in code, name
assert 'flags.put(name,true)' in code and 'index.set("modules",null)' in code
plugin = load('plugin.yml')
gradle = (root/'build.gradle.kts').read_text(encoding='utf-8')
version = re.search(r'^version\s*=\s*"([^"]+)"', gradle, re.MULTILINE)
assert version is not None and plugin['name'] == 'SpectreCore' and plugin['version'] == version.group(1)
assert set(plugin['commands']) == {'spectrecore'} and not plugin['commands']['spectrecore'].get('aliases')
routes = (root/'src/main/java/dev/hydev/spectrecore/command/CommandRoutes.java').read_text(encoding='utf-8')
assert 'route("mago","mago","",Access.PUBLIC' in routes, 'El Mago must remain public below /spectrecore'
equipment = load('modules/equipamiento/config.yml')
expected = {'botania':'VERDANA_ARMOR','necrotica':'NECROTICO','asterion':'MINOTAURO_ARMOR','ultimo-juramento':'CABALLERO_ARMOR','sangre-primer-dragon':'DRACONIDO_ARMOR'}
assert set(equipment['skills']) == set(expected)
for skill,prefix in expected.items():
    for slot,suffix in [('helmet',1),('chestplate',2),('leggings',3),('boots',4)]:
        assert equipment['skills'][skill]['armor'][slot] == f'{prefix}_{suffix}'
cooking = load('modules/cocina/config.yml')['cooking']
assert cooking['visuals']['visible-ingredients'] and cooking['servings']['water-level-portions']
recipes = load('modules/cocina/recetas.yml')['recetas']
items = yaml.safe_load((root/'integracion-mmoitems/COCINA.yml').read_text(encoding='utf-8'))
assert len(recipes) == 15 and len(items) == 45
assert not (root/'integracion-mmoitems/CONSUMABLE_Cocina.yml').exists(), 'No debe existir catálogo culinario de CONSUMABLE'
assert {'guardian','alba','carmesi','espectral','minero','velocidad','espeleologo','mareas','saltador','escudero','brasa','vigor','sombra','maraton','leyenda'} == set(recipes)
for recipe in recipes.values():
    assert len(recipe['ingredientes']) == 3
    assert recipe['revolver-desde'] < recipe['revolver-hasta'] <= recipe['listo-desde'] < recipe['listo-hasta'] < recipe['quemado']
    for quality in ['normal','exquisito','pasado']:
        assert recipe['resultado-'+quality] in items
mining = load('modules/tesoros/mineria.yml')
assert 'enabled' not in mining and mining['tesoros']['id'] == 'PALADIO'
assert mining['tesoros']['cantidad-minima'] == 2 and mining['tesoros']['cantidad-maxima'] == 8
assert mining['probabilidades']['mineria'] == {'tier1':0.65,'tier2':0.90,'tier3':1.40} and 'gemas' not in mining
resonance = load('modules/resonancia/config.yml')
assert resonance['gemas']['carga-maxima'] == 50 and resonance['gemas']['ventana-segundos'] == 30
assert resonance["gemas"]["ids"] == {quality: [f"GEMA_{family}_{grade}" for family in ("RUBI","ONICE","AMATISTA")] for grade,quality in enumerate(("basica","intermedia","superior"),1)}
assert resonance['gemas']['pico'] == 'PICO_GEMAS' and resonance['gemas']['identificar-por'] == 'id'
assert load('modules/tesoros/combate.yml')['botin'] == {'tipo':'MATERIAL','id':'ALMA_1'}
assert load('modules/tesoros/agricultura.yml')['botin'] == {'tier1':'AGRICULTURA_T1','tier2':'AGRICULTURA_T2','tier3':'AGRICULTURA_T3'}
assert load('modules/tesoros/config.yml')['cooldown-compartido-segundos'] == 5
jobs = load('modules/trabajos/config.yml')
expected_jobs = {
    'minero': ((10,1,4),(7,4,6),(4,8,12)),
    'granjero': ((8,1,3),(5,3,6),(3,6,10)),
    'cazador': ((12,2,6),(8,6,12),(5,12,24)),
}
for job, tiers in expected_jobs.items():
    assert 'enabled' not in jobs[job]
    for name,(cooldown,minimum,maximum) in zip(('novato','experimentado','profesional'),tiers):
        assert jobs['cooldowns'][job][name] == cooldown
        assert jobs[job][name]['dinero'] == {'minimo':minimum,'maximo':maximum}
assert 'habilitados' not in jobs['contratos']
assert jobs['contratos']['precios'] == {'novato':50,'experimentado':1500,'profesional':3500}
slayer = load('modules/slayer/config.yml')
assert slayer['objetivos'] == {'tier1':20,'tier2':50,'tier3':90}
portal = load('modules/dungeons/portal.yml')
assert portal['radio-participantes'] == 64 and portal['tiempo-dentro-segundos'] == 5
activities = load('modules/actividades/config.yml')
assert len(activities['cygnus']['movimientos']) == 7 and activities['cygnus']['probabilidad-fuga'] == 30
assert sum(activities['cofre']['premios'].values()) == 100
assert len(activities['cygnus']['ofertas']) == 7 and len(activities['polvos']['comandos']) == 6
assert activities['entrada']['nivel-resistencia'] == 100 and activities['entrada']['duracion-segundos'] == 30
assert len(list((resources/'modules').rglob('*.yml'))) == 35
for obsolete in ['mining.yml','jobs.yml','activities.yml','portal.yml','cooking/recipes.yml']:
    assert not (resources/obsolete).exists(), obsolete
print('PASS: 9 modules, 35 configuration files, unchanged default rewards/skills/recipes, validated exact job salaries, cooldowns and contract prices')
