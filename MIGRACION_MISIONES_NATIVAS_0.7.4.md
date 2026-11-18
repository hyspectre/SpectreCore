# Misiones 0.7.4 — Interfaz nativa de SpectreCore

## Qué cambió

Los dos menús entregados por la administración (zMenu «Misiones» y «Hordas») son ahora **inventarios Java de Paper**, sin dependencia runtime de zMenu para esta interfaz. Se conservaron las cabezas personalizadas Base64, gran parte de los textos originales, los slots de los 54 espacios y la información de Príncipe / Rey / Emperador.

La navegación comparte título y encabezados:
- `/spectrecore misiones` — índice de cacería y Teléfono Rojo.
- `/spectrecore misiones monarquia` — contratos de Monarquía Renacida / Revenant.

El índice muestra las otras categorías de origen (esqueletos, arañas, amenazas futuras) como **vistas informativas no interactivas**, igual que la versión fuente: no están implementados sus submenús ni precios.

## Configuración editable

`plugins/SpectreCore/ui/misiones.yml` — creado desde el recurso incluido al primer arranque. Contiene las dos páginas `pages.misiones` y `pages.revenant`, cada una con:
- `title`, `size: 54`
- `items.<id>.slot` (0..53), `texture` (Base64 SKIN), `name` y `lore`.

**No se acepta ninguna acción, permiso ni comando arbitrario en el YAML visual.** Los botones se mapean mediante IDs internos del código Java, no por nombres o textos visibles. Se validan las dos páginas antes de reemplazar la configuración cargada, evitando aplicar una modificación parcial. Se admite `/spectrecore reload` para actualizar la presentación **después de instalar esta versión**.

Se conservan los formatos legacy `&a`, `&l` y `&#RRGGBB`. El código usa `CustomHeads` de SpectreCore; si una textura es inválida, muestra un libro y registra aviso.

## Acciones nativas y economía

| Página | Botón | Acción |
|---|---|---|
| Misiones | Monarquía (ID 1, slot 13) | Abre página Revenant |
| Misiones | Teléfono Rojo (slot 50) | `SlayerModule.purchasePhone` con Vault confirmado y entrega MMOItems |
| Misiones | Cerrar (slot 48) | Cierra inventario |
| Monarquía | Rangos I, II, III (slots 21, 23, 25) | `SlayerModule.playerContract` validado, permiso, misión única y retiro Vault confirmado |
| Monarquía | Abandonar (slot 53) | Valida misión activa, tarifa y retiro Vault confirmado |
| Monarquía | Volver (slot 48) | Abre índice Misiones |
| Monarquía | Cerrar (slot 50) | Cierra inventario |

Los clics se procesan desde `InventoryClickEvent` y todos los movimientos y arrastres que interfieran con la UI se cancelan. No se otorgan permisos OP al jugador y no se llaman comandos de zMenu para ninguna acción.

**Se mantienen los precios:** Revenant 200/500/950 ↾, abandono 200 ↾, Teléfono Rojo 500 ↾.
**Se mantienen los objetivos:** 20/50/90 zombis y los requisitos del lore 7/10/14 de combate (la validación real de acceso se hace por los permisos `slayer.revenant.tier1/2/3` como en el sistema existente).

## Puertas de activación (staging)

En `plugins/SpectreCore/modules/slayer/config.yml`:
```yaml
contratos:
  habilitados: false
acceso-remoto:
  habilitado: false
```

Los menús pueden abrirse **aunque los contratos estén desactivados**, pero ningún botón cobra ni contrata hasta que un administrador habilite esas opciones tras la instalación. Los flags admiten `/spectrecore reload`.

**No despliegues los menús zMenu antiguos simultáneamente con compras activas**: podrían seguir ejecutando su propio `withdraw` o `player-command-as-op` si alguien los abre por un enlace antiguo. No es necesario eliminar zMenu del servidor si se usa para otras interfaces, pero los accesos de NPC y el objeto `TELEFONO1` deben migrarse a `/spectrecore misiones` cuando se confirme su configuración.

## QA para la única ventana de mantenimiento

1. Respaldar `plugins/SpectreCore/`, JAR y YAML zMenu antes del cambio.
2. Con el JAR nuevo y el flag de menú habilitado, ejecutar `/spectrecore misiones`. Verificar título, cabezas, colores HEX, 54 slots, textos y botones bloqueados de otras categorías.
3. Clic en Monarquía; comprobar volver/cerrar e información detallada de los 3 rangos.
4. Verificar que inventario del jugador no admita movimientos/clics shift que extraigan decoraciones de la UI.
5. Con `contratos.habilitados: false` y `acceso-remoto.habilitado: false`, comprobar que cada botón rechaza acciones **sin cobrar**.
6. Desactivar/migrar enlaces viejos de zMenu desde NPCs y `TELEFONO1` antes de activar cobros nuevos. Comprobar comportamiento del teléfono real (la configuración de ese objeto no fue entregada).
7. En staging con Vault confirmado, activar `contratos.habilitados: true`, recargar y comprobar permisos, saldo insuficiente, misión ya activa, contrato correcto, cancelación y ausencia de doble cobro.
8. Probar compra del teléfono con inventario lleno, MMOItems desactivado y saldo insuficiente; no debe cobrar si falla la validación.
9. Cambiar un título de `ui/misiones.yml`, recargar y comprobar que afecta solo presentación. Probar un YAML con slots duplicados: debe conservar la versión válida anterior.
10. Validar persistencia de `slayer-data.yml` tras el reinicio de mantenimiento; verificar que los jefes invocados siguen siendo `ReyZombie{tier}`.

## Limitaciones honestas

- Compilación, YAML y pruebas de rutas son automáticas; la apariencia y compatibilidad de inventario deben verificarse **ingame**.
- El motor económico actual intenta revertir fallos de almacenamiento, pero no ofrece garantía ACID completa ante apagones entre cobro y guardado. No afirmar atomicidad de Vault+YAML.
- Las categorías Nigromante y Araña siguen siendo «próximamente», tal como los originales. No se inventaron sus precios ni objetivos visuales.
- Los textos y estadísticas de boss (vida, daño, loot y XP) siguen siendo información configurada en el menú, no una lectura dinámica de MythicMobs; revisar si el balance real cambia.
