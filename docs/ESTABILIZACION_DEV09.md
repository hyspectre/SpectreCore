# Estabilización de dev09 — 26/11/2026

Base revisada: `1fdcf87` (primer parche de auditoría de OpenCode), descendiente de `018bf6a` y `73eb5c4`. Se mantiene 0.9.0-dev8: no es una release ni se ha desplegado en Paper. El [registro de auditoría](ESTADO_Y_AUDITORIA.md) conserva los hallazgos históricos; este documento indica el estado posterior.

| Hallazgo | Cambio realizado | Qué falta comprobar |
| --- | --- | --- |
| AUD-01 P0 | Carga estricta y transaccional de minería y cooldowns; rechazo de YAML corrupto, inválido o duplicado sin sobreescritura; guardado atómico y bloqueo de nuevas mutaciones ante error. | Datos reales, migración de cooldowns legacy, fallo de disco/reinicio; smoke de estado en Gradle. |
| AUD-02 P0 | Fusiona IDs ordinarios, entre comillas dobles o simples por identidad semántica; conserva campos personalizados no gestionados; valida duplicados, entradas y salida antes de aplicar. | Prueba en copia del catálogo efectivo. |
| AUD-03 P1 | Sincronización retroactiva respeta denegaciones directas, contextuales y el permiso efectivo de LuckPerms. | Perfiles offline/grupos/contextos; no revocar grants positivos históricos sin conocer su propietario. |
| AUD-04 P1 | Relicario v2 reserva transacción sin conceder bono; retira una reliquia, guarda recibo con datos del jugador y confirma. Recuperación con recibo; ambigüedad bloqueada para revisión. | Fallos reales de guardado/NBT y cierre abrupto de Paper en cada fase. |
| AUD-05 P1 | Geoda exige hito arcano, permiso de región en el objetivo real, clave válida y espacio para recompensa; pago/recompensa en el mismo inventario/PDC. | Pruebas de WorldGuard y geodas con raycast, llave apilada e inventario lleno. |
| AUD-06 P1 | Recupera rotura real bajo checks existentes de `MithrilRegionAccess.canBreak` y guardia de automatización, sin cobrar por una operación ficticia. | Regiones, flags, chunks, drops/XP y otros plugins de protección; no asumir equivalencia con eventos de rotura vanilla. |
| AUD-07 P2 | Probabilidad/duración de geodas y feedback/sonido de geoda/Forja configurables. | Resto de constantes jugables de gemas, revisión progresiva. |
| AUD-08 P2 | Cocina omite checkpoints periódicos sin cambios de reloj; conserva guardados de resets/transacciones. | Indexación de estaciones, medición con carga real. |
| AUD-09 P2 | Pasivas vuelven a reconciliar las mismas claves de atributos y hacen el barrido cada cinco ciclos del motor, sin quickTick duplicado. | Perfil con jugadores y otras habilidades. |
| AUD-10/11 P2 | No se cambia toda la arquitectura ni se elimina la GUI de El Mago. | Extraer responsabilidades gradualmente; Dialogs solo si mejoran UX. |

## Migración y recuperación (servidor detenido, sobre una copia)

