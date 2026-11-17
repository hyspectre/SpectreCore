# SpectreCore 0.7.3-m3-alpha — Entrega integrada (staging)

**Estado:** fuentes listas para compilar y ensayar en Paper 1.21.10 / Java 21. **No es una versión validada en servidor ni un JAR de producción.** El ZIP es una copia nueva; conserva las fuentes originales 0.7.3 y m2.

## Alcance: puntos 2 a 7 del plan maestro

| Punto | Incorporado en m3 | Límites de la entrega |
|---|---|---|
| 2. Cocina y recompensas | `ClaimJournal` duradero (YAML, guardado atómico), bloqueos preventivos de estación en operaciones ambiguas, listados y cierre manual de claims; cofres de Actividades conservan estados pending/claiming y evitan reentrega automática; Vault-first en dinero | No existe transacción atómica conjunta entre inventario de Minecraft + disco. Un crash puede dejar una entrega pendiente de inspección; jamás resolver automáticamente. |
| 3. YAML | Escritura atómica para migración de configuración; mensajes transaccionales, diagnóstico, modelos, menú y cabezas configurables; validadores de títulos/valores y catálogo de presentación; `ui/mago.yml`, `diagnostics.yml`, `models/*.yml` | Permanece código antiguo con textos y reglas difíciles de editar y algunos ajustes de módulo solo se activan tras reiniciar. No equivale a «todo ya externalizado». |
| 4. El Mago | Menú `Dialog` desde `/mago`; RPG, profesiones, guías, armaduras, recetario y nuevas páginas de consulta; enlaces internos únicamente, sin ejecutar comandos arbitrarios de YAML; MiniMessage en nueva UI y recursos | Páginas informativas de sistemas futuros no implican implementación de mascotas, accesorios ni nuevos encantamientos. Recetas conservan la GUI de inventario. |
| 5. Integraciones | Detección opcional de los 13 plugins declarados, WorldGuard BUILD comprobado al registrar/retirar cocina y colocar/retirar modelos (hook reflexivo); MMOItems existente para objetos, Vault-first en dinero, zMenu existente por comando y LuckPerms existente por comandos validados | **No** se implementaron adaptadores API profundos para todos los plugins. WorldGuard/Vault/MMOItems necesitan comprobarse con las versiones reales. Preferir interfaces existentes y no añadir dependencias obligatorias sin necesidad. |
| 6. Modelos 3D | `BlockDisplay`, `ItemDisplay`, `TextDisplay` YAML, `PLAYER_HEAD` Base64 validado, vistas previas temporales, anclas persistentes en `models/placements.yml`, restore por chunk cargado, limpieza de entidades y animaciones simples BOB/SPIN | Sin importador Blockbench ni animación de esqueletos. Entidades no cargan chunks a la fuerza. Los modelos deben probarse in-game. |
| 7. Diagnóstico y estructura | `DiagnosticsService` acotado (64 métricas) desactivado por defecto, `/spectrecore diagnostico`, persistencia compartida `AtomicFileStore`, límites de vista previa/modelos, un test adicional de diagnóstico y contrato de seguridad | `CookingModule` sigue siendo extenso; la división del motor en subservicios es **parcial**. No se dispone aún de benchmark real TPS/MSPT. |

### Archivos nuevos/importantes

- `src/main/java/dev/hydev/spectrecore/storage/ClaimJournal.java` — cuarentena de operaciones ambiguas.
- `src/main/java/dev/hydev/spectrecore/integration/WorldGuardBridge.java` — hook opcional, deniega si WorldGuard está presente pero la consulta falla.
- `src/main/java/dev/hydev/spectrecore/diagnostics/DiagnosticsService.java` — contadores y duración de operaciones, acotados.
- `src/main/java/dev/hydev/spectrecore/model/DisplayModels.java` — motor de modelos persistentes.
- `src/main/resources/ui/mago.yml` — interfaz temática y textos en MiniMessage.
- `src/main/resources/models/settings.yml` — límites defensivos.
- `src/main/resources/models/ejemplo.yml` — modelo 3D de ejemplo.
- `src/main/resources/diagnostics.yml` — telemetría desactivada por defecto.
- `tests/DiagnosticsSmoke.java` y `tests/validate_m3_security.py` — nuevas comprobaciones.
- `.github/workflows/build.yml` — versión y ruta del artefacto actualizadas; ejecuta todas las pruebas disponibles antes de compilar.

