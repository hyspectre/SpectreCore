# Relicario de Hydev — módulo jugable (v0.2)

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

## Instalación y activación
1. Añadir el tipo `RELIQUIA` a `plugins/MMOItems/item-types.yml` (fusionar, no sobrescribir).
2. Añadir los 32 IDs del archivo `RELIQUIA.yml` al archivo del tipo, una vez registrado; comprobar creación con `/mi browse`.
3. Instalar el nuevo SpectreCore. En `plugins/SpectreCore/config.yml`, ajustar `modules.accesorios.enabled: true` y reiniciar el servidor.
4. Abrir `/spectrecore relicario`, probar primero una reliquia de baja rareza y verificar el archivo `plugins/SpectreCore/data/relicario/<UUID>.yml`.
5. Hacer copia de seguridad antes de introducir objetos de valor. Si el almacenamiento falla, el consumo se bloquea.

Bonos de vida, movimiento y minería son atributos Paper; maná usa AuraSkills (requiere API cargada); daño, defensa, cosechas, cocina y recompensas de oficios tienen ganchos propios de SpectreCore. Las bonificaciones de magia y fuego sobre otros plugins de combate deben verificarse en staging, pues el motor podría no atribuir sus golpes al jugador. El efecto de alimentos requiere un evento de consumo real.

No hay cambios en la Forja, sus tres estaciones, los conjuntos ni los objetos
existentes de jugadores.
