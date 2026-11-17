# SpectreCore 0.7.3-m2-alpha — núcleo de UI y modelos (STAGING)

Esta versión continúa desde 0.7.3-m1. **No instalar en producción todavía.** El ZIP contiene fuentes, NO JAR compilado.

## Implementación entregada

- `/mago` con Dialogs nativos de Paper 1.21.10 y navegación de páginas por `ui/mago.yml` editable. Sin ejecución arbitraria de comandos desde YAML; destinos solo páginas internas o rutas previamente autorizadas.
- Categorías iniciales: RPG (armaduras, habilidades, mascotas, encantamientos, accesorios), Profesiones (Cocina), Guías.
- `/mago armaduras` y ruta antigua `/spectrecore armaduras`: Dialogs en lugar de libro; contenido de habilidades y bonos conserva cálculos ya existentes y lectura de MMOItems.
- `/mago recetas`: abre el recetario GUI de Cocina actual; el recetario usa Components Adventure para títulos/lore, compatibles con MiniMessage.
- `TextService`: soporte MiniMessage nativo para nuevas interfaces y compatibilidad con formato heredado `&`/`§` para mensajes antiguos. Tiene interpolación segura de Component mediante placeholders para datos de jugadores.
- `CustomHeads`: creación de `PLAYER_HEAD` con propiedad `textures` Base64 de GameProfile, valida URL `textures.minecraft.net` y usa caché acotada para los items.
- `DisplayModels`: vista previa administrativa temporal de modelos YAML mediante `BLOCK_DISPLAY`, `ITEM_DISPLAY`, `TEXT_DISPLAY`, con posición, escala y rotación configurables; permite `base64` en cabezas `ITEM_DISPLAY`.
- Límites defensivos de tamaño, IDs, piezas y tiempo. `/spectrecore modelo vista ejemplo` permite probarlo; vista previa limitada a 4 jugadores, 24 piezas cada una y eliminación automática (10 s por defecto).
- `/spectrecore integraciones` para ver presencia de MythicLib, MMOItems, MythicMobs, ItemsAdder, AuraSkills, FancyHolograms, FancyNPCs, zMenu, WorldGuard, LuckPerms, Essentials, FAWE y Vault. Son detecciones opcionales, no puentes funcionales todavía.
- Preserva cambios de persistencia m1 y las 8 familias de módulos declaradas.

## Limitaciones reales

1. Sin Gradle y sin dependencia Paper API descargable en este entorno: **no se pudo compilar el JAR de m2 ni ejecutar el servidor**. Los métodos Paper se comprobaron con Javadocs oficiales 1.21.10; quedan pendientes compilación e integración.
2. No está migrado el 100 % de los `sendMessage(String)` existentes. MiniMessage ya funciona en el motor nuevo de Component, en Dialogs y en el recetario, pero la adopción general de mensajes es progresiva.
3. `DisplayModels` ofrece **vista previa temporal**, no crea instalaciones persistentes ni reproduce animaciones; no se han desarrollado aún editor Blockbench ni imports automáticos.
4. Los proveedores externos solo se detectan: FancyNPCs, FancyHolograms, WorldGuard, zMenu, LuckPerms y AuraSkills deben integrarse con APIs exactas según las versiones reales del servidor; no hay acoplamiento a versiones no confirmadas.
5. Persisten ventanas de interrupción en acciones de inventario/recetas del módulo Cocina; la protección atómica m1 no equivale a transacciones completas.
6. La base64 `textures` no es necesariamente una firma oficial de Mojang; es un contenido configurado por administrador. Los modelos usan recursos a cargo de un equipo confiable.
7. `/mago` es una interfaz informativa: no modifica ni concede objetos o progreso por sí sola.

## Ensayos de staging obligatorios

1. Respaldar `/plugins/SpectreCore`, mundos, economía y progresión. Retirar JAR antiguos que compitan por las mismas mecánicas.
2. Compilar con Java 21 y Paper 1.21.10; resolver primero cualquier error de API.
3. Jugador sin permisos: `/mago`, RPG → Armaduras → Bonos / Habilidad → Volver, Cocina → Recetario; comprobar que no accede a comandos administrativos.
4. Administrador: `/spectrecore armaduras`, `/spectrecore integraciones`, `/spectrecore modelo vista ejemplo` y cierre/reinicio antes de que caduque la vista. Verificar que no quedan display entities huérfanas.
5. Modelo con `base64` real en `models/ejemplo.yml`: visualizar textura, posición, rotación y escala; probar base64 inválido, URL ajena, vector enorme, 25+ partes y texto enorme.
6. Alterar `ui/mago.yml` con destino de botón inexistente y hacer `/spectrecore reload`: conservar el último menú válido y emitir error, sin bloquear el resto de mecánicas.
7. Verificar condiciones multijugador: abrir/cerrar 30 veces, recargar YAML, inventario lleno, jugadores desconectando simultáneamente, y medir TPS/MSPT usando Spark.
8. Probar las 4 recetas, 5 habilidades, cofres, trabajos, portal, tesoros, resonancia, slayer y actividades de la 0.7.3-m1.

## Fallo del deployment

Si falla, detener el servidor, restaurar copia de datos correspondiente y reinstalar el JAR de m1. **No mezclar estados nuevos y antiguos** sin revisar cambios de esquema.

## Recomendación de arquitectura siguiente

- Separar lógicas de Cocina en servicios más pequeños, sin cambios funcionales.
- Transacciones persistentes para Cocina y reconciliación de claims de cofres.
- Convertir todos los textos externos de GUI, hologramas y mecánicas a componentes Adventure/MiniMessage mediante capas `TextService`.
- Modelos 3D persistentes con gestión de chunk, PDC, reciclaje de instancias y telemetría, antes de habilitarlos en producción.
- Conectar NPC El Mago por adaptador FancyNPCs después de revisar su versión/API exacta.
