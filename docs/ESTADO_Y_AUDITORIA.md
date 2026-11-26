# Estado técnico y auditoría de SpectreCore

> Registro de continuidad. Base de evidencia: **26/11/2026**, no un certificado de seguridad. Actualiza estados al corregir o reproducir cada hallazgo, con commit y pruebas; no des por resuelto un punto por haber escrito una propuesta.

## Referencias de trabajo

- Repositorio: `hyspectre/SpectreCore`.
- Rama acumulativa auditada: [`fix/0-9-mineria-integraciones-hydev`](https://github.com/hyspectre/SpectreCore/tree/fix/0-9-mineria-integraciones-hydev).
- Base de código: [`73eb5c4dc60dce3f37743e4238e57d25ffd44c7c`](https://github.com/hyspectre/SpectreCore/commit/73eb5c4dc60dce3f37743e4238e57d25ffd44c7c) · `0.9.0-dev8`.
- [PR #11](https://github.com/hyspectre/SpectreCore/pull/11), borrador sobre PR #10.
- `main` tenía código histórico `0.7.3-m3-alpha` ([`da2be87`](https://github.com/hyspectre/SpectreCore/commit/da2be870f5a0eb60ea7ef5ed68796102bc6a0c4c)); la rama auditada acumulaba 855 commits posteriores sin divergencia de código en ese momento. La actualización de documentación no cambia esas versiones; inspecciona HEAD y comparación actual.
- Target: Paper 1.21.10, Java 21. **No instalar como release de producción sin certificación.**

## 1. Auditoría: bloqueos y riesgos

Prioridades: **P0** pérdida de datos/configuración o bloqueo de release; **P1** seguridad, integridad, integración o jugabilidad crítica; **P2** deuda de mantenimiento/UX/rendimiento. Todos los puntos están abiertos en la base auditada. Rutas Java relativas a `src/main/java/dev/hydev/spectrecore/`; números de línea corresponden al commit base.

| ID | Prioridad | Estado de evidencia | Observación y criterio de cierre |
|---|---|---|---|
| AUD-01 | P0 | Confirmado por inspección | `mining/MiningModule.java:508–535`: carga tolerante de `mining-data.yml`; una lectura fallida puede generar estado vacío que se sobrescribe. Caso análogo en `treasure/TreasureCooldowns.java:20–51`. Cerrar con lectura estricta, bloqueo de guardado si datos ilegibles, uso del almacenamiento común/migración no destructiva y regresión de YAML dañado. |
| AUD-02 | P0 | Reproducido localmente | `integracion-mmoitems/mineria-v2/instalar_mineria.py:27–74`: el parser de secciones no detecta claves de ítems entre comillas. Genera duplicados semánticos y el YAML puede perder propiedades originales. Cerrar con prueba de claves equivalentes citadas/no citadas, rechazo de entrada ambigua y validación de salida antes de escribir; preservar archivos ajenos y respaldos. |
| AUD-03 | P1 | Confirmado por inspección | `mining/MiningPermissionSync.java:85–102`: al sincronizar hitos elimina denegaciones explícitas y solo concede los hitos elegibles, sin distinguir origen. Preservar restricciones administrativas y conciliar permisos obsoletos sin borrar grants externos; probar perfiles existentes, permisos contextuales, denegaciones y reducción de nivel. Revisar el uso reflejado del proveedor interno AuraSkills (`115–128`). |
| AUD-04 | P1 | Riesgo de fallo intermedio, no exploit demostrado | `relicario/RelicarioStore.java:77–96` y `RelicarioModule.java:594–604`: se guarda desbloqueo antes de retirar el ítem. Si se cae el servidor en ese intervalo, el objeto restante podría transferirse a otro jugador. Cerrar con estrategia de recuperación/idempotencia y pruebas de caídas y transferencia; no perder el objeto pagado ni duplicar desbloqueos. |
| AUD-05 | P1 | Comprobaciones faltantes por inspección; probar ingame | `mining/MiningModule.java:197–224,247–310`: el descubrimiento comprueba el hito arcano, la apertura de geoda no revalida nivel/permiso ni consulta directamente protección de región. Revalidar al abrir, con llave, caducidad, propietario y espacio; cubrir cambio de permiso, región protegida, relog y entrega única. Revisar también consistencia ante fallo entre consumo de llave, marcador y recompensa. |
| AUD-06 | P1 | Riesgo de integración; no exploit demostrado | `mining/MithrilToolModule.java:164–179`, `MithrilDrill.java:96`, `StoneDrill.java:138`: las excavaciones adicionales usan `breakNaturally` o `setType`, no necesariamente el flujo completo de las protecciones externas. Verificar hooks de Paper, WorldGuard y otras restricciones realmente instaladas; validar al ejecutar, no cargar chunks y no otorgar XP/tesoros/pagos adicionales no previstos. |
| AUD-07 | P2 | Confirmado por inspección | `MiningGems.java:86–125`, `MiningModule.java:197–310,414`, `MiningForgeUpgrades.java:55–69`: porcentajes, duraciones, textos, sonidos y valores jugables nuevos directamente en Java. Mover reglas de gemas/geodas/mejoras y feedback a YAML validado, sin alterar balance ni IDs por accidente; ampliar validador de presentación a textos realmente emitidos. |
| AUD-08 | P2 | Patrón confirmado; impacto no medido | `CookingModule.java:794–815,1116–1145`: recorre estaciones, busca jugadores cercanos y guarda archivo completo cada cinco segundos sin exigir cambios. Indexar estaciones activas/cargadas, evitar escrituras sin cambios y medir MSPT; retener persistencia y diario de claims. |
| AUD-09 | P2 | Patrón confirmado; impacto no medido | `SpectreCorePlugin.java:181–186`, `core/PassiveBonuses.java:38–46` y skills de equipamiento: revisiones repetidas sobre todos los jugadores (default 2 ticks). Reducir a cambios/estado activo y tareas con límites, conservando precisión de combos/bonificaciones. Medir y comparar rendimiento. |
| AUD-10 | P2 | Deuda de estructura | Clases grandes que mezclan reglas, persistencia, integración y presentación; extraer responsabilidades cuando tengan beneficios verificables y mantener compatibilidad. No refactorización masiva sin pruebas. |
| AUD-11 | P2 | Diferencia de diseño documentada | `guide/MagoMenu.java:246–297` usa inventarios; Dialogs son la preferencia para navegación y explicaciones cuando convenga. Mantener GUI de ítems y menús acordados; cualquier migración visual debe ser intencionada. |

### Reproducción de AUD-02 (sin tocar un servidor)

El instalador se puede importar como módulo Python mediante `runpy.run_path` y probar `merge_items` con estos textos:

```yaml
# Catálogo original válido:
"MITRILO":
  base:
    material: DIAMOND
    custom-model-data: 123
```

Al fusionar un fragmento cuyo ID se escribe `MITRILO:` sin comillas, la rutina actual lo clasifica como nuevo y añade una segunda clave de igual valor. En PyYAML con carga tolerante prevaleció la última definición y desapareció `custom-model-data`. La prueba debe exigir reconocimiento seguro de la equivalencia o rechazo explícito antes de escribir. Los respaldos no sustituyen la validación.

## 2. Estado por sistemas

| Sistema | Estado observado en dev8 | Próxima verificación |
|---|---|---|
| Cocina | 15 recetas / 45 calidades; estaciones, reclamos y guardado | Persistencia al reiniciar, claims interrumpidos, inventario lleno; costo de muchas estaciones |
| Equipamiento | 5 sets, pasivas y habilidades | Combos Asterión, daño/extendido de Último Juramento, daño/curación de Dragón y sinergia Dracoreliquia |
| Relicario | Catálogo base de 37 objetos, 9 familias, límites e hitos | Repetidos, consumo con caída, aplicación simultánea de atributos y limpieza |
| Trabajos | 3 profesiones, contratos, tesoros, encargos y diario | Antiplace, permisos, fondos, Vault/LuckPerms, conciliación y recuperación |
| Minería | Progresión 1–25, herramientas, gemas, geodas, encuentros y talleres | AUD-01/02/03/05/06/07, identificación de menas, herramientas, recarga y gemas |
| Slayer | Misiones, niveles, contratos y desbloqueos | Progreso, recuperación, permisos de recetas, XP y recompensas sin duplicación |
| Mazmorras | Portal condicionado a sesiones, control de salida | Registro al entrar, aparición tras Minotauro, extracción real en Dungeons+, desconexiones |
| El Mago | Menús nativos independientes y configurables | Inmersión, navegación, categorías y ausencia de IDs/comandos visibles; AUD-11 |

Regresiones históricas de versiones anteriores no equivalen a pruebas de dev8. Se conservan como objetivos de revalidación: Sangre del Laberinto (combo/ruptura), Voto del Deshonrado (daño, duración y daño recibido), Herencia Dracónica (bonificaciones, curación y sinergia), portal (registro de sesión, creación y salida). No asumir que están corregidas sin repetir las pruebas sobre la revisión que se va a desplegar.

## 3. Integraciones: qué falta

- **AuraSkills:** archivo de ocho hitos con permisos reales y mensajes. Verificar perfiles retroactivos, recompensas del menú, niveles y Luck; la sincronización retroactiva requiere AUD-03. No duplicar nivel ni menú. Distinguir contratos pagados.
- **MythicMobs Free:** Duende y Prospector definidos en `HYDEV_MINERIA.yml` y `HYDEV_MINERIA_SKILLS.yml`. Cargar ambos, verificar IA, persecución, evasión, habilidades, telegráficos, drops, despawn, cap y regiones. Un YAML sintáctico no garantiza que la versión instalada soporte todas las mecánicas.
- **MMOItems:** archivos nuevos en `mineria-v2/`, con Forja Minera de 27 recetas en la revisión auditada. No instalar fragmentos anteriores y nuevos de forma indiscriminada. Probar cambios del catálogo, recetas, permisos y compatibilidad con tipos/IDs reales; reproducir transferencia de ranuras/UUID de gemas e historial de stats al mejorar una herramienta, incluso con inventario lleno o cancelación.
- **NPC Minero/Forja y Dungeons+:** el servidor real y sus configuraciones no están en el repo. Inspeccionar configuración concreta antes de cambiar callbacks o crear estaciones; verificar NPC y cierre de sesión efectiva. No afirmar instalación realizada.

## 4. Evidencia de la auditoría 26/11/2026

| Verificación | Resultado |
|---|---|
| Fuentes y archivos recuperados | 241 archivos de fuentes, integración, pruebas y documentación relevante; 96 archivos Java, 15.438 líneas |
| YAML | 81 archivos analizados; sin errores sintácticos ni duplicados en los archivos originales |
| Python | 18 validadores ejecutados y aprobados; prueba de instalador aprobada en la suite existente, con caso adicional AUD-02 fallido |
| GitHub Actions | Dos ejecuciones exitosas para `73eb5c4`, con pruebas Java puras, sintaxis, Gradle, empaquetado y artifacts; [ejecución principal](https://github.com/hyspectre/SpectreCore/actions/runs/45679811533) |
| Gradle / Java en entorno local de auditoría | No ejecutados localmente: faltaban `javac` y Gradle. El resultado de compilación proviene de CI del commit exacto |
| Paper real | No ejecutado en esta auditoría; no hay certificación ingame ni mediciones MSPT |

Principales tamaños aproximados: `CookingModule` 1.533 líneas; `SlayerModule` 828; `RelicarioModule` 621; `SpectreCorePlugin` 611; `MiningModule` 537. El objetivo es cohesión real, no un límite arbitrario de líneas.

## 5. Plan de estabilización (propuesta, no trabajo ejecutado)

La auditoría recomienda **0.9.0-rc1** de estabilización antes de sumar mecánicas. No cambiar la versión del plugin automáticamente por este documento.

- [ ] Resolver AUD-01 y AUD-02 con regresiones reproducibles y preservación de datos.
- [ ] Resolver AUD-03/04/05 y comprobar AUD-06 en entorno autorizado; reforzar transacciones.
- [ ] Completar configuración de reglas y UX mineras; eliminar fuentes de verdad duplicadas.
- [ ] Reducir tareas periódicas y escrituras innecesarias, con medición comparable.
- [ ] Ampliar pruebas unitarias/contratos de las clases y rutas añadidas en dev8, incluidos casos de gemas, geodas, instalación y Forge.
- [ ] Completar validación ingame y certificar todos los sistemas.
- [ ] Actualizar documentación y comprobar build, commit, migración y rollback; integrar ramas únicamente tras revisión/autorización del propietario.

### Puerta de release

Antes de etiquetar como estable, comprobar y registrar:

- [ ] YAML válido y sin duplicados, datos corruptos protegidos, migración y respaldo probados.
- [ ] Compilación CI y pruebas de regresión en el commit candidato exacto.
- [ ] Pruebas de juego de todas las mecánicas, permisos/menús, integraciones, NPC y bosses.
- [ ] Sin duplicación ni pagos/recompensas indebidos ante inventario lleno, clics simultáneos, disconnect, muerte, restart, fallo de persistencia o proveedor no disponible.
- [ ] WorldGuard y protecciones reales respetadas; chunks y entidades limpias al salir, apagar y recargar.
- [ ] Perfil de rendimiento en escenarios representativos (MSPT, número de tareas/entidades, costo de escritura/lectura, jugadores concurrentes). Registrar escenario y valores, no inventar targets o mediciones.
- [ ] Migración reversible, JAR/paquete de integración ordenado, pasos claros de instalación y rollback.
- [ ] Estado, rama, commit y artefactos verificados; aprobación para publicar en producción.

## 6. Registro de continuidad

Añade una entrada al cerrar cambios sustanciales; conserva los resultados históricos relevantes, actualiza tablas/checklists si hay evidencia nueva y marca como obsoletos los hallazgos superados.

| Fecha | Rama / commit | Estado | Evidencia o pendientes |
|---|---|---|---|
| 26/11/2026 | `fix/0-9-mineria-integraciones-hydev` / `73eb5c4` | Auditoría de solo lectura | Hallazgos AUD-01 a AUD-11 abiertos; CI aprobado, pruebas ingame pendientes. |
| 26/11/2026 | `main` y rama de desarrollo | Documentación de onboarding | README, AGENTS y este registro. No modifica código, balance, versión ni estado de certificación. |

**Para la siguiente IA:** elige la base correcta, lee las reglas en AGENTS, comprueba el estado remoto y trabaja con evidencia. Actualiza aquí los cambios y pendientes reales; no reinicies proyectos ni supongas que un historial completo equivale a una release.
