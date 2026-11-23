# SpectreCore 0.9 — Plan de desarrollo

**Estado:** en desarrollo. `0.9.0-dev4` es una entrega parcial, no una versión lista para producción.

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

### 0.9.0-dev3 — Mitrilo y Pedido del Herrero (implementado en esta rama)

- Mitrilo: recurso `MATERIAL:MITRILO` en vetas mineras T2/T3, accesible mediante contrato minero Experimentado.
- Dos nuevos picos `TOOL` definidos en MMOItems; mejoran la obtención de Mitrilo cuando se equipan.
- Recetas de la Forja existentes: se proporcionan fragmentos, a incorporar tras verificar el archivo de estación real (no se duplica la Forja).
- Pedido repetible de 4 Mitrilo por 180 monedas; el jugador elige entre reservar el mineral para herramientas o venderlo a la Forja.
- Retiro y pago con bitácora persistente; transacciones ambiguas quedan para reconciliación administrativa.
- Pruebas estáticas, integración de configuración, tests de cantidades y CI. **No representa prueba ingame ni instalación automática de MMOItems.**

### 0.9.0-dev4 — Herramientas especializadas de Mitrilo (implementadas en esta rama)

- **Pico de Mitrilo:** mantiene la bonificación pasiva para hallar Mitrilo y añade una habilidad de vetas conectadas, con hasta tres bloques adicionales por extracción y consumo de energía/durabilidad.
- **Brújula Geológica:** pulso regional limitado, señales de intensidad y orientación durante 8 segundos. No revela coordenadas exactas ni carga chunks.
- **Farol de Mitrilo:** visión nocturna básica gratuita y sendero de retorno anclado en el ítem, con límite espacial y marcas temporales.
- **Barrena de Mitrilo:** túnel 1×2 de hasta 16 bloques, un bloque cada 0,25 segundos, solo en roca de lista blanca.
- **Cristal de Mitrilo:** recarga 50 de los 100 puntos máximos de resonancia almacenados individualmente en cada herramienta.
- Las habilidades respetan la protección WorldGuard verificada; sin ella, las lecturas y excavaciones sensibles se bloquean. La minería automática no incrementa botines ni pagos por extracción manual.
- Configuración y textos en YAML, cinco nuevos IDs de MMOItems, fragmentos para la Forja existente y pruebas de regresión.

**Pendiente fuera del repositorio:** fusionar los ítems/recetas con MMOItems y comprobar en el servidor la persistencia de datos del ítem, la protección de regiones, la durabilidad, los controles y las partículas. No hay nueva estación de forja.

### Próximas líneas (no implementadas)

1. **Forja real:** fusionar las dos recetas de Mitrilo en la estación MMOItems que abre el NPC. Su archivo real todavía no está en el repositorio, por lo que la instalación sigue pendiente.
2. **Encargos culinarios:** objetivos que aprovechen las 15 recetas y sus calidades, con balance y transacciones seguras.
3. **Identidad de colecciones:** mostrar rutas verificadas de obtención de reliquias, sin anunciar drops inexistentes.

## Validaciones aplazadas por acuerdo hasta finalizar el desarrollo de 0.9

- Pruebas ingame en Paper (comportamiento, reinicios y persistencia).
- Fallos y conciliación de transacciones Vault/LuckPerms.
- Daño mágico atribuido correctamente con MythicMobs y MMOItems.

**Esas tres validaciones no bloquean desarrollar 0.9, pero sí su publicación en producción.** No declarar completa la versión mientras sigan pendientes.

## Publicación

Cada cambio de código o configuración se publica y verifica en GitHub. Las nuevas funciones se desarrollan en ramas independientes; no fusionar a `main` sin revisión ni sustituir una instalación en producción sin respaldo.
