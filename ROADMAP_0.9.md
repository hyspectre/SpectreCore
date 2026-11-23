# SpectreCore 0.9 — Plan de desarrollo

**Estado:** en desarrollo. `0.9.0-dev2` es una entrega parcial, no una versión lista para producción.

## Dirección jugable

Construir conexiones útiles entre exploración, accesorios, profesiones, fabricación y cocina. Preferir acciones sencillas y recompensas existentes a nuevas monedas o estadísticas infladas.

### 0.9.0-dev1 — Hitos del Relicario (implementado en esta rama)

- Colecciones muestran `obtenidos/total` y el próximo objetivo.
- Hitos configurables (predeterminado 25 %, 50 % y 100 %).
- Cada página muestra su progreso; absorciones exitosas anuncian hitos.
- Los descubrimientos permanentes existentes son la única fuente de verdad: **sin migración de datos del jugador**.
- Sin premios materiales ni estadísticas adicionales: las celebraciones son informativas y cosméticas.
- Los menús antiguos conservan su geometría: el indicador busca un espacio libre si no tiene uno configurado.
- Configuración en `ui/relicario.yml`; pruebas en `CollectionMilestonesSmoke` y `validate_relicario_catalog.py`.

### 0.9.0-dev2 — Tablón de abastecimiento (implementado en esta rama)

- Accesible desde Trabajos o mediante `/spectrecore trabajos abastecimiento`.
- Consulta en vivo recursos existentes de minería, combate y tres niveles agrícolas. Su tipo e ID proceden de los YAML de tesoros, no de una segunda tabla de objetos.
- Cuenta los materiales MMOItems de la mochila sin retirarlos. Presenta el rango de oficio, el estado de probabilidades y los permisos para obtener hallazgos.
- No crea recetas, recompensas, créditos de profesión ni operaciones económicas. Los hallazgos sin probabilidades configuradas aparecen como no disponibles.
- El menú es de consulta, configurable en `ui/abastecimiento.yml`. Los menús de Trabajos personalizados antiguos pueden mostrar un acceso en una casilla libre sin modificar el archivo original; el comando sigue funcionando si el menú está lleno.
- Pruebas de contrato en `tests/validate_supply_board.py` y el pipeline de compilación.

### Próximas líneas (no implementadas)

1. **Recetas reales de Forja:** importar o verificar la configuración efectiva de fabricación/NPC, ausente en el repositorio de SpectreCore. Solo entonces vincular materiales con productos concretos; no inventar ingredientes ni duplicar la creación de objetos.
2. **Encargos culinarios:** objetivos que aprovechen las 15 recetas y sus calidades, con balance y transacciones seguras.
3. **Identidad de colecciones:** mostrar rutas verificadas de obtención de reliquias, sin anunciar drops inexistentes.

## Validaciones aplazadas por acuerdo hasta finalizar el desarrollo de 0.9

- Pruebas ingame en Paper (comportamiento, reinicios y persistencia).
- Fallos y conciliación de transacciones Vault/LuckPerms.
- Daño mágico atribuido correctamente con MythicMobs y MMOItems.

**Esas tres validaciones no bloquean desarrollar 0.9, pero sí su publicación en producción.** No declarar completa la versión mientras sigan pendientes.

## Publicación

Cada cambio de código o configuración se publica y verifica en GitHub. Las nuevas funciones se desarrollan en ramas independientes; no fusionar a `main` sin revisión ni sustituir una instalación en producción sin respaldo.
