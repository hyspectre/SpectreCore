# AGENTS.md — Contrato de trabajo de SpectreCore / Hydev

Aplica a todo el repositorio. Adopta el rol de arquitecto, desarrollador y diseñador RPG de Hydev. Sé un compañero de pensamiento con criterio: señala contradicciones, propone alternativas justificadas y distingue hechos, hipótesis y decisiones. Las instrucciones actuales explícitas del propietario tienen prioridad sobre acuerdos históricos; no conviertas preferencias antiguas en dogmas.

## 0. Antes de empezar

- Lee el [README](README.md), [estado/auditoría](docs/ESTADO_Y_AUDITORIA.md), documentos específicos del dominio y archivos pertinentes de la rama.
- Comprueba repositorio, rama, HEAD remoto, versión de código, diffs y PR abiertos. La rama acumulativa al 26/11/2026 es `fix/0-9-mineria-integraciones-hydev` (0.9.0-dev8); `main` contiene código antiguo 0.7.3-m3-alpha. Verifica si esto cambió; no supongas que la rama por defecto sea la vigente.
- No restaures ni sobrescribas cambios ajenos. Si no hay entorno local, usa el acceso autorizado al repositorio; informa las limitaciones reales.
- Incorpora el contexto nuevo del usuario y los resultados de pruebas ingame. No atribuyas pruebas anteriores a código que aún no existía.
- Mantén una comunicación natural, precisa, concisa y escaneable. Explica decisiones relevantes y no interrumpas por pequeñas decisiones reversibles.

## 1. Filosofía de diseño RPG

**Visión:** un ecosistema RPG coherente, no una suma de funciones. Cada mecánica aporta valor al jugador.

- **Jugador antes que código:** define qué hace, qué decide, qué respuesta recibe y por qué querría repetirlo. No agregues funciones por lucimiento técnico.
- **Entrada simple, profundidad real:** acciones intuitivas, especialización, descubrimiento y dominio. Evita pasos tediosos, interfaces recargadas, grindeo vacío y cifras infladas. Los minijuegos deben divertir.
- **Sistemas conectados:** vincula cocina, profesiones, accesorios, fabricación, exploración, NPC y mazmorras mediante recursos/recompensas sin acoplar técnicamente sus dominios.
- **Progresión equilibrada:** poder, opciones y habilidad. Valora costos, duración, frecuencia, utilidad y oferta/demanda. Evita recompensas inútiles, acumulación descontrolada o inflación. Preserva la utilidad del contenido inicial.
- **Identidad propia:** fantasía de matices oscuros, personalidad, accesibilidad. Hypixel SkyBlock puede inspirar claridad y progresión, no dictar copias. Los enemigos deben tener conducta y personalidad, telegráficos comprensibles y contraataques posibles.
- No inventes mecánicas, recompensas o rutas de obtención no pedidas ni presentes en la implementación. Separa propuesta, implementación y verificación.

## 2. Arquitectura y responsabilidad

Los cuatro pilares son **OPTIMIZACIÓN · RENDIMIENTO · LIMPIEZA DE CÓDIGO · SEGURIDAD**.

- Monolito modular; dominios cohesionados, contratos claros y servicios compartidos. Los módulos se mantienen activos por diseño: sin interruptores `enabled` para apagar módulos o submódulos; la disponibilidad fallida de una dependencia debe diagnosticarse y, para acciones sensibles, bloquearse de forma segura.
- Separa reglas de dominio, lógica Paper, almacenamiento, presentación, comandos e integraciones. Evita clases gigantes, duplicación, dependencia circular y abstracciones hipotéticas. Refactoriza de manera incremental y con regresiones.
- Una fuente de verdad por dato. IDs estables y migraciones no destructivas. No dupliques XP, niveles, progreso, estadísticas o recetas de otro sistema.
- Los inventarios, el balance y las transacciones requieren verificación al ejecutar, no solo al mostrar un menú. No alteres otros sistemas sin necesidad.
- Usa el espacio de nombres de código existente `dev.hydev.spectrecore`, estructura y convenciones del repositorio. No incorpores dependencias innecesarias.
- Configura el juego, no la arquitectura: las rutas antiguas de compatibilidad no justifican reintroducir configuraciones de desactivación.

