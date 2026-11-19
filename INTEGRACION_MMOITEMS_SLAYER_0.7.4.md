# Hydev · MMOItems y progresión Slayer (0.7.4)

## Objetivo

**Desbloquear el uso del equipo, no regalarlo.** La XP Slayer y los niveles 0–11 de
Monarquía Renacida se almacenan en SpectreCore; el puente LuckPerms concede
`hydev.slayer.item.<tipo>.<id>` de forma persistente al alcanzar cada nivel.

Para que MMOItems **impida realmente su uso antes del desbloqueo** hay que añadir
una entrada **`base.permission`** en cada objeto. Es la estadística nativa
«Item Permissions», NO `granted-permissions` (que otorgaría permisos al equipar
el ítem y podría crear una escalada de privilegios).

Referencia oficial de la sintaxis:
https://gitlab.com/phoenix-dvpmt/mmoitems/-/wikis/Config-Format-of-Stats/diff?version_id=f5f2ae97e71c1fb31a78411b43d41e08a9c3e7e2
Esa documentación ejemplifica:
```yaml
EJEMPLO:
  base:
    permission:
    - "hydev.slayer.item.sword.filopodrido"
```

## Instalación (staging primero)

1. Detén tu servidor de pruebas. Guarda una copia íntegra de
   `plugins/MMOItems`, `plugins/SpectreCore` y la base de datos LuckPerms.
2. Confirma en `plugins/MMOItems/config.yml`:
   ```yaml
   permissions:
       abilities: false
       items: true
   ```
   Puede haber otras opciones entre estas líneas; no las borres.
3. Previsualiza, sin cambiar nada:
   ```bash
   python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems
   ```
4. Aplica la integración **solo si aparecen los 16 IDs**. El programa crea
   `plugins/MMOItems/backups/spectrecore-slayer-<fecha>/SWORD.yml` y
   `ARMOR.yml` automáticamente:
   ```bash
   python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems --apply
   ```
5. Comprueba los permisos sin realizar cambios:
   ```bash
   python3 tools/apply_slayer_mmoitems_permissions.py --mmoitems-dir plugins/MMOItems --check
   ```
6. Inicia staging, recarga MMOItems mediante el mecanismo oficial para tu
   versión y comprueba que los objetos existentes y los recién generados
   exigen el permiso. SpectreCore no necesita conceder permisos a mano:
   `SlayerUnlockBridge` sincroniza lo ganado con LuckPerms.

**Importante:** el instalador modifica los **YAML del servidor** cuando se
ejecuta con `--apply`, no el repositorio de código. Sin ese paso, los nodos
de LuckPerms existen pero no hay una restricción nativa en MMOItems.

## Matriz completa

| Nivel | Item | Permiso |
|---|---|---|
| 1 | SWORD/FILOPODRIDO | `hydev.slayer.item.sword.filopodrido` |
| 2 | ARMOR/ZOMBIEARMOR_1 | `hydev.slayer.item.armor.zombiearmor_1` |
| 2 | ARMOR/ZOMBIEARMOR_2 | `hydev.slayer.item.armor.zombiearmor_2` |
| 3 | ARMOR/ZOMBIEARMOR_3 | `hydev.slayer.item.armor.zombiearmor_3` |
| 3 | ARMOR/ZOMBIEARMOR_4 | `hydev.slayer.item.armor.zombiearmor_4` |
| 5 | SWORD/COLMILLO_PUTREFACTO | `hydev.slayer.item.sword.colmillo_putrefacto` |
| 6 | ARMOR/RENACIDO_1 | `hydev.slayer.item.armor.renacido_1` |
| 6 | ARMOR/RENACIDO_2 | `hydev.slayer.item.armor.renacido_2` |
| 7 | ARMOR/RENACIDO_3 | `hydev.slayer.item.armor.renacido_3` |
| 7 | ARMOR/RENACIDO_4 | `hydev.slayer.item.armor.renacido_4` |
| 8 | SWORD/SABLE_NECROTICO | `hydev.slayer.item.sword.sable_necrotico` |
| 9 | ARMOR/NECROTICO_1 | `hydev.slayer.item.armor.necrotico_1` |
| 9 | ARMOR/NECROTICO_2 | `hydev.slayer.item.armor.necrotico_2` |
| 10 | ARMOR/NECROTICO_3 | `hydev.slayer.item.armor.necrotico_3` |
| 10 | ARMOR/NECROTICO_4 | `hydev.slayer.item.armor.necrotico_4` |
| 11 | SWORD/NECROESPADA | `hydev.slayer.item.sword.necroespada` |

## Seguridad y conservación

- `--apply` **falla sin tocar ningún archivo** si falta un ID, hay duplicados,
  no hay una sección `base` o la opción global `permissions.items` no está
  habilitada.
- Los valores `permission` ya existentes se conservan; el nodo Slayer se
  añade como requisito **adicional**. No se eliminan otros requisitos.
- La escritura es atómica con respaldo y recuperación ante fallo parcial.
- Se preservan los demás campos y comentarios: el script inserta líneas, sin
  serializar/desarmar todo el YAML.
- La opción `mmoitems.bypass.item` omite restricciones de uso: comprueba en
  LuckPerms que los grupos de jugadores ordinarios **no** tengan ese bypass.
- La documentación encontrada de Hydev confirma los IDs de las armaduras,
  pero los archivos completos de espadas en producción **no han sido leídos**.
  Por eso la integración exige comprobar los 16 IDs en el servidor de pruebas.

## QA

Prueba con dos jugadores sin OP ni bypass: uno nivel 0 y otro con los niveles
Slayer correspondientes. Verifica espada en mano, ataque/skills, armadura
equipada, cambio de armadura, objeto recién generado y objeto existente
(actualizar su revisión con MMOItems si hace falta). Prueba también al
reconectar y después del reinicio. Si un item permite su uso sin permiso,
verifica `permissions.items`, el bypass y el requisito del propio objeto.

CI ejecuta `tests/test_slayer_mmoitems_permissions.py` para comprobar
16 IDs, paridad con los permisos Java, respaldo, idempotencia, permisos
preexistentes, CRLF, ausencia de ID y falta de habilitación global.

**No modificar producción hasta pasar la revisión ingame de staging.**
