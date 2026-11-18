"""Cross-check every Settings call against its packaged module key and default."""
from pathlib import Path
import re,json,yaml,math
root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
class UniqueLoader(yaml.SafeLoader):pass
def unique(loader,node,deep=False):
    result={}
    for key,value in node.value:
        name=loader.construct_object(key,deep=deep)
        assert name not in result, f'Duplicate YAML key: {name}'
        result[name]=loader.construct_object(value,deep=deep)
    return result
UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG,unique)
def flatten(value,prefix=''):
    out={}
    for key,item in value.items():
        name=prefix+key
        if isinstance(item,dict):out.update(flatten(item,name+'.'))
        else:out[name]=item
    return out
configs={p.relative_to(resources/'modules').as_posix():flatten(yaml.load(p.read_text(encoding='utf8'),Loader=UniqueLoader) or {}) for p in (resources/'modules').rglob('*.yml')}
string=r'"(?:\\.|[^"\\])*"'
pattern=re.compile(r'\bSettings\.(text|string|i|l|d|f|sound|particle|color|material|potion)\(\s*('+string+r')\s*,\s*('+string+r')\s*,\s*('+string+r'|(?:org\.bukkit\.|net\.kyori\.adventure\.text\.format\.)?(?:Material|Sound|Particle|NamedTextColor|PotionEffectType)\.[A-Z_0-9]+|-?(?:\d+\.\d*|\.\d+|\d+)(?:[fFdDlL])?)')
used={};catalog=[];count=0
for path in (root/'src/main/java').rglob('*.java'):
    source=path.read_text(encoding='utf8')
    for match in pattern.finditer(source):
        kind,module,key,default=match.groups();module=json.loads(module);key=json.loads(key)
        config=module+'/'+('mensajes' if kind=='text' else 'ajustes')+'.yml'
        assert key in configs[config], f'{path.name}: missing {config} {key}'
        actual=configs[config][key]
        if default.startswith('"'):expected=json.loads(default)
        elif kind in ('sound','particle','color','material','potion'):expected=default.split('.')[-1]
        else:expected=float(re.sub('[fFdDlL]$','',default))
        assert actual==expected, f'Default drift {config} {key}: {actual!r} != {expected!r}'
        if isinstance(actual,float):assert math.isfinite(actual)
        used.setdefault(config,set()).add(key);count+=1
        catalog.append({'archivo':config,'clave':key,'predeterminado':actual,'origen':path.name+':'+str(source.count('\n',0,match.start())+1)})
    if path.name=='DisplayModels.java':
        # The model service routes short messages through its own Settings.text wrapper.
        for match in re.finditer(r'\bmessage\(\s*\w+\s*,\s*("(?:\\.|[^"\\])*")\s*,\s*("(?:\\.|[^"\\])*")',source):
            key,default=(json.loads(value) for value in match.groups());config='nucleo/mensajes.yml'
            assert configs[config][key]==default, f'Model wrapper drift: {key}'
            used.setdefault(config,set()).add(key);count+=1
            catalog.append({'archivo':config,'clave':key,'predeterminado':default,'origen':'DisplayModels.java'})
    if path.name=='CommandRouter.java':
        for match in re.finditer(r'say\(sender,\s*('+string+r'),\s*('+string+r')',source):
            key='CommandRouter.'+json.loads(match.group(1));expected=json.loads(match.group(2));config='nucleo/mensajes.yml'
            assert configs[config][key]==expected, f'Router message drift: {key}'
            used.setdefault(config,set()).add(key);count+=1
            catalog.append({'archivo':config,'clave':key,'predeterminado':expected,'origen':'CommandRouter.java'})
assert 'Settings.install(moduleConfigs);' in (root/'src/main/java/dev/hydev/spectrecore/SpectreCorePlugin.java').read_text(encoding='utf8'), 'Missing live settings binding'
# ToastService uses dynamically named presets, not literal Settings calls.
# Keep key completeness strict: only its documented fields may be present.
presets = {'furia', 'resonancia', 'gema-basica', 'gema-intermedia', 'gema-superior',
           'trabajo-minero', 'trabajo-granjero', 'trabajo-cazador'}
toast = configs['nucleo/ajustes.yml']
assert toast['toasts.enabled'] in (True, False)
assert type(toast['toasts.intervalo-global-segundos']) is int and 0 <= toast['toasts.intervalo-global-segundos'] <= 600
toast_keys = {'toasts.enabled', 'toasts.intervalo-global-segundos'}
for event in presets:
    flag = 'toasts.eventos.' + event
    toast_keys.add(flag)
    assert type(toast[flag]) is bool, flag
    for field in ('titulo','descripcion','icon'):
        key = 'toasts.presets.' + event + '.' + field
        toast_keys.add(key)
        assert type(toast[key]) is str and 0 < len(toast[key]) <= 120, key
        if field == 'icon':
            assert re.fullmatch(r'(minecraft:)?[a-z0-9_]+',toast[key]), key
used.setdefault('nucleo/ajustes.yml',set()).update(toast_keys)
for key in sorted(toast_keys):
    catalog.append({'archivo':'nucleo/ajustes.yml','clave':key,'predeterminado':toast[key],
                    'origen':'ToastService.java (dynamic)'})
for path,values in configs.items():
    if path.endswith(('/mensajes.yml','/ajustes.yml')):
        # Historical book palette keys remain readable for migration compatibility.
        obsolete={'ArmorGuide.color-001','ArmorGuide.color-002','ArmorGuide.color-003','ArmorGuide.color-004'} if path=='equipamiento/ajustes.yml' else ({'ArmorGuide.mensaje-001','ArmorGuide.mensaje-002','ArmorGuide.mensaje-003','ArmorGuide.mensaje-004','ArmorGuide.mensaje-005','ArmorGuide.mensaje-054','ArmorGuide.mensaje-055','ArmorGuide.mensaje-056','ArmorGuide.mensaje-057','ArmorGuide.texto-001','ArmorGuide.texto-002'} if path=='equipamiento/mensajes.yml' else set())
        assert set(values)-obsolete==used.get(path,set()), f'Orphan presentation keys in {path}: {set(values)-used.get(path,set())}' 
(root/'CATALOGO_CONFIGURACION.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf8')
print(f'PASS: {count} presentation bindings, exact defaults, unique YAML keys, live loader wiring and complete key coverage')
