# Validación de SpectreCore 0.7.3-alpha

- Compilación Gradle: correcta (Java 21, Paper API 1.21.10).
- CommandRoutingRegression: 374 comprobaciones de rutas, permisos, argumentos, autocompletado y migración; cobertura de los 36 handlers que antes eran comandos independientes.
- ModuleConfigSmoke: 70 comprobaciones de migración y asociación con módulos reales.
- PresentationConfigSmoke: 428 comprobaciones de recarga y conservación de ajustes; incluye textos antiguos y descripciones personalizadas.
- MigrationRegression: 98.499 comprobaciones de las reglas anteriores.
- Auditorías Python: configuración modular, defaults de presentación, YAML sin claves duplicadas y único comando público.

No se ha ejecutado en un servidor Paper real. Faltan pruebas con NPC, zMenu, MMOItems y callbacks de Dungeons+ en la instalación del servidor. Las configuraciones externas no proporcionadas deben actualizarse mediante la tabla COMANDOS_0.7.3.md.