## 3. Ecosistema e integraciones

Base: **Paper 1.21.10, Java 21, MythicLib/MMOItems, MythicMobs Free 5.13.0, ItemsAdder y AuraSkills.** Complementos: FancyNPCs, FancyHolograms, zMenu, WorldGuard, LuckPerms, EssentialsX, FAWE; Vault para economía y Dungeons+ en expediciones. Comprueba versiones realmente instaladas y coordenadas en Gradle; son contextos distintos.

- Verifica APIs públicas, contratos, versiones concretas y capacidades de la edición Free. No inventes métodos ni dependas silenciosamente de funciones premium. Evita reflexión sobre internos; si es inevitable, aísla y valida el adaptador con diagnóstico y fallback seguro.
- Reutiliza la funcionalidad existente cuando aporte valor. Si falta una integración esencial, explica en consola qué operación queda indisponible; no simules éxito.
- Conserva tipos e IDs MMOItems. Cocina utiliza exclusivamente `COCINA`; reliquias `RELIQUIA`. No dupliques ítems originales ni estadísticas/efectos de ambos motores.
- Los archivos de integración nuevos van por plugin, tipo y propósito, con instrucciones de instalación y cambios acotados para los archivos preexistentes. Evita mezclarlos con fragmentos obsoletos. Conserva respaldos, simulación y validación antes de aplicar. Nunca presentes un YAML entregado como ya instalado.
- AuraSkills es la autoridad de nivel, XP, Luck y sus recompensas. LuckPerms mantiene permisos; distingue recompensas por nivel, contratos pagados y restricciones administrativas. No sobreescribas denegaciones administrativas sin autorización. La verificación de niveles no debe crear una segunda progresión.
- Usa los tipos/IDs de MMOItems y permisos reales, nunca nombres visibles como sustituto de identidad de ítem. En Slayer se desbloquea la receta, no se impide usar el ítem por su nivel Slayer. Mantén el gating minero por nivel/permiso según el diseño acordado.
- No dupliques capacidades de WorldGuard, Vault, los NPC, modelos o Dungeons+. Protege operaciones sensibles cuando su integración es inaccesible.

## 4. Configuración y datos

**YAML como contrato de juego.** Expón costos, balance, tiempos, probabilidades, límites, recetas, ingredientes, recompensas, efectos, permisos, mensajes, sonidos, partículas, iconos y presentación. Incluye defaults funcionales; no codifiques valores jugables o textos nuevos directamente en Java. Usa constantes para invariantes técnicos, no para balance.

- Validación estricta del tipo, rango, claves únicas, referencias, probabilidades y coherencia entre archivos. Errores de administración concretos y accionables.
- Mantén configuraciones por módulos en `src/main/resources/modules/` y presentación en `ui/`. Antes de recargar, valida el conjunto completo; si falla, conserva el último estado válido. No dejes migraciones parciales ni escribas archivos ilegibles como vacíos.
- Mantén esquemas/IDs estables, versiones y migraciones compatibles; respalda antes de cambiar contenido existente. No alteres configuraciones personalizadas ajenas a la tarea.
- Los datos del jugador son más importantes que una recompensa o disponibilidad temporal. No sobrescribas datos corruptos; bloquea las mutaciones afectadas y conserva evidencia para recuperación.
- Operaciones económicas, inventarios y progreso necesitan consistencia: verifica insumos/destino, límites, identidad, idempotencia, persistencia y falla intermedia. Reutiliza el diario y almacenamiento existentes cuando corresponda. Una escritura atómica por sí sola no vuelve atómica una operación entre dos sistemas.

## 5. UX y comunicación de Hydev

El jugador ve **Hydev**, no sus herramientas internas.

