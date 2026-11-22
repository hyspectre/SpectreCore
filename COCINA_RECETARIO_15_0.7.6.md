# Cocina 0.7.6 · Expansión de 15 recetas (rama de pruebas)

**Esta ampliación está en la rama de desarrollo; no fusionar ni instalar en
producción antes de las pruebas de aceptación de Hydev.**

## Recetario

Se conserva la estación física existente: caldero con agua, mechero, pala de
madera, tres ingredientes en orden, ventana de revolver y servir con cuenco.
La cantidad de raciones continúa siendo de 1 a 3 en función del nivel de agua.

Se sustituyó la pequeña GUI antigua por una GUI de **54 espacios**, fondo y
títulos oscuros/dorados inspirados en Misiones. La portada muestra 15 platos
por página con fichas separadas de ingredientes, tiempos, uso y beneficio,
además del progreso de descubrimiento. El menú **no abre categorías externas,
no ejecuta comandos desde iconos y no menciona plugins al jugador**.
Los textos, materiales, iconos Base64 y posiciones son editables en
`plugins/SpectreCore/ui/recetario.yml`.

Por defecto, las 15 fichas son consultables desde el primer día y muestran
ingredientes, cocción y beneficios. La etiqueta distingue **pendiente de
preparar** de **descubierta**. Los descubrimientos siguen persistiendo por
jugador. Quien prefiera el misterio puede activar
`catalogo.ocultar-desconocidas: true` en `ui/recetario.yml`; entonces
las recetas desconocidas muestran únicamente una pista. Las 4 recetas anteriores conservan IDs, ingredientes
en orden, ventanas de cocción y resultados originales.

## 15 recetas y efectos normales

El **grado** (1–5) representa la rareza/costo relativo de los ingredientes
en Minecraft; **no es un precio monetario fijo** porque la economía del
servidor puede modificar los valores de mercado. Los platos más caros
aportan efectos más intensos o combinaciones superiores.

| Plato | Ingredientes (orden exacto) | Costo relativo | Beneficio normal |
|---|---|---|---|
| Guiso del Guardián | Patata + filete cocido + champiñón | Grado 1 | Resistencia I, 120 s |
| Caldo del Alba | Zanahoria + trigo + zanahoria dorada | Grado 2 | Velocidad I, 180 s |
| Estofado Carmesí | Remolacha + verruga del Nether + filete cocido | Grado 2 | Fuerza I, 90 s |
| Consomé Espectral | Seta roja + polvo de hueso + lágrima de ghast | Grado 4 | Regeneración I, 20 s |
| Caldo Minero | Hierro crudo + patata asada + champiñón | Grado 2 | Prisa minera I, 180 s |
| Caldo de Velocidad | Azúcar + conejo cocido + zanahoria dorada | Grado 3 | Velocidad II, 90 s |
| Sopa del Espeleólogo | Bayas luminosas + carbón + patata asada | Grado 1 | Visión nocturna I, 180 s |
| Caldo de las Mareas | Bacalao + alga + cristales de prismarina | Grado 2 | Respiración acuática I, 240 s |
| Estofado del Saltador | Pata de conejo + miel + conejo cocido | Grado 3 | Salto II, 120 s |
| Potaje del Escudero | Lingote de hierro + cordero cocido + zanahoria dorada | Grado 2 | Resistencia I, 180 s |
| Caldo de la Brasa | Polvo de blaze + crema de magma + filete cocido | Grado 3 | Resistencia al fuego I, 240 s |
| Consomé de Vitalidad | Sandía reluciente + lágrima de ghast + miel | Grado 4 | Regeneración II, 16 s |
| Sopa de las Sombras | Ojo de araña fermentado + zanahoria dorada + tinta | Grado 3 | Invisibilidad I, 150 s |
| Caldo del Maratón | Perla de ender + pata de conejo + zanahoria dorada | Grado 4 | Velocidad II + Salto I, 180 s |
| Banquete del Eco | Fragmento de eco + manzana dorada + lágrima de ghast | Grado 5 | Absorción II, 210 s + Regeneración II, 25 s |

