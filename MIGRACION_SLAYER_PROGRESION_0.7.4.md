# HYDEV · Slayer: Monarquía Renacida — integración y QA de staging

## Alcance

En la rama de desarrollo 0.7.4 se incluyen XP Slayer separados de Fighting,
niveles 0–11, 16 permisos nuevos de equipamiento, accesos a contratos y menús
nativos con MiniMessage. **No desplegado en Hydev; el PR continúa como borrador.**

- Los objetivos y precios de Monarquía siguen siendo 20/50/90 zombis y
  200/500/950 ↾; la cancelación cuesta 200 ↾.
- XP Slayer por victoria propia confirmada: 25/100/250 (configurable en
  `modules/slayer/config.yml`). Nivel 1: 100 XP totales. La curva completa
  está allí con umbrales acumulados.
- El jefe de cada jugador se identifica por UUID y categoría. Invocarlo no
  completa la misión ni entrega XP. El golpe final del **contratante** es
  requisito provisional; el reparto por participación no se ha implementado.
- En caso de fallo al invocar, la cuota se conserva. Comando público:
  `/spectrecore slayer invocar revenant`. En el menú Revenant hay un botón
  de reintento; no acepta contratos sin la cuota, ni genera dos jefes activos.
- No se cuentan MythicMobs (esbirros incluidos) como bajas normales; la
  detección consulta `BukkitAPIHelper.isMythicMob(Entity)` y falla de forma
  restrictiva si la integración está activa pero no responde.

## Permisos ganados

Son **desbloqueos de uso**, no objetos gratis ni recetas nuevas. SpectreCore
concede y guarda estos nodos mediante LuckPerms. Configura las restricciones
equivalentes en las definiciones reales de MMOItems:

| Nivel | Tipo / ID MMOItems | Nodo de permiso |
| --- | --- | --- |
| 0 | Contrato Revenant I | `slayer.revenant.tier1` |
| 1 | SWORD/FILOPODRIDO | `hydev.slayer.item.sword.filopodrido` |
| 2 | ARMOR/ZOMBIEARMOR_1 | `hydev.slayer.item.armor.zombiearmor_1` |
| 2 | ARMOR/ZOMBIEARMOR_2 | `hydev.slayer.item.armor.zombiearmor_2` |
| 3 | ARMOR/ZOMBIEARMOR_3 | `hydev.slayer.item.armor.zombiearmor_3` |
| 3 | ARMOR/ZOMBIEARMOR_4 | `hydev.slayer.item.armor.zombiearmor_4` |
| 3 | Contrato Nigromante I | `slayer.necromancer.tier1` |
| 5 | SWORD/COLMILLO_PUTREFACTO | `hydev.slayer.item.sword.colmillo_putrefacto` |
| 5 | Contrato Revenant II | `slayer.revenant.tier2` |
| 6 | ARMOR/RENACIDO_1 | `hydev.slayer.item.armor.renacido_1` |
| 6 | ARMOR/RENACIDO_2 | `hydev.slayer.item.armor.renacido_2` |
| 7 | ARMOR/RENACIDO_3 | `hydev.slayer.item.armor.renacido_3` |
| 7 | ARMOR/RENACIDO_4 | `hydev.slayer.item.armor.renacido_4` |
| 8 | SWORD/SABLE_NECROTICO | `hydev.slayer.item.sword.sable_necrotico` |
| 9 | ARMOR/NECROTICO_1 | `hydev.slayer.item.armor.necrotico_1` |
| 9 | ARMOR/NECROTICO_2 | `hydev.slayer.item.armor.necrotico_2` |
| 10 | ARMOR/NECROTICO_3 | `hydev.slayer.item.armor.necrotico_3` |
| 10 | ARMOR/NECROTICO_4 | `hydev.slayer.item.armor.necrotico_4` |
| 10 | Contrato Revenant III | `slayer.revenant.tier3` |
| 11 | SWORD/NECROESPADA | `hydev.slayer.item.sword.necroespada` |

**Crítico:** conceder un nodo de LuckPerms NO impide usar un objeto MMOItems
que no esté configurado para requerir ese nodo. Habilita/verifica la restricción
de uso de objetos en la versión concreta de MMOItems, y añade cada nodo a su
respectiva definición de ítem **sin sobrescribir sus stats, lore, texturas o
habilidades**. MMOItems documenta la estadística «Item Permissions» para
establecer los requisitos de uso:
https://docs.phoenixdevt.fr/mmoitems/features/stats/stats.html

Los archivos reales de SWORD/ARMOR de MMOItems no están dentro de este repo:
**no se declara terminada la integración de uso hasta aplicarlos y probarlos**.

## Menús

`/spectrecore misiones`, `/spectrecore misiones monarquia` y
`/spectrecore misiones progresion` usan inventarios Java. Texto editable en
`ui/misiones.yml`, con comillas dobles y MiniMessage; los nombres/lore
desactivan cursiva por defecto. El lore se actualizó para Monarquía v2,
pero vida/daño y XP Slayer son una **copia configurable** (no lectura en vivo
de los archivos MythicMobs). Revisar después de cada rebalance.

## Limitaciones pendientes

1. El Tier I del Nigromante queda desbloqueado por nivel 3, pero falta decidir
   sus precios/objetivos, menú y configuración de compra. NO asumir que se
   puede contratar todavía por dinero.
2. El crédito de victoria sigue exigiendo el golpe final del dueño. Si otro
   jugador mata al jefe, no recibe XP Slayer. La alternativa por daño es
   una propuesta, no una regla aprobada.
3. Un UUID de jefe guardado puede seguir existiendo en un chunk descargado.
   No hay reinvocación automática cuando `Bukkit.getEntity(uuid)` da
   `null`: eso podría duplicar jefes vivos en chunks descargados.
   Debe definirse una recuperación administrativa y QA específica.
4. Los contratos no son una transacción ACID entre Vault y YAML.
   Respaldar estado y economía antes del primer despliegue.
5. Los objetos deben validar el nodo del jugador realmente en MMOItems,
   evitando permisos comodín o bypass de administrador durante QA.
6. Corroborar compatibilidad ingame con Paper 1.21.10, MythicMobs 5.13.0
   Free, LuckPerms y la versión exacta de MMOItems.

## Matriz mínima de pruebas en staging

- Jugador nivel 0 compra Revenant I; Revenant II/III rechazados.
- Eliminar zombies vanilla suma progreso; matar guardias invocados por
  MythicMobs no suma progreso.
- Al llegar a la cuota, aparece `ReyZombie1`. El mensaje dice
  «Jefe invocado», no «Misión completada». No hay XP Slayer todavía.
- Con el jefe activo, forzar nueva invocación o pulsar reintentar: no
  crea otro. Si la primera invocación falla, conserva cuota.
- Matar al jefe propio: +25 XP exactamente una vez. Matar un jefe
  invocado por un admin o ajeno: no entrega XP propio.
- Ganar 100 XP acumulados: sube al nivel 1, se guarda la XP, LuckPerms
  concede `hydev.slayer.item.sword.filopodrido`.
- Jugador antes/después del nivel 1 intenta usar FILOPODRIDO en
  MMOItems: solo la persona con permiso debería poder hacerlo.
- Reconectar y reiniciar staging: XP, nivel y permisos persisten.
- Nivel 3: Nigromante I como acceso; nivel 5: Revenant II;
  nivel 10: Revenant III; nivel 11: NECROESPADA.
- Verificar menús sin cursiva, textos MiniMessage, recompensas actualizadas
  y precio/objetivo dinámicos.