## Compatibilidad y continuidad

Se preservan los ocho módulos (`equipamiento`, `cocina`, `tesoros`, `resonancia`, `trabajos`, `slayer`, `dungeons`, `actividades`), sus recetas, recompensas, estadísticas y comandos anteriores. `/spectrecore` sigue administrando; `/mago` abre la interfaz pública. La configuración se migra por clave y conserva respaldos. No introducir un segundo JAR de SpectreSkills/SpectreCooking/HydevBotania en paralelo: podría ejecutar las mismas mecánicas dos veces.

### Nuevos archivos de estado (respaldar)

- `plugins/SpectreCore/cooking/claims.yml` (si existe): **no borrar ni editar a ciegas**; reclamaciones ambiguas de ítems.
- `plugins/SpectreCore/models/placements.yml`: instancias 3D guardadas, inmutables entre reinicios si su definición YAML persiste.
- `plugins/SpectreCore/activity-pending.yml`: cofres abiertos, pendientes y reclamaciones ambiguas.
- `plugins/SpectreCore/cooking/stations.yml`: estaciones y progreso de cocina.
- `plugins/SpectreCore/cooking/discoveries.yml`: descubrimientos de recetas.

## Compilar

Opción principal: subir el contenido del directorio fuente a repositorio privado de GitHub y ejecutar **Actions → Build SpectreCore → Run workflow**; descarga el artefacto `SpectreCore-0.7.3-m3-alpha` si todos los pasos pasan.

Con Java 21 y Gradle 8.14.3, desde la raíz de este proyecto:

```bash
python3 -m pip install pyyaml
python3 tests/validate_release.py
python3 tests/validate_presentation.py
python3 tests/validate_mago_models.py
python3 tests/validate_m3_security.py
bash tests/check_java_syntax.sh
bash tests/run_pure_java_tests.sh
gradle clean build --no-daemon
```

Resultado esperado **solo tras compilación exitosa**: `build/libs/SpectreCore-0.7.3-m3-alpha.jar`. No se adjunta un JAR de m3 sin un build completo de Paper.

## Limitaciones técnicas y riesgos conocidos

1. **Sin build completo de Paper aquí.** Las comprobaciones estáticas y puras no demuestran compatibilidad de clases, métodos ni comportamiento en servidor.
2. **Inventario + disco:** no existe garantía exactamente-una-vez con Bukkit + YAML. Ante claims pendientes, detener los intentos automáticos y auditar evidencias antes de resolver.
3. **Cambios de YAML a mitad de una sesión:** `/spectrecore reload` sirve para ajustes compatibles; cambios de módulos, formato de estado y algunos ciclos de vida requieren reinicio programado. Nunca `/reload` global de Bukkit.
4. **WorldGuard:** el hook de permisos está diseñado según la API 7 pública pero se invoca por reflexión; verificar con la versión instalada. Si la consulta falla estando WorldGuard activo, la modificación se bloquea por seguridad.
5. **MiniMessage:** el motor de nueva interfaz lo soporta; algunos mensajes legados todavía utilizan `§` y `&` por compatibilidad; su migración total sigue pendiente.
6. **FancyNPCs/FancyHolograms/FAWE/zMenu/AuraSkills:** se detectan, pero no todos necesitan nuevas llamadas API en esta fase. No confundir detección con integración funcional.
7. **Modelo Base64:** contiene datos `textures` de la cabeza de Minecraft, no el PNG directamente; el host de textura debe ser `textures.minecraft.net`; texturas y resource packs dependen del cliente.
8. **No desplegar en producción** hasta compilar, pasar ensayo en staging y ejecutar al menos la batería crítica del documento `PRUEBAS_UNIFICADAS_0.7.3-m3.md`.

## Criterio de certificación (nuestro `/magorelease`)

- **BLOQUEADO** si no compila, hay errores en carga de YAML, un claim se duplica, se pueden vulnerar permisos, o hay pérdidas/regresiones graves.
- **CONDICIONAL** cuando compila y supera pruebas críticas pero falta rendimiento a escala, integración de terceros o prueba de reinicio.
- **APTO PARA LANZAR** únicamente si Paper staging demuestra funcionalidad, seguridad y rendimiento aceptables y existen copias restaurables.

La decisión de publicar corresponde a la administración de Hydev.
