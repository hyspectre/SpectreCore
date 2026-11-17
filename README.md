# SpectreCore 0.7.3-m2-alpha — Hydev · FUENTES PARA STAGING

**No es un JAR compilado ni una versión apta para producción.** Esta entrega continúa la 0.7.3-m1 y preserva sus correcciones de persistencia, sus mecánicas y la jerarquía administrativa `/spectrecore`.

## Novedades m2

- `/mago`: centro informativo con **Paper Dialogs** y categorías definidas en `ui/mago.yml`.
- `/mago armaduras`: guía de los cinco conjuntos en Dialogs, sin libros. `/spectrecore armaduras` sigue funcionando por compatibilidad.
- `/mago recetas`: reusa el inventario GUI del recetario existente.
- MiniMessage vía Adventure en menús, recetario y nuevos componentes; mensajes antiguos `&` / `§` siguen disponibles.
- Texturas de cabezas custom con payload `textures` Base64 de Minecraft y caché acotada.
- Vistas previas temporales de modelos YAML con `BlockDisplay`, `ItemDisplay` (incluye cabezas Base64) y `TextDisplay`.
- Detección opcional de MythicLib, MMOItems, MythicMobs, ItemsAdder, AuraSkills, FancyNPCs, FancyHolograms, zMenu, WorldGuard, LuckPerms, Essentials, FastAsyncWorldEdit y Vault.
- `models/ejemplo.yml`, `ui/mago.yml` y textos en `modules/*/mensajes.yml` editables sin cambiar Java.

## Compilar y probar

Java **21** + Paper **1.21.10** y Gradle compatible. Ejecuta `gradle clean build` en la raíz (requiere acceso a repositorios de dependencias). **No se ha validado la compilación completa de esta versión aquí.**

Comprobaciones sin servidor (requieren `javac` y, para validadores YAML, Python con PyYAML):

```bash
bash tests/check_java_syntax.sh
bash tests/run_pure_java_tests.sh
python tests/validate_release.py
python tests/validate_presentation.py
python tests/validate_mago_models.py
```

`tests/MigrationRegression.java` y `tests/CommandRoutingRegression.java` se ejecutan como tareas Gradle; esta entrega también los ejecutó con Java 21 de forma aislada.

## Comandos

- Jugadores: `/mago`, `/mago rpg`, `/mago armaduras`, `/mago recetas`.
- Administradores: `/spectrecore ayuda`, `/spectrecore integraciones`, `/spectrecore modelo vista ejemplo`, `/spectrecore reload`.
- Comandos antiguos de `/spectrecore` permanecen. Los aliases de «libro» en Cocina son compatibilidad temporal y no deben considerarse el estándar visual nuevo.

## Estado real de integraciones

Los complementos externos se **detectan**, pero esta versión NO implementa todavía todos sus adaptadores API. No instala nuevas dependencias obligatorias. Los modelos 3D son solo previsualizaciones, **no** despliegues persistentes. MiniMessage aún no se ha aplicado a todos los mensajes históricos.

Lee **`MIGRACION_0.7.3-m2.md`** antes de usar staging y conserva copia íntegra de JAR/config/datos originales. No reemplazar la versión de producción con este ZIP.
