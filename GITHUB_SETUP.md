# Primera publicación a GitHub

Repositorio previsto: **hyspectre/SpectreCore**, privado, rama `main`.

Estado: `0.7.3-m3-alpha` (código fuente para staging).

Contenido:
- `src/main/java`: código fuente Java.
- `src/main/resources`: configuraciones YAML de ejemplo.
- `tests`: verificaciones y scripts.
- `.github/workflows/build.yml`: pipeline de validación/compilación Java 21/Paper 1.21.10.
- Documentos de pruebas y migración.

No subir un ZIP como único archivo dentro del repositorio: descomprimirlo y versionar su **contenido**, conservando la estructura y el directorio `.github/`.

Tras el primer push, verificar que GitHub Actions compile antes de considerar el JAR listo.
