# SpectreCore 0.7.3-m1-alpha — primera intervención (STAGING)

**No es un release para producción.** Basado en el ZIP de 0.7.3 (se preserva sin modificar).

## Cambios reales

1. `storage/AtomicFileStore`: reemplazo de pequeños archivos de estado a través de temporal + renombrado atómico cuando el sistema de archivos lo permite, escritura UTF-8 y copia única de respaldo `.bak` de un archivo existente.
2. Cocina: `stations.yml` usa `AtomicFileStore`, lectura YAML estricta; si el archivo es inválido o no se puede guardar, detiene interacciones que consumen ingredientes y registra el error. No sobrescribe silenciosamente el estado corrupto.
3. Cofres de Actividades: `activity-pending.yml` mantiene el formato antiguo `UUID: MATERIAL` para pendientes; agrega `claims.<UUID>.material` para entregas que entraron en fase `CLAIMING`. Se escribe `CLAIMING` antes de entregar el premio, y su eliminación se persiste después. Si ocurre un cierre entre ambas operaciones, **no se reintenta automáticamente**, para evitar duplicaciones.
4. Desconexiones y apagado: un premio pendiente se conserva para el siguiente ingreso; ya no se entrega a un jugador mientras abandona el servidor ni durante `onDisable`.
5. Nuevos mensajes de error para jugadores configurables en `modules/cocina/mensajes.yml` y `modules/actividades/mensajes.yml`.
6. Se reutiliza el almacenamiento atómico desde `systems/YamlStore` y se añade `tests/AtomicFileStoreSmoke.java`.

## Límites y riesgos conocidos

- **No se garantiza entrega exactamente una vez:** Minecraft no permite una transacción atómica conjunta entre inventario, mundo y archivo YAML. `CLAIMING` significa entrega posiblemente realizada, necesita revisión humana. En caso de duda NO conceder otro premio. En próximas fases se añadirán trazas para conciliación más sencilla.
- El sistema de Cocina todavía puede consumir/entregar recursos antes de persistir completamente cada acción; el guardado atómico previene archivos truncados, **no elimina todas las ventanas de pérdida o duplicación tras una caída**.
- Las escrituras son síncronas en el hilo principal como en la versión anterior; se debe medir MSPT en staging y estudiar snapshots/batching seguro en siguientes fases.
- La copia `.bak` es una instantánea inicial, no un historial continuo. Restaurar una copia puede revertir cambios posteriores.
- Si la plataforma no admite `ATOMIC_MOVE`, el código hace un reemplazo convencional; no es equivalente a un commit transaccional.
- No se ha compilado contra Paper ni probado en juego en este entorno. Las pruebas Java aisladas y comprobación de sintaxis no sustituyen esa verificación.

## Recuperación de un cofre en CLAIMING (solo administrador)

1. Detén el servidor y haz una copia de `plugins/SpectreCore/activity-pending.yml` y del mundo/inventario.
2. Busca `claims.<UUID>.material` y comprueba la entrega a ese jugador con inventario, registros y posibles drops. Si NO puedes determinar si fue entregado, **no repitas la entrega** y mantén el registro.
3. Si está inequívocamente entregado: elimina solo el registro `claims.<UUID>` y arranca de nuevo.
4. Si está inequívocamente no entregado: elimina `claims.<UUID>` y añade una entrada raíz `<UUID>: <MATERIAL>`; tras el siguiente ingreso podrá completarse. Mantén un registro administrativo de lo resuelto.
5. No edites el YAML con el servidor encendido. Para deshacer cambios, restaura el backup previo, no combines dos estados incompatibles.

## Batería de pruebas manuales (servidor de staging)

- [ ] Compila con Java 21 y Paper API 1.21.10; revisa que la nueva versión se habilita sin excepciones.
- [ ] Abre un cofre con un usuario normal y confirma un solo premio y borrado del registro.
- [ ] Simula cierre tras persistir PENDING y antes de entregar: al reingresar, un solo premio.
- [ ] Inyecta una interrupción controlada **después de escribir CLAIMING y antes de entregar**: al reiniciar no debe pagarse automáticamente; debe aparecer aviso para reconciliación.
- [ ] Simula inventario lleno, desconexión durante apertura, reinicio y `/stop`; no debe aparecer un doble premio.
- [ ] Guarda estaciones e ingredientes, apaga y reinicia; compara cantidad/receta/estado.
- [ ] Prueba `stations.yml` malformado: Cocina debe detenerse sin sobrescribir el archivo existente.
- [ ] Prueba ruta `stations.yml` sin permisos de escritura: nuevas interacciones de Cocina deben detenerse al detectar fallo.
- [ ] Prueba `activity-pending.yml` con sintaxis YAML inválida: cofres deshabilitados sin conceder premios.
- [ ] Compara MSPT/tiempo de guardado antes/después con número representativo de estaciones.

## Estado de validación local

- `tests/run_pure_java_tests.sh`: smoke tests y `AtomicFileStoreSmoke`.
- `tests/validate_presentation.py`: textos nuevos y YAML sin claves duplicadas.
- `tests/validate_release.py`: preservación de módulos, recetas y recompensas predeterminados.
- `tests/check_java_syntax.sh`: parser de Java 21.
- `MigrationRegression` y `CommandRoutingRegression`: regresiones de lógica y rutas.

**Futura fase:** transacciones de cocina/consumo, migración MiniMessage, /mago con Dialogs, modelos 3D con custom heads Base64 y adaptadores a plugins de Hydev.
