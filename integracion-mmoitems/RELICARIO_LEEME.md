# Relicario de Hydev — fase de catálogo (v0.1)

Este commit prepara el catálogo y la lógica de acumulación, pero **NO instala
todavía la absorción de accesorios ni aplica bonificaciones al jugador**.

## Estado y ubicación
- `src/main/resources/modules/accesorios/catalogo.yml`: 32 reliquias, 17 estadísticas y límites.
- `src/main/resources/modules/accesorios/config.yml`: políticas de seguridad.
- `src/main/resources/ui/relicario.yml`: índice, cuatro colecciones y confirmación.
- `integracion-mmoitems/ITEM_TYPE_RELIQUIA.yml`: entrada para agregar a
  `plugins/MMOItems/item-types.yml` cuando el módulo jugable esté listo.
- `integracion-mmoitems/RELIQUIA.yml`: 32 objetos para el tipo personalizado,
  **sin stats equipables**. No sustituye otros archivos de MMOItems.
- `RelicarioRules.java`: cálculo puro de familias y bonos, con pruebas.

## Regla de progreso
- Todos los accesorios de familias **distintas** se acumulan simultáneamente.
- En una misma familia solo aporta el accesorio del mayor rango registrado.
- Los IDs antiguos permanecen descubiertos para el catálogo.
- Los topes se aplican por estadística al total de todas las familias.
- El registro se calculará desde IDs absorbidos, no desde valores incrementados.
- Absorber el mismo ID dos veces estará prohibido.
- El consumo confirmará y verificará el objeto MMOItems real, nunca su nombre.

## Familias (9)
- Brote Marchito → Núcleo Silvestre
- Medallón Agrietado → Medallón Profano
- Cuerno Astillado → Marca del Laberinto
- Grillete del Deshonrado → Anillo del Último Voto
- Sello del Prospector → Ojo del Prospector
- Dedal de la Fortuna → Espiga de Ámbar → Cornucopia de Ámbar
- Colmillo del Acechador → Silbato del Cazador
- Brújula Extraviada → Rosa de los Vientos
- Espejo de Escarcha → Espejo del Vacío

## Antes de habilitar
Falta implementar almacenamiento durable por UUID, interacción de confirmación,
y recálculo al iniciar/cerrar sesión. También falta conectar los 17 tipos de
bonificación con Paper, MythicLib y los sistemas de Cocina/Trabajos/Caza
según corresponda. En particular, maná solo tiene efecto si el servidor
cuenta con un proveedor de maná activo.

**No activar** `modules.accesorios.enabled` ni
`modules/accesorios/catalogo.yml: enabled` todavía; las configuraciones
actuales se distribuyen con ambos interruptores en `false`.

No hay cambios en la Forja, sus tres estaciones, los conjuntos ni los objetos
existentes de jugadores.