Cada plato incluye **normal, exquisito y sobrecocido**:
- Exquisito: el efecto dura aproximadamente 25% más.
- Sobrecocido: el efecto dura aproximadamente 40% menos.
- El nivel del efecto depende de la receta; la calidad cambia la duración,
  sin sobrepasar el nivel II en esta primera tanda.
- `número de raciones × 3` unidades de ingredientes por lote,
  conservando las tres fases originales del minijuego.

## Integración de los objetos entregados

El archivo `integracion-mmoitems/COCINA.yml` contiene
**45 definiciones**, 12 existentes y 33 nuevas. Es la única fuente de
platos culinarios de SpectreCore: **no se buscan premios en CONSUMABLE**.

**IMPORTANTE: estos objetos no se instalan automáticamente.**
Antes de probar la expansión en staging:

1. Respaldar `plugins/SpectreCore/` y la configuración de MMOItems existente.
2. Registrar el tipo `COCINA` en `plugins/MMOItems/item-types.yml`
   (subtipo heredado de `CONSUMABLE`, sin modificar el tipo genérico).
   Instalar las 45 definiciones en `plugins/MMOItems/item/COCINA.yml`.
   **No copiar ni fusionar estos platos en `item/consumable.yml`**.
   Tras reemplazar el catálogo, ejecutar `/mi reload` (o reiniciar).
   Las 45 variantes incrementan `revision-id: 1` para regenerar sus
   estadísticas en copias antiguas al actualizarse en MMOItems (inicio de
   sesión, interacción de inventario, según `item-revision.disable-on`).
   **Comprobar primero un objeto recién generado** y después una copia
   antigua: la actualización de objetos viejos depende de la configuración
   concreta de MMOItems. Respaldar inventarios si ya había platos distribuidos.
   Si los IDs culinarios anteriores estuvieran en CONSUMABLE, revisar y
   migrar esos IDs con respaldo; conservar cualquier otro consumible.
3. En MMOItems, los efectos de los consumibles usan `effects.<efecto>.duration`
   (segundos) y `effects.<efecto>.amplifier` (intensidad I=1, II=2).
   **No usar `level`**: versiones recientes de MMOItems leen la clave
   `amplifier`, y los objetos con `level` no incorporan el efecto esperado.
   El catálogo se corrigió para las 45 variantes y sus 51 efectos.
   Verificar en la versión instalada las variantes de `HASTE`, `JUMP_BOOST`,
   `WATER_BREATHING` y `ABSORPTION`; aún no están probadas dentro de Hydev.
4. Iniciar SpectreCore y comprobar que se cargaron 15 recetas y que se pueden
   resolver los tres objetos de **cada** plato.
5. Si una recompensa no existe, la estación no entrega el plato ni consume
   el cuenco al servir. **No forzar ni repetir transacciones pendientes**;
   consultar el registro de transacciones.

La ampliación no añade efectos de minería basados en multiplicadores de
minerales o economía, por lo que el Caldo Minero concede Prisa minera
y **no** incrementa el botín. Esta separación evita explotación de drops.

## Riesgos y validación

- **Selección por primer ingrediente:** todas las recetas oficiales tienen
  un ingrediente inicial distinto; de otro modo una preparación sería
  inaccesible. El cargador ahora detecta y rechaza duplicados.
- **Migración:** `ModuleConfiguration` agrega solamente las nuevas
  recetas que falten a `modules/cocina/recetas.yml`, conservando ajustes
  existentes y el respaldo previo de la configuración.
- **Seguridad de inventarios:** eventos de clic y arrastre bloquean sacar
  ítems; los controles solo ejecutan acciones permitidas en Java en el tick
  siguiente y verifican que el inventario siga abierto.
- **No se ha probado en un servidor real**: realizar quince preparaciones,
  al menos tres calidades y una prueba de desconexión/reconexión con
  descubrimientos antes del despliegue general.