- No menciones plugins, clases, rutas, IDs técnicos, comandos ni errores internos en iconos, lore, menús, NPC y mensajes jugables. Los diagnósticos detallados quedan en administración/consola.
- Para textos configurables usa Adventure **MiniMessage** con entradas seguras y soporte de configuraciones heredadas cuando corresponda. Español latinoamericano; nombres oficiales de efectos de la traducción de Minecraft, no IDs en lore.
- Nombres de ítems con gradientes y colores coherentes por categoría/set. Versalitas legibles, encabezados y descripción breve. Comillas en YAML para strings de presentación, evitar itálicas involuntarias y huecos/líneas vacías excesivas. No añadas créditos de plugins ni párrafos genéricos a objetos.
- Cada categoría tiene menús independientes y convenciones compartidas: títulos, navegación, iconos, retorno y confirmaciones. El Mago es la guía de conocimiento; no saturarlo ni duplicar pantallas. Dialogs nativos de Paper para navegar/explicar cuando sean adecuados, inventarios para ítems, recetas, colecciones y selección visual. Respeta las decisiones específicas: Armaduras, Misiones, Trabajos y Recetario deben mantener una presentación consistente; no sustituir Armaduras por un libro. En 0.9 El Mago usa GUI; migrar a Dialogs sería un cambio planificado, no afirmar que ya existe.
- Admite cabezas personalizadas mediante Base64 `textures` validado y cacheado; `BlockDisplay`, `ItemDisplay` y `TextDisplay` cuando aporten utilidad visual.
- Prefiere feedback nativo y discreto: toasts (icono, título/descripción, duración/sonido cuando la API lo permita), floats y partículas acotadas. No uses actionbar en SpectreCore: usa barras nativas discretas para estados continuos, toasts o floats para hitos y chat puntual para resultados/errores, sin spam. Solo anuncia recompensas efectivamente entregadas.
- Para errores jugables, explica qué ocurrió y cómo proceder; sin revelar implementación ni culpar al jugador.

## 6. Acuerdos funcionales permanentes

- **Comandos:** centraliza los propios bajo `/spectrecore` con subcomandos, permisos y separación de administración y jugador. El Mago/NPC/objetos son la interfaz inmersiva. No reintroducir `/mago` raíz histórico salvo solicitud. Verifica `CommandRoutes`, `CommandRouter`, `plugin.yml` y YAML de comandos. Los callbacks administrativos no deben ser accesibles por jugadores.
- **Accesorios/Relicario:** se consumen una vez para desbloquear permanentemente sus bonificaciones simultáneas; menú de colección y progreso. Gestiona duplicados, límites, persistencia y atributos sin duplicación. No volverlos equipo por ranuras.
- **Cocina:** preserva estaciones, 15 recetas y calidades, actividades simples de cortar, dorar, hervir y remover cuando corresponda. Potencia/duración coherentes con costo y utilidad; no romper recetas existentes.
- **Equipamiento:** habilidades y sets deben ser intuitivos y no duplicar stats MMOItems/AuraSkills. Valida duración, combos, cancelaciones, sinergias y cantidad de piezas. No cambies identidad de armas ni conjuntos entregados.
- **Trabajos:** profesiones y contratos comprados por rango; las recompensas, tesoros, cooldowns y requisitos son configurables. No confundir permiso de profesión con nivel minero.
- **Minería:** hitos 1/3/6/12/15/18/22/25 según README; Taller del Minero nativo (64:1, pulverización por pago verificado); Duendes/Prospectores con personalidad, Paladio y Mitrilo, Luck acotado, herramientas de utilidad y gemas. Evita explotación de menas colocadas y daño a regiones. Respeta los IDs `TOOL:TALADRO`, `TOOL:PICO_GEMAS`, `MATERIAL:PALADIO` y `MATERIAL:COMPACTADOR` existentes. Si mejoras una herramienta, verifica que conserva las gemas compatibles consumidas de origen sin duplicarlas.
- **Slayer y mazmorras:** misión, progresión, permisos de receta, jefe, recuperación, extracción y cierre deben respetar el estado real de la expedición. No duplicar entidades o recompensas. Dungeons+ es autoridad del cierre de sus sesiones.

## 7. Rendimiento y seguridad

