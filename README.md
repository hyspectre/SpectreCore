# SpectreCore — núcleo RPG de Hydev

**Versión en desarrollo:** `0.9.0-dev6`  
**Servidor objetivo:** Paper 1.21.10 · Java 21  
**Estado:** sexta entrega de 0.9; usa GitHub Actions para validar el código. **No es una versión de producción**.

SpectreCore reúne cocina, accesorios consumibles, habilidades de armadura, profesiones, encargos, exploración, Slayer, herramientas de mundo y menús dentro de un único plugin modular. Los módulos forman parte permanente del sistema; no existen interruptores de configuración para desactivarlos.

La línea 0.9 incorpora **hitos de colección en el Relicario**: progreso visible por categoría, objetivos configurables y celebración de descubrimientos. No añade monedas, estadísticas ni estructuras nuevas de persistencia. Incluye además un **Tablón de abastecimiento** para consultar los materiales realmente definidos por minería, caza y agricultura, cuánto llevas y si sus hallazgos están accesibles. No crea recetas ni altera la economía. Consulta [ROADMAP_0.9.md](ROADMAP_0.9.md) para el alcance pendiente.

La tercera entrega agrega **Mitrilo**, hallazgo de minería avanzada y recurso para dos picos de MMOItems. El tablón incorpora el **Pedido del Herrero**, que entrega 4 Mitrilo por 180 monedas con registro de transacciones. Los fragmentos de recetas deben integrarse en la Forja ya existente, sin crear una estación duplicada. Consulta [INSTALAR_MITRILO.md](integracion-mmoitems/INSTALAR_MITRILO.md).

La cuarta entrega añade **Brújula Geológica, Farol de Mitrilo, Barrena de Mitrilo y Cristal de Mitrilo**, además de una habilidad de extracción por vetas en el Pico existente. Todas las herramientas guardan su energía de forma persistente en cada objeto. Consulta la [guía de instalación y controles](integracion-mmoitems/INSTALAR_MITRILO.md) antes de fusionar ítems y recetas en MMOItems.

La quinta entrega introduce el **Pico de Amatista** y una resonancia amplificada: su habilidad crea bloques hechizados temporales que se rompen para obtener gemas de distintas calidades. El pico utiliza Mitrilo, Paladio y amatista en su receta. Consulta las instrucciones del [Pico de Amatista](integracion-mmoitems/INSTALAR_MITRILO.md#pico-de-amatista--resonancia-amplificada-090-dev5).

La sexta entrega añade el **Taller del Minero** dentro de SpectreCore. Desde nivel 3 de Minería de AuraSkills, los jugadores pueden compactar 64 minerales comunes en un objeto hechizado MMOItems o pulverizarlo para obtener un pago fijo. Tiene menú nativo, vista previa, confirmación, operaciones por lotes y protección de transacciones. Se abre por `/spectrecore minero`, desde Trabajos o con el Compactador Portátil. Consulta la [guía de integración del NPC Minero](integracion-mmoitems/TALLER_MINERO_NATIVO.md).

## Comandos

Solo se registra `/spectrecore` como comando raíz.

- Jugadores: `/spectrecore mago`, `/spectrecore relicario`, `/spectrecore armaduras`, `/spectrecore cocina`, `/spectrecore trabajos`, `/spectrecore trabajos abastecimiento`, `/spectrecore misiones`.
- Administradores: `/spectrecore integraciones`, `/spectrecore diagnostico`, `/spectrecore modelo`, `/spectrecore reload`.
- Compras pendientes: `/spectrecore trabajos transacciones`; resolver solo después de comprobar permisos y saldos mediante `/spectrecore trabajos resolver <uuid>`.

La navegación del Mago usa menús del servidor configurados en `src/main/resources/ui/mago.yml`. Todas las rutas y permisos aparecen en `src/main/java/dev/hydev/spectrecore/command/CommandRoutes.java`.

## Configuración y recursos

- `src/main/resources/modules/`: balance, recetas, mensajes, límites y presentación.
- `src/main/resources/ui/`: menús nativos, incluido el Relicario.
- `integracion-mmoitems/`: ítems y configuraciones de MMOItems que debe instalar la administración.
- `src/main/resources/models/`: modelos con Display Entities, admitiendo cabezas Base64.
- Adventure MiniMessage en textos configurables; preserva soporte para colores heredados cuando sea necesario.

## Integraciones

Paper, MythicLib/MMOItems, MythicMobs, ItemsAdder y AuraSkills integran el ecosistema previsto. También se detectan o utilizan, según función y versión, WorldGuard, LuckPerms, FancyNPCs, FancyHolograms, zMenu, EssentialsX, FAWE y Vault. La presencia de un plugin no garantiza que todas sus funciones estén integradas.

## Compilación y verificaciones

```bash
gradle clean build --no-daemon
bash tests/run_pure_java_tests.sh
python3 tests/validate_release.py
python3 tests/validate_relicario_catalog.py
python3 tests/validate_supply_board.py
python3 tests/validate_mithril.py
```

El workflow `.github/workflows/build.yml` ejecuta además validadores de configuración, pruebas Java y empaqueta el JAR al completar satisfactoriamente. La compilación no sustituye pruebas reales dentro de Minecraft.

## Actualización segura

Lee [MIGRACION_0.8.2-stability-rc1.md](MIGRACION_0.8.2-stability-rc1.md), conserva copias de los archivos persistentes y actualiza las referencias antiguas a `/mago` por `/spectrecore mago`. Las transacciones de trabajos interrumpidas se dejan en cuarentena para conciliación manual.

**No instalar directamente en producción sin completar las pruebas de integración y respaldar los datos.**
