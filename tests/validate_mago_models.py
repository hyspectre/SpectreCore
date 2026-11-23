from pathlib import Path
import yaml
import re
root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
class StrictLoader(yaml.SafeLoader): pass
def unique(loader,node):
    pairs=loader.construct_pairs(node,deep=True)
    result={}
    for k,v in pairs:
        assert k not in result,f'Duplicate YAML key: {k}'
        result[k]=v
    return result
StrictLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique)
def read(name):
    return yaml.load((resources/name).read_text(encoding='utf8'),Loader=StrictLoader)
mago=read('ui/mago.yml')
assert set(mago['pages']) >= {'inicio','rpg','profesiones','cocina','habilidades','mascotas','accesorios','encantamientos'}
for id,page in mago['pages'].items():
    assert re.fullmatch(r'[a-z0-9_-]{1,48}',id),id
    assert isinstance(page['title'],str) and isinstance(page['body'],str)
    assert len(page['body'])<=3200
    assert len(page.get('buttons',[]))<=12
    for button in page.get('buttons',[]):
        assert button['page'] in mago['pages'] or button['page'] in {'armaduras','recetas'}
        assert len(button['label'])<=120
model=read('models/ejemplo.yml')
assert 1<=len(model['parts'])<=24 and 20<=model['duration-ticks']<=1200
for part in model['parts']:
    assert part['type'] in {'BLOCK_DISPLAY','ITEM_DISPLAY','TEXT_DISPLAY'}
    for field in ('offset','scale'):
        assert isinstance(part[field],list) and len(part[field])==3
        assert all(isinstance(n,(float,int)) for n in part[field])
    assert not part.get('base64') or part['type']=='ITEM_DISPLAY'
plugin=read('plugin.yml')
assert set(plugin['commands'])=={'spectrecore'}
assert 'route("mago","mago","",Access.PUBLIC' in (root/'src/main/java/dev/hydev/spectrecore/command/CommandRoutes.java').read_text(encoding='utf8')
required={'MythicLib','MMOItems','MythicMobs','ItemsAdder','AuraSkills','FancyNPCs','FancyHolograms','zMenu','WorldGuard','LuckPerms','Essentials','FastAsyncWorldEdit'}
assert required<=set(plugin['softdepend'])
assert not any('Book.book(' in p.read_text(encoding='utf8') or '.openBook(' in p.read_text(encoding='utf8') for p in (root/'src/main/java').rglob('*.java'))
print('PASS: Mago navigation, MiniMessage YAML, 3D sample, integration markers, no book UI')
