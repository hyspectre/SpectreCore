# SpectreCore · MiniMessage global (rama 0.7.4)

## Archivos y alcance

La presentación configurable de los nueve módulos utiliza MiniMessage y
valores de texto entre **comillas dobles** en los recursos incluidos en el JAR:

- `modules/{actividades,cocina,dungeons,equipamiento,nucleo,resonancia,slayer,tesoros,trabajos}/mensajes.yml`
- `ui/{misiones,trabajos,mago}.yml`
- Textos de `modules/nucleo/comandos.yml`, nombres de
  `modules/cocina/recetas.yml` y `models/ejemplo.yml`
- Títulos/descripciones de los toasts de logros: los componentes se serializan a
  Adventure JSON antes de registrar el advancement de Minecraft.

**No migrar como mensajes** IDs de MMOItems, MythicMobs, comandos ejecutables,
UUID, materiales, nodos de permiso ni estructuras de configuración; deben
seguir siendo valores de máquina.

## Renderizado central

`TextService.component(String)` usa MiniMessage y traduce en memoria los
códigos antiguos `&` y `§` (colores, decoraciones, reset y hexadecimales).
`Settings.message(...)` devuelve `Component` para chat.
`Settings.plain(...)` devuelve texto plano para logs.

Los parámetros `{nombre}` de mensajes se sustituyen mediante
`Placeholder.unparsed` (no se evalúan etiquetas de usuarios). Cuando un
valor está explícitamente marcado como `Component`, se inserta con
`Placeholder.component` para conservar formatos internos fiables.

Las piezas de menús de inventario usan
`.decoration(TextDecoration.ITALIC, false)` en nombres y lore. Los títulos de
inventario no necesitan esta regla especial.

## Actualización de instalaciones existentes

En `ModuleConfiguration.reload()`, los **valores de texto** de cada
`modules/*/mensajes.yml` existente se migran a MiniMessage solo cuando
contienen códigos antiguos. Antes de guardar por primera vez se copia el YAML
original a `backups/minimessage-v1/modules/<modulo>/mensajes.yml`.

La migración conserva las claves nuevas, claves personalizadas y textos
editados por admins, sin reponer valores por defecto. Es idempotente y deja
legibles los archivos antiguos si no se ha instalado el JAR actualizado.

Las interfaces existentes `ui/misiones.yml` y `ui/trabajos.yml` no se
reescriben automáticamente: el renderizador acepta sus colores legacy y
mantiene personalizaciones. Para tener la sintaxis MiniMessage en disco,
aplicar la nueva plantilla con respaldo de los menús instalados.

## Ejemplo

```yaml
titulo: "<gold><bold>Monarquía Renacida</bold></gold>"
mensaje: "<gray>Recompensa: <green>{dinero} ↾</green></gray>"
```

No confundir **comillas YAML** con la opción de cursiva del cliente Minecraft:
son problemas distintos.

## Calidad y despliegue

- `tests/validate_presentation.py` comprueba los valores globales
  MiniMessage, claves, tipos, comillas y paridad con fallbacks.
- `PresentationConfigSmoke` verifica la migración con respaldos,
  renderizado heredado, componentes y protección ante etiquetas inyectadas.
- Ejecutar GitHub Actions y `gradle clean build`; aún se requiere prueba
  de títulos, chat, toasts y menús dentro de Hydev.

No desplegar sobre el servidor activo sin respaldar `plugins/SpectreCore`,
comprobar que los ajustes son válidos y retirar las rutas de zMenu que
cobren el mismo contrato dos veces.
