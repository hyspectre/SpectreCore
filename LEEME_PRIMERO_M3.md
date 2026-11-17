# SpectreCore 0.7.3-m3-alpha — LEER ANTES DE INSTALAR

Esta entrega es un **proyecto fuente**, no un JAR compilado ni una versión de producción.

1. Abre `MIGRACION_0.7.3-m3.md`: cambios, límites, reversión, archivos que respaldar.
2. Abre `PRUEBAS_UNIFICADAS_0.7.3-m3.md`: un plan completo de ensayo en staging para minimizar reinicios de Hydev.
3. Compila el contenido de ESTA carpeta con GitHub Actions o `gradle clean build --no-daemon` (Java 21; Paper API 1.21.10).
4. La generación exitosa de JAR no certifica mecánicas: prueba el JAR en staging con versiones reales de los plugins de Hydev.
5. Solo después de compilación y ensayo, considera un despliegue programado de producción con respaldo y plan de retorno.

**Nuevos comandos administrativos:**

- `/spectrecore diagnostico`
- `/spectrecore cocina transacciones`
- `/spectrecore cocina resolver <UUID> confirmado` (requiere ser operador en juego; solo con inspección humana)
- `/spectrecore modelo vista ejemplo`
- `/spectrecore modelo colocar ejemplo`
- `/spectrecore modelo lista`
- `/spectrecore modelo quitar <UUID>`

**Usuarios:** `/mago`, `/mago rpg`, `/mago armaduras`, `/mago recetas` y la navegación por Dialogs.

**Estado**: tests de sintaxis, configuración, regresión aislada y límites defensivos superados; compilación Java+Paper real y validación dentro de un servidor pendientes.
