# SpectreCore 0.8.2-stability-rc1 — cambios y migración

**Estado:** candidato de estabilización. Requiere prueba dentro de Paper 1.21.10 antes de producción.  
**Rama de desarrollo:** `fix/auditoria-estabilidad-20261122`.

## Cambios

- **Relicario:** todos los accesorios consumidos se acumulan simultáneamente, incluidos los que comparten familia. Los límites por atributo siguen aplicándose sobre el total. Se conservan identificadores y descubrimientos previos.
- **Catálogo de accesorios:** admite ampliar o reducir el número de entradas dentro de los límites configurados, con validación de espacios y navegación por categoría.
- **Combate:** se atribuye daño indirecto al jugador que lo causó cuando Paper expone esa información; requiere confirmar habilidades de MythicMobs/MMOItems dentro del juego.
- **Trabajos:** se registra cada compra en `data/jobs/purchases.yml` antes de cobrar. Si el servidor cae durante la concesión del rango, el registro queda pendiente de reconciliación en lugar de desaparecer. El archivo `jobs-data.yml` se carga estrictamente.
- **Slayer:** la lectura inválida de `slayer-data.yml` detiene el inicio del plugin sin sobrescribir el progreso corrupto.
- **Comandos:** El Mago se abre con `/spectrecore mago`; ya no existe un comando raíz `/mago`.
- **CI:** pruebas ajustadas para validar las reglas actuales, no la arquitectura anterior.

## Antes de instalar

1. Respalda la carpeta `plugins/SpectreCore/`, en especial `jobs-data.yml`, `slayer-data.yml` y `data/relicario/`.
2. Actualiza menús, NPC y enlaces que ejecuten `/mago` por `/spectrecore mago`. No elimines datos para actualizar.
3. Comprueba el resultado del workflow **Build SpectreCore** para el commit instalado.
4. En un servidor de pruebas, verifica absorción simultánea, cambios de estadísticas, combate físico/mágico, cocina, compras de contratos y permisos LuckPerms.

## Conciliación de compras de trabajos

- `/spectrecore trabajos transacciones`: lista los casos pendientes.
- `/spectrecore trabajos resolver <uuid>`: cierra un registro **solo después** de comprobar el saldo y el permiso en LuckPerms.
- Un registro pendiente bloquea nuevas compras para el jugador afectado hasta la revisión.
- No elimines `data/jobs/purchases.yml` para quitar bloqueos; reconcílialos, pues el sistema no puede garantizar si un proveedor externo ya cobró o concedió el rango durante una caída.

## Limitaciones

Las pruebas automatizadas no sustituyen un servidor Paper real. No se garantiza automáticamente que proyectiles y habilidades personalizadas atribuyan el causante del daño correctamente, ni que la economía externa garantice transacciones exactamente una vez. La integración a `main` debe realizarse después de validar esta rama y revisar los cientos de commits previos aún no fusionados.