1. Respaldar JAR, carpeta completa de SpectreCore, datos de jugadores, mundos y configuraciones integradas. Conservar copias fuera del servidor. No borrar datos por un error de carga.
2. `mining-data.yml` y `treasure-data.yml`: un archivo ausente permite iniciar datos nuevos; uno existente vacío/corrupto **bloquea** su sistema. Si un archivo vacío era realmente un estado inicial sin jugadores, verifícalo con los respaldos antes de convertirlo a `players: {}` o `cooldowns: {}`. Si había datos, recuperar la copia comprobada. No usar el `.bak` automáticamente: es el primer respaldo, no necesariamente el más reciente.
3. Relicario v1 sin pendientes conserva todos los IDs; se convierte a v2 al siguiente pago. Un v1 con `pending-withdrawal` conserva sus bonos históricos, pero impide nuevas absorciones hasta revisión. Un v2 con `pending.id` y `pending.token` no concede el bono pendiente; si el jugador tiene el recibo correspondiente, el servidor confirma al entrar. Si no hay recibo, **no reembolsar ni volver a consumir automáticamente**. Revisar inventario real y copias, decidir con el administrador si hubo pago; con el jugador desconectado y el servidor parado, reconciliar el ledger y hacer una copia previa. En v2: añadir a `absorbed` únicamente cuando el consumo esté confirmado; eliminar `pending` tras resolver. En v1: retirar `pending-withdrawal` solo tras verificar los IDs. El API `Player.saveData()` no expone confirmación de fsync ni prueba de durabilidad física, por lo que la validación de caída en Paper sigue siendo obligatoria.
4. Menú El Mago: mantiene el formato `pages` + `visual`, los libros/categorías anteriores y navegación por IDs libres. Sitúa entradas nuevas bajo `pages.<id>`; enlaza mediante `buttons[].page`. El icono de una página puede ser un material válido como `BOOK`, la textura Base64 de Minecraft anterior o `""` para el predeterminado, bajo `visual.iconos.<id>`. No requiere Java. El formato experimental `mago.sections` del parche anterior omitía destinos y contenido: si se instaló, conservar el original y recuperar la plantilla empaquetada de `ui/mago.yml`; trasladar manualmente solo ediciones intencionales al esquema actual. No hay conversión automática que elimine páginas.
5. Instalar PyYAML en el entorno de Python y ejecutar el instalador minero **sin** `--aplicar` primero. Revisar diferencias y respaldar; no ejecutar con el servidor en marcha. Campos existentes explícitamente definidos por la nueva integración siguen el paquete; campos ajenos se conservan. YAML ambiguo, duplicado o con sintaxis de entradas no soportada se rechaza antes de escribir.
6. Ejecutar el flujo del [README](../README.md) y el checklist de [certificación](ESTADO_Y_AUDITORIA.md#puerta-de-release). No etiquetar como estable sin pruebas reales del servidor.

## Pruebas específicas

- `tests/test_instalador_mineria.py`: IDs con y sin comillas; modelo personalizado; rechazo de duplicados; dry run, respaldo, idempotencia y recetas.
- `tests/StateRecoverySmoke.java`: invocado por `gradle check`; prueba ausencia, corrupción, duplicados, transacción pendiente, reinicio, recibo incorrecto/valido, legado en cuarentena.
- `tests/validate_native_mago.py`: catálogo completo, enlaces y soporte de materiales y texturas.
- Pruebas ingame pendientes: crear geoda con hito y sin hito, región negada y permitida, selección agachado, pago especial e inventario lleno; taladros en límite de región y chunks; apagar Paper antes/después de cada fase de absorción; confirmar que bonos se aplican exactamente una vez; recargar con datos corruptos y verificar integridad byte por byte.


## Protección adicional del diario compartido

`ClaimJournal` (cocina, compras de trabajos, abastecimiento y taller minero) utiliza la misma carga estricta. Una copia existente vacía, sin versión reconocida, con claves duplicadas o campos inválidos se bloquea sin permitir nuevas transacciones. Los antiguos diarios vacíos con `schema-version: 1` siguen siendo válidos; nuevos archivos guardan también `claims: {}` cuando no hay pendientes. Restaurar un diario dañado únicamente desde un respaldo verificado, nunca limpiarlo para resolver un error. Los casos se añaden a `PlayerTransactionJournalSmoke`.

## Indicador de resonancia de herramientas

La barra de jefe muestra la carga de la herramienta **en la mano principal**, incluso vacía.
El Pico Perdido mantiene su carga por jugador; Mitrilo, Mitrilo Templado, Amatista,
Brújula Geológica, Farol y Barrena muestran su carga persistente por objeto, el estado
de recarga y el control de su habilidad. No se modifican costos, carga, permisos ni
datos guardados. La barra desaparece al cambiar a herramientas sin resonancia.
La Amatista distingue carga insuficiente, campo parcial posible y carga completa
(45 y 75 con los valores iniciales); el espacio disponible determina el costo real.
«Carga lista» no garantiza un objetivo válido ni una región permitida.
Los taladros de piedra mantienen su excavación automática, sin carga de resonancia.
`/spectrecore resonancia` consulta también el estado del dispositivo sostenido.
Al instalar el JAR, los textos nuevos se cargan con los valores por defecto sin
reemplazar la configuración personalizada. Verificación ingame: alternar entre
dos picos con cargas distintas y un pico normal; cargar, activar y esperar
la recarga de cada habilidad, comprobar nivel insuficiente y campo de amatista.