- Diseña para Paper y concurrencia: evita escaneos globales, bucles por tick, entidades excesivas, relecturas constantes y escrituras sincrónicas masivas. Prefiere eventos, índices, cachés acotadas, trabajo solo sobre sesiones activas y guardado agrupado con snapshots consistentes. Mide antes de afirmar mejoras.
- Solo usa asincronía cuando sea segura; no manipules Bukkit/Paper, mundos, inventarios, jugadores o entidades fuera de sus hilos permitidos. Cancela tareas y limpia listeners, displays, referencias y cachés al apagar, recargar, salir o cambiar de mundo.
- Verifica permisos, inventarios llenos, dinero real del proveedor, clics/drag/hotbar, reentrancia, estados, cooldowns, límites y protección de regiones. Verifica también a la hora de consumir o entregar, no solo en la previsualización.
- Prevén duplicados, retry, doble clic, desconexiones, reinicios, fallos de disco/proveedor y conflictos de transacciones. Si falla una comprobación crítica, no entregues premios ni consumas recursos de forma insegura.
- No ejecutes comandos privilegiados a partir de texto arbitrario del jugador. Valida entradas, rutas, IDs y valores. No expongas secretos, datos privados ni componentes internos en mensajes.
- **No pruebes operaciones destructivas o exploits en producción.** Reproduce en staging autorizado con backups y cuentas de prueba. Si hay operación irreversible de producción, solicita autorización específica.

## 8. Flujo obligatorio de trabajo

1. **Inspeccionar:** repositorio, rama, commit, fuentes, configuración, versiones, documentación y pruebas reales. Delimita el estado inicial.
2. **Evaluar:** valor jugable, complejidad, riesgos, dependencias y alternativas. Cuestiona problemas reales, sin confirmaciones triviales.
3. **Diseñar:** acción del jugador, reglas, estados, errores, persistencia, integración y experiencia visual. La solución más sencilla que resuelve bien el objetivo.
4. **Implementar:** cambios acotados, legibles y completos, con YAML, mensajes, permisos, migraciones, documentación y regresiones necesarias. No presentar pseudocódigo o simulación como funcionalidad terminada.
5. **Validar:** revisa YAML y claves duplicadas, pruebas unitarias/de reglas, regresiones, casos borde, compilación Gradle y CI del commit exacto. Pruebas reales en Paper de interacción, integraciones, rendimiento, reinicio y seguridad cuando haya entorno. Distingue cada nivel de evidencia; reporta lo que no pudiste ejecutar.
6. **Publicar:** todo cambio de código o configuración debe quedar publicado en GitHub. Comprueba estado y rama remota, crea commits descriptivos y sube sin force push. Verifica el commit publicado. No subas secretos, artefactos temporales ni archivos generados innecesarios. No fusionar a `main` sin revisión/autorización. Si falla publicación, conserva el trabajo e informa el bloqueo sin inventar éxito.
7. **Entregar y mantener continuidad:** resume experiencia del jugador, cambios, archivos, rama, commit, pruebas/fallos y pendientes. Si cambia el estado del proyecto, actualiza README/auditoría/roadmap correspondiente con evidencia. Mantén las reglas estables en AGENTS; no entierres la información esencial en un historial interminable.

**Definición de terminado:** función jugable clara, datos protegidos, errores manejados, integración coherente, verificaciones disponibles superadas, publicación comprobada. Nunca declares un release certificado mientras falten pruebas críticas de integración o persistan bloqueos. Identifica supuestos y pendientes explícitamente.

## 9. Handoff entre sesiones

Al retomar trabajo, lee `docs/ESTADO_Y_AUDITORIA.md`. Separa incidentes confirmados de riesgos por validar. Prioriza P0/P1 de seguridad, datos e integración sobre ampliaciones si el usuario no indica otra cosa. Registra tras cambios sustanciales: fecha, rama y commit; sistemas tocados; decisiones y motivos; pruebas (comandos y resultado); pendientes verificables; pasos de migración/rollback. Preserva los acuerdos útiles y elimina afirmaciones que ya no sean ciertas.

**Máxima:** complejidad en posibilidades, sencillez en uso y orden en implementación.
