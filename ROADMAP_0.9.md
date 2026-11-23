# SpectreCore 0.9 — Plan de desarrollo

**Estado:** en desarrollo. `0.9.0-dev1` es una entrega parcial, no una versión lista para producción.

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

### Siguientes líneas a evaluar (no implementadas)

1. **Vinculación de profesiones y forja:** determinar qué materiales de oficios existentes aportan a recetas ya disponibles. No crear una economía paralela ni duplicar funciones de MMOItems.
2. **Encargos culinarios:** objetivos que aprovechen las 15 recetas y sus calidades. Antes de establecer recompensas, revisar inventarios, persistencia y límites económicos.
3. **Identidad de colecciones:** mostrar rutas verificadas de obtención de reliquias, no anunciar drops que todavía no existen.

## Validaciones aplazadas por acuerdo hasta finalizar el desarrollo de 0.9

- Pruebas ingame en Paper (comportamiento, reinicios y persistencia).
- Fallos y conciliación de transacciones Vault/LuckPerms.
- Daño mágico atribuido correctamente con MythicMobs y MMOItems.

**Esas tres validaciones no bloquean desarrollar 0.9, pero sí su publicación en producción.** No declarar completa la versión mientras sigan pendientes.

## Publicación

Cada cambio de código o configuración se publica y verifica en GitHub. Las nuevas funciones se desarrollan en ramas independientes; no fusionar a `main` sin revisión ni sustituir una instalación en producción sin respaldo.
