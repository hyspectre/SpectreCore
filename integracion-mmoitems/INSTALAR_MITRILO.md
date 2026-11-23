# Integración del Mitrilo — Hydev / SpectreCore 0.9.0-dev3

## Identidad y progresión

**Mitrilo** es un recurso único de MMOItems: `MATERIAL:MITRILO`. Se obtiene como hallazgo de minería, sin generar bloques adicionales ni sustituir Paladio.

- Menas T2 (Redstone y Lapislázuli): **1 %** de hallazgo por bloque elegible.
- Menas T3 (Diamante, Esmeralda y otras clasificadas): **2 %** de hallazgo.
- Requiere contrato de **Minería Experimentado** o superior; el botín funciona desde rango 2.
- Obtienes **1 o 2 unidades**, compartiendo el tiempo de espera de tesoros mineros.
- El Pico de Mitrilo multiplica estas probabilidades por **1,25**; el Pico de Mitrilo Templado, por **1,60**.
- El Mitrilo se entrega antes de evaluar el tesoro minero habitual: nunca se dan dos recompensas del mismo bloque.

Las probabilidades y cantidades están en `modules/tesoros/mineria.yml`. Los rangos, precios y recompensas se regulan en `modules/trabajos/config.yml`. Si necesitas ajustar balance, edita YAML y utiliza el mecanismo habitual de recarga de SpectreCore.

## Objetos MMOItems

Los archivos `integracion-mmoitems/MATERIAL_MITRILO.yml` y `TOOL_MITRILO.yml` **son fragmentos para fusionar con tipos existentes**:

1. Respalda `plugins/MMOItems/item/MATERIAL.yml` y `plugins/MMOItems/item/TOOL.yml`.
2. Añade la entrada `MITRILO:` del primer fragmento a `MATERIAL.yml` **sin borrar el resto de minerales**.
3. Añade `PICO_MITRILO:` y `PICO_MITRILO_TEMPLADO:` a `TOOL.yml`.
4. Recarga MMOItems con el comando administrativo apropiado y verifica que se creen los tres objetos.
5. **No crees tipos MMOItems nuevos:** se reutilizan `MATERIAL` y `TOOL`.

Los picos poseen materiales base DIAMOND_PICKAXE y NETHERITE_PICKAXE. Los multiplicadores especiales los aplica SpectreCore al verificar los IDs reales de MMOItems, nunca por el nombre o lore del objeto.

## Fabricación mediante la Forja existente

`RECETAS_FORJA_MITRILO.yml` contiene dos entradas compatibles con la sección `recipes:` de una estación de fabricación MMOItems:

- **Pico de Mitrilo:** 12 Mitrilo, 6 lingotes de hierro y 2 palos.
- **Pico de Mitrilo Templado:** 24 Mitrilo, 8 diamantes y 1 Pico de Mitrilo.

**Importante:** el archivo de la estación que abre el NPC Forja no forma parte de este repositorio. Este fragmento no se instalará automáticamente. Localiza el YAML de la estación real, fusiona ambas recetas con su sección `recipes:` y conserva los demás productos. Revisa el formato según la versión concreta de MMOItems. No crees una segunda Forja.

## Pedido del Herrero

Desde `/spectrecore trabajos abastecimiento` se puede entregar **4 Mitrilo por 180 monedas** de manera repetible, con contrato de Minería Experimentado. El sistema identifica objetos por tipo/ID MMOItems y retira exactamente la cantidad solicitada del inventario principal. No se puede entregar material renombrado.

- Antes de retirar materiales, SpectreCore guarda la operación en `data/jobs/purchases.yml`.
- Si se confirma el pago Vault, cierra la transacción. **No se usa consola para simular pagos exitosos.**
- Si algo falla entre el retiro y el pago, **no** reembolsa automáticamente una operación ambigua; queda pendiente para administración, evitando pago duplicado.
- `/spectrecore trabajos transacciones` permite consultar los casos, y `/spectrecore trabajos resolver <uuid>` cerrarlos **solo tras verificar inventario, monedas y permisos**.

## Estado de validación

Esta entrega se prueba mediante GitHub Actions (sintaxis, Java, YAML, reglas y código de transacciones). Permanecen aplazadas las pruebas completas dentro de Paper, fallos simulados Vault/LuckPerms y daño mágico MythicMobs/MMOItems, según el acuerdo para cerrar 0.9.

**No instalar directamente en producción ni fusionar sobre archivos existentes sin respaldo.**
