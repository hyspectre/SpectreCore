# SpectreCore — núcleo RPG de Hydev

**Versión en desarrollo:** `0.9.0-dev1`  
**Servidor objetivo:** Paper 1.21.10 · Java 21  
**Estado:** primera entrega de 0.9; compilación y pruebas automatizadas en GitHub Actions. **No es una versión de producción**.

SpectreCore reúne cocina, accesorios consumibles, habilidades de armadura, profesiones, encargos, exploración, Slayer, herramientas de mundo y menús dentro de un único plugin modular. Los módulos forman parte permanente del sistema; no existen interruptores de configuración para desactivarlos.

La línea 0.9 incorpora **hitos de colección en el Relicario**: progreso visible por categoría, objetivos configurables y celebración de descubrimientos. No añade monedas, estadísticas ni estructuras nuevas de persistencia. Consulta [ROADMAP_0.9.md](ROADMAP_0.9.md) para el alcance y las verificaciones aplazadas.

## Comandos

Solo se registra `/spectrecore` como comando raíz.

- Jugadores: `/spectrecore mago`, `/spectrecore relicario`, `/spectrecore armaduras`, `/spectrecore cocina`, `/spectrecore trabajos`, `/spectrecore misiones`.
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
```

El workflow `.github/workflows/build.yml` ejecuta además validadores de configuración, pruebas Java y empaqueta el JAR al completar satisfactoriamente. La compilación no sustituye pruebas reales dentro de Minecraft.

## Actualización segura

Lee [MIGRACION_0.8.2-stability-rc1.md](MIGRACION_0.8.2-stability-rc1.md), conserva copias de los archivos persistentes y actualiza las referencias antiguas a `/mago` por `/spectrecore mago`. Las transacciones de trabajos interrumpidas se dejan en cuarentena para conciliación manual.

**No instalar directamente en producción sin completar las pruebas de integración y respaldar los datos.**
