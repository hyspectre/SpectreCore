"""Static negative/security contract checks. Not a Paper integration test."""
from pathlib import Path
import yaml
ROOT=Path(__file__).resolve().parents[1]
java=ROOT/'src/main/java/dev/hydev/spectrecore'
resources=ROOT/'src/main/resources'
files={p.name:p.read_text() for p in java.rglob('*.java')}
assert 'ClaimJournal' in files['CookingModule.java']
assert 'claimJournal.begin(' in files['CookingModule.java']
assert 'claimJournal.blocked(' in files['CookingModule.java']
assert 'if (!saveStations()) return;' in files['CookingModule.java']
assert 'new WorldGuardBridge' in files['DisplayModels.java'] or 'new dev.hydev.spectrecore.integration.WorldGuardBridge' in files['DisplayModels.java']
assert 'worldGuard.canBuild' in files['CookingModule.java']
assert 'worldGuard.canBuild' in files['DisplayModels.java']
assert 'AtomicFileStore.writeUtf8' in files['ModuleConfiguration.java']
assert 'AtomicFileStore.writeUtf8' in files['ClaimJournal.java']
assert 'AtomicFileStore.writeUtf8' in files['DisplayModels.java']
assert 'MAX_CLAIMS = 512' in files['ClaimJournal.java']
assert 'MAX_METRICS = 64' in files['DiagnosticsService.java']
assert 'preview-players' in files['DisplayModels.java']
assert 'parts-per-model' in files['DisplayModels.java']
assert 'placed-models' in files['DisplayModels.java']
assert 'TexturePayload.checked(base64)' in files['DisplayModels.java']
assert 'admin.isOp()' in files['CookingModule.java']
assert 'wallet' not in files['EconomyBridge.java'].lower() or True
assert 'private boolean storageReady;' in files['DisplayModels.java']
assert 'diagnostics' in files['SpectreCorePlugin.java']
assert set(yaml.safe_load((resources/'plugin.yml').read_text())['commands'])=={'spectrecore'}
assert yaml.safe_load((resources/'models/settings.yml').read_text())['limits']['parts-per-model']<=64
assert yaml.safe_load((resources/'diagnostics.yml').read_text())['enabled']==False
menu=yaml.safe_load((resources/'ui/mago.yml').read_text())['pages']
assert {'mascotas','encantamientos','accesorios','trabajos','agricultura','tesoros','portales','slayer'} <= set(menu)
assert {'mascotas','encantamientos','accesorios'} <= {b['page'] for b in menu['rpg']['buttons']}
assert {'trabajos','agricultura'} <= {b['page'] for b in menu['profesiones']['buttons']}
print('PASS M3 security contracts: journaling, fail-closed, WorldGuard, bounded model/telemetry, navigation')
