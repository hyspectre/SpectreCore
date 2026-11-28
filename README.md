# SpectreCore · núcleo RPG de Hydev

**SpectreCore** es el núcleo modular de los sistemas RPG de Hydev, un servidor Minecraft de fantasía oscura. Integra la progresión del jugador, el equipamiento, las profesiones y la exploración en un ecosistema coherente: posibilidades profundas, controles intuitivos e implementación ordenada.

> [!IMPORTANT]
> **Lee [AGENTS.md](AGENTS.md) antes de modificar código o configuración.** Contiene el contrato de diseño y desarrollo acordado con Hydev. Consulta también el [estado técnico y auditoría](docs/ESTADO_Y_AUDITORIA.md) para continuar desde el trabajo real, no desde supuestos.

## 1. Estado del repositorio

Última revisión de documentación: **26 de noviembre de 2026**.

| Referencia | Estado al auditar |
|---|---|
| [Rama activa de estabilización](https://github.com/hyspectre/SpectreCore/tree/dev09) | `0.9.0-dev8`; recuperación del parche `1fdcf87`, consulta [estado y migración](docs/ESTABILIZACION_DEV09.md) |
| [Rama acumulativa de desarrollo](https://github.com/hyspectre/SpectreCore/tree/fix/0-9-mineria-integraciones-hydev) | `0.9.0-dev8`, base auditada: [`73eb5c4`](https://github.com/hyspectre/SpectreCore/commit/73eb5c4dc60dce3f37743e4238e57d25ffd44c7c) |
| [PR #11](https://github.com/hyspectre/SpectreCore/pull/11) | Borrador; integra la revisión minera sobre PR #10 |
| `main` | Código histórico `0.7.3-m3-alpha`; no contiene aún la línea 0.9 |
| Servidor objetivo | Paper **1.21.10**, Java **21** |
| Certificación de release | **Pendiente**. CI verde no implica compatibilidad ni seguridad ingame |

La [auditoría de experiencia del jugador](docs/AUDITORIA_EXPERIENCIA_DEV09.md) registra mejoras de claridad y feedback de la línea activa, separadas de la auditoría técnica.

La documentación base está disponible en ambas ramas; eso **no significa que sus fuentes estén sincronizadas**. Para retomar el desarrollo 0.9, comienza por `dev09` y verifica el commit remoto y cualquier cambio posterior. No derives una funcionalidad nueva de `main` por defecto. Comprueba siempre la versión real en `build.gradle.kts` y `src/main/resources/plugin.yml`.

## 2. Principios no negociables

- **Jugador primero:** diseño intuitivo, decisiones útiles, retroalimentación clara y progresión con propósito.
- **Rendimiento, optimización, limpieza y seguridad.** Monolito modular, permisos validados, datos persistentes y transacciones sin duplicación.
- **Configura el juego en YAML, no la arquitectura.** Los módulos siempre forman parte del núcleo; sin interruptores para desactivarlos. Ausencias de dependencias deben diagnosticarse de manera explícita.
- **Identidad Hydev:** el jugador no debe ver nombres de plugins, IDs internos ni instrucciones técnicas en menús, lore o NPC. MiniMessage, español latinoamericano, gradientes y versalitas legibles.
- **Feedback sin actionbar:** estados continuos mediante barras nativas discretas, hitos mediante toasts o floats y mensajes puntuales sin saturar el chat.
- **Integrar antes de reinventar:** una fuente de verdad por dato, APIs públicas verificadas e IDs existentes conservados.

Las reglas completas, las convenciones de UX y el flujo obligatorio están en [AGENTS.md](AGENTS.md).

## 3. Sistemas de la línea 0.9

| Dominio | Función |
|---|---|
| Equipamiento | Cinco conjuntos legendarios, habilidades activas/pasivas y guía de armaduras |
| Cocina | Calderos, actividades culinarias simplificadas, 15 recetas y 45 resultados por calidad |
| Relicario | Accesorios consumibles de desbloqueo permanente, bonificaciones simultáneas, colecciones e hitos; el catálogo base auditado incluye 37 reliquias |
| Trabajos y tesoros | Contratos de minero, granjero y cazador, recompensas, tablón de abastecimiento y encargos |
| Minería | Paladio, Mitrilo, herramientas especializadas, resonancia, gemas, geodas, Duendes y Prospectores, Taller del Minero y Forja Minera |
| Slayer | Misiones, progresión, contratos de dificultad, jefes y recompensas |
| Mazmorras | Controladores de combate y portal de extracción dependiente del registro de expediciones |
| El Mago | Centro de conocimiento y navegación por categorías; menús independientes y configurables |
| Núcleo | Adaptadores, comandos, textos MiniMessage, diagnósticos, modelos y avisos visuales |

La tabla describe funcionalidades presentes en la base auditada de desarrollo, **no certificadas todas en el servidor**. Los límites, riesgos, y pruebas pendientes están documentados en [docs/ESTADO_Y_AUDITORIA.md](docs/ESTADO_Y_AUDITORIA.md). El [roadmap 0.9](https://github.com/hyspectre/SpectreCore/blob/fix/0-9-mineria-integraciones-hydev/ROADMAP_0.9.md) conserva el historial de diseño; contrástalo con el código, pues algunos detalles fueron modificados en dev8.

### Progresión minera acordada

AuraSkills es la fuente de experiencia, nivel, Luck y menú de progresión. Por nivel concede permisos `hydev.mineria.*`; SpectreCore valida los hitos y mantiene por separado los contratos pagados.

| Nivel | Desbloqueo |
|---:|---|
| 1 | Minería inicial; contrato Novato |
| 3 | Minerales hechizados; Taller del Minero |
| 6 | Paladio, Taladro, contrato Experimentado |
| 10 | Recompensa: 1 Cénit |
| 12 | Duendes del Oro, Chatarra Mecánica y Taladro Reforzado |
| 15 | Resonancia mineral, Prospectores, Pico Perdido, Taladro Calibrado y contrato Profesional |
| 18 | Mitrilo y picos de Mitrilo |
| 22 | Resonancia arcana, Pico de Amatista y geodas |
| 25 | Ingeniería y prospección: Linterna y Barrena de Mitrilo |

**Geodas resonantes:** desde nivel 22, las menas naturales de diamante, esmeralda y redstone acumulan progreso persistente. El medidor muestra el avance y las menas restantes; a 100 % la siguiente ubicación apta revela una geoda privada. Por defecto son 200 menas (Lupa de Espato: +30 % de avance). El progreso no se pierde si falta una ubicación segura o un recurso configurado. Abre la geoda con la Llave Resonante para seleccionar y obtener una gema IV.

No reemplaces los IDs de ítems preexistentes, ni mezcles las antiguas recetas y paquetes mineros sin verificar el catálogo efectivo.

## 4. Dónde trabajar

Estas rutas corresponden a la rama 0.9; en una rama antigua algunas no existen.

| Ruta | Responsabilidad |
|---|---|
| `src/main/java/dev/hydev/spectrecore/` | Código Java; `SpectreCorePlugin.java` es la composición y ciclo de vida |
| `.../command/`, `.../config/`, `.../text/` | Rutas/permisos, validación/migraciones, renderizado textual |
| `.../storage/`, `.../integration/`, `.../core/` | Persistencia y diarios, adaptadores externos, servicios compartidos |
| `.../cooking/`, `.../relicario/`, `.../mining/` | Dominios especializados |
| `.../systems/`, `.../skills/`, `.../activities/`, `.../treasure/` | Trabajos, Slayer, portal, equipamiento y otras actividades |
| `.../guide/`, `.../model/`, `.../diagnostics/` | Interfaz, modelos Display y diagnóstico |
| `src/main/resources/modules/<dominio>/` | Configuración, balance, mensajes, recetas y presentación propios |
| `src/main/resources/ui/`, `src/main/resources/models/` | Menús nativos y modelos |
| `integracion-auraskills/`, `integracion-mythicmobs/`, `integracion-mmoitems/` | Archivos de integración externos; no equivalen a una instalación realizada |
| `tests/`, `.github/workflows/build.yml` | Validaciones, regresiones, compilación y empaquetado |
| `referencias-skript/`, `integracion-zmenu/` | Referencias históricas; no desplegar automáticamente |

## 5. Comandos y presentación

Contrato vigente de **0.9**: todos los comandos propios se enrutan bajo `/spectrecore`. Las operaciones de jugador pueden exponerse mediante El Mago, objetos y NPC, sin revelar comandos en los iconos. La versión antigua de `main` aún tiene `/mago` registrado.

- Jugadores: `/spectrecore mago`, `relicario`, `armaduras`, `cocina`, `trabajos`, `trabajos abastecimiento`, `minero`, `misiones` (todos bajo el mismo raíz).
- Administración: `/spectrecore ayuda`, `integraciones`, `diagnostico`, `modelo`, `reload`.
- Conciliación de operaciones: `/spectrecore trabajos transacciones` y `/spectrecore trabajos resolver <uuid>` solo tras comprobar el estado real.

La autoridad de rutas concretas y permisos es `command/CommandRoutes.java`, `command/CommandRouter.java`, `modules/nucleo/comandos.yml` y `plugin.yml` de la rama seleccionada. No confíes en listados de versiones anteriores para implementar comandos nuevos.

## 6. Ecosistema e instalación

Base: Paper, MythicLib/MMOItems, MythicMobs **Free 5.13.0**, ItemsAdder y AuraSkills. Complementos: FancyNPCs, FancyHolograms, zMenu, WorldGuard, LuckPerms, EssentialsX, FAWE, Vault y Dungeons+ según función. Verifica versiones reales y APIs antes de modificar adaptadores: las dependencias de compilación no prueban las versiones instaladas.

- La línea minera utiliza [`integracion-mmoitems/mineria-v2/`](https://github.com/hyspectre/SpectreCore/tree/fix/0-9-mineria-integraciones-hydev/integracion-mmoitems/mineria-v2), [recompensas AuraSkills](https://github.com/hyspectre/SpectreCore/blob/fix/0-9-mineria-integraciones-hydev/integracion-auraskills/rewards/mining.yml) y ambos YAML de MythicMobs.
- El instalador minero dispone de simulación y respaldos, y valida claves YAML duplicadas antes de escribir. Requiere PyYAML. **No lo apliques aún a producción** sin validar la Forja e integraciones en un servidor de pruebas.
- El Taller del Minero compacta 64 unidades y pulveriza materiales hechizados por dinero; no requiere una estación duplicada de MMOItems. Falta verificar el enlace con el NPC real.
- Consulta las migraciones en la rama activa antes de reemplazar el JAR. Conserva respaldos de JAR, configuraciones y datos. No instales paquetes legacy y actuales juntos.

## 7. Compilar y validar

Requisitos: JDK 21, Gradle 8.14.3 (la versión usada por CI), Python 3 y PyYAML; acceso a los repositorios Maven definidos en Gradle.

```bash
git status --short --branch
git log -1 --oneline
python3 -m pip install pyyaml
bash tests/check_java_syntax.sh
bash tests/run_pure_java_tests.sh
gradle clean build --no-daemon
```

Ejecuta además **los validadores de Python enumerados en `.github/workflows/build.yml` de tu rama**, incluidas las pruebas del instalador si existen. El workflow de 0.9 empaqueta el JAR en `build/libs/` y el ZIP de integraciones mineras. Comprueba resultados del commit exacto, no de un build anterior.

Una compilación exitosa no verifica eventos de Paper, comportamiento de mobs, compatibilidad de ítems, transacciones o rendimiento real. Consulta el checklist de certificación en [docs/ESTADO_Y_AUDITORIA.md](docs/ESTADO_Y_AUDITORIA.md).

## 8. Continuidad de trabajo

1. Lee [AGENTS.md](AGENTS.md) y el [estado/auditoría](docs/ESTADO_Y_AUDITORIA.md).
2. Inspecciona ramas, versión, archivos y pruebas antes de elegir la base.
3. Prioriza los bloqueos de seguridad y datos antes de ampliar contenido, salvo indicación explícita del responsable.
4. Diseña, implementa, valida y publica los cambios de código/configuración en GitHub sin forzar pushes ni fusionar a `main` sin autorización.
5. Actualiza las notas de continuidad cuando cambie el estado de una mecánica, incluyendo commit, evidencia, tareas pendientes y riesgos.

**La documentación es una guía; las fuentes de la rama y las pruebas reproducibles determinan el estado real.**
