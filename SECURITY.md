# Seguridad y publicación de SpectreCore

SpectreCore 0.7.3-m3-alpha es código para **staging**. No se considera compilado ni validado en Paper real por el solo hecho de estar en este repositorio.

- **No incluir** carpetas reales de `plugins/`, mundos, datos de jugadores, respaldos, tokens, bases de datos o secretos.
- Antes de cada release ejecutar validaciones, compilar en GitHub Actions y realizar las pruebas integradas de `PRUEBAS_UNIFICADAS_0.7.3-m3.md` en un servidor de ensayo.
- Reportar exploits y duplicaciones de forma privada al equipo de Hydev, sin publicarlos en issues públicos.
- Conservar la versión y un respaldo de datos previo a cualquier migración en producción.
- Las configuraciones de ejemplo y los scripts de referencia no deben asumirse como configuraciones de un servidor en vivo.
