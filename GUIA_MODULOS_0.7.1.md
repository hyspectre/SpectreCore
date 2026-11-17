# SpectreCore 0.7.1-alpha — configuración modular

Java 21 · Paper 1.21.10. Esta versión reorganiza la configuración; no añade nuevas mecánicas.

| Módulo | Mecánicas | Archivos dentro de plugins/SpectreCore/ |
|---|---|---|
| equipamiento | Botania, Necrótica, Asterión, Último Juramento, Sangre del Primer Dragón y libro de armaduras | modules/equipamiento/config.yml |
| cocina | Calderos, ingredientes visibles, líquidos animados, calidad, porciones, inspección y libro de recetas | modules/cocina/config.yml y recetas.yml |
| tesoros | Hallazgos de minería, almas de combate, agricultura y cooldown compartido | modules/tesoros/config.yml, mineria.yml, combate.yml y agricultura.yml |
| resonancia | Carga del Pico Perdido, ventana de extracción y calidad de gemas | modules/resonancia/config.yml |
| trabajos | Minero, granjero, cazador, salarios y cooldowns | modules/trabajos/config.yml |
| slayer | Activación, progreso y recompensas de misiones | modules/slayer/config.yml |
| dungeons | Estados de daño de jefes, bloqueos de fases y portal de extracción | modules/dungeons/config.yml, jefes.yml y portal.yml |
| actividades | Cygnus, cofre aleatorio, polvos, desbloqueos, teléfono y protección al entrar | modules/actividades/config.yml |

Los comandos administrativos, diagnóstico e inspector /nombreitem pertenecen al núcleo.

## Uso

config.yml es el índice: modules.<nombre>.enabled activa cada categoría. Los ajustes están en modules/. Tesoros, equipamiento, trabajos, dungeons y actividades también ofrecen interruptores de subsistemas.

- /spectrecore modulos: muestra estados activos y cambios pendientes de reinicio.
- /spectrecore reload: recarga parámetros. Si hay YAML inválido, conserva la última configuración válida.
- Reinicia el servidor para aplicar cambios de enabled. No uses /reload de Bukkit.

La resonancia y los tesoros de minería se pueden activar independientemente. Desactivar una categoría desactiva sus subsistemas.

## Actualizar desde 0.7.0

1. Detén el servidor y conserva una copia de plugins/SpectreCore/.
2. Reemplaza el JAR anterior por SpectreCore-0.7.1-alpha.jar. Evita dejar dos versiones.
3. Inicia el servidor. Se crean los archivos modulares importando los valores anteriores cuando corresponda.
4. Revisa /spectrecore modulos y los archivos modules/.

Antes de modificar el índice antiguo se guardan copias en backups/configuration-v1/. Los antiguos archivos planos se conservan, pero dejan de ser la configuración activa después de migrar. Edita los archivos modules/ desde entonces. Los archivos modulares existentes no se sobrescriben con valores antiguos.

Se conservan salarios, probabilidades, IDs y recetas configurados. No se inventan salarios ni probabilidades ausentes. Las variables persistentes de Skript no se importan automáticamente.

Los datos de jugadores, estaciones, descubrimientos, minerales colocados y premios pendientes mantienen sus rutas: cooking/stations.yml, cooking/discoveries.yml, mining-data.yml, treasure-data.yml, jobs-data.yml, slayer-data.yml y activity-pending.yml; también se conservan las marcas PDC de chunks.

CONFIGURACION_EJEMPLO contiene valores de referencia: no la copies encima de una instalación con ajustes propios.

## Validación y alcance

Compilación completada, 98.499 comprobaciones de reglas, 24 comprobaciones de migración y validación de los ocho módulos y catorce archivos. Falta prueba de integración en Paper con tus plugins; el comando de salida de Dungeons+ debe verificarse en tu servidor.

Las estaciones y minijuegos nuevos de cocina, comercio y encargos culinarios, así como Gastronomía y Arcana integradas en AurelliumSkills, no se implementan en esta versión.
