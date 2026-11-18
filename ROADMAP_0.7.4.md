# Roadmap SpectreCore 0.7.4 — Desarrollo sin reinicios de Hydev

**Objetivo:** preparar una única release candidate acumulativa, probada con CI, para instalar en una ventana de mantenimiento. No cambiar el JAR activo ni reiniciar Paper durante estas fases.

**Base de trabajo:** `main` = 0.7.3-m3-alpha; rama `feature/auraskills-ultimo-juramento`; PR #2 = 0.7.4-aura-alpha (CI compilación y artefacto aprobados). No fusionar a `main` ni etiquetar como estable antes de aceptación ingame.

## Reglas no negociables

1. Las estadísticas de AuraSkills usan modificadores **temporales**, no alteran niveles/XP, y deben eliminarse al terminar la habilidad o perder acceso al set, morir, desconectarse o cambiar de mundo. Sin bonos duplicados si se reactivan.
2. Si un bono es **por objetivo**, no sustituirlo por un Strength global: afectaría a otros enemigos y podría cambiar PvP. Si un bono es global, comprobar su alcance sobre jugadores, mobs hostiles y entidades pasivas.
3. No suponer equivalencia universal Strength/daño: +45 Strength ≈ +15 % normal con Umbría en la medición de Hydev (24,8 → 28,5), pero puede variar con configuración, nivel y armas. Separar golpes normales, críticos de MythicLib, críticos vanilla y combinación de críticos.
4. El respaldo sin AuraSkills no puede sumar el antiguo multiplicador **además** de Strength. Guardar la semántica de los porcentajes configurados.
5. Configuración modular bajo `modules/equipamiento/config.yml`. Mantener todas las otras configuraciones y datos intactos. Java 21 y Paper 1.21.10.
6. **No reinicios**: usar GitHub Actions para compilación, análisis estático y regresiones. `/spectrecore reload` no instala cambios Java.

## Hito A — Puente y Último Juramento (en PR #2)

- [x] Dependencia AuraSkills API en `compileOnly`.
- [x] Bridge con Strength temporal +45 y expiración de seguridad.
- [x] Suprimir el bono anterior +15 % si el modificador está aplicado; mantener fallback legado.
- [x] Limpiar modificadores en expiración, desconexión, muerte, cambio de mundo, pérdida de armadura y apagado.
- [x] CI Gradle y artifact para la revisión actual.
- [ ] Verificación ingame de Strength, daño, Blink +10 %, extensión 0,6 s por golpe y máximo 10 s, +20 % daño recibido.
- [ ] Verificar comportamiento en PvP/entidades pasivas y no aplicación doble bajo MythicLib/MMOItems.
- [ ] Verificar desconexión/crash y respaldo sin AuraSkills en staging.

## Hito B — Herencia Dracónica

Contrato actual del código:
- Bonificación pasiva ofensiva = +3 %; activa = **+6 % adicional** (total objetivo +9 %).
- Reducción pasiva entrante = 3 %; activa = 6 % adicional (objetivo = 9 %).
- Activación por dos golpes válidos o daño hostil recibido.
- Cura 4 HP (2 corazones) sin superar vida máxima.
- Duración base 10 s; arma MMOItems `SWORD/DRAGON` puede extender a 12 s.
- CD 45 s, velocidad activa +6 %.

Desarrollo:
- [x] Extender AuraSkillsBridge para estadísticas Strength, Toughness y Speed (API). Toughness/Speed quedan sin integrar en armaduras hasta calibrarlos.
- [x] Preparar Strength pasivo y activo con fallback sin doble daño, **opt-in desactivado por defecto** hasta calibrar. Referencia provisional: +9 Strength para 3 %, +18 adicional para 6 %; requiere comprobación ingame.
- [ ] Evaluar Toughness con configuración real de `stats.yml`, **sin inventar** equivalencia de puntos a reducción porcentual; mantener protección de daño legado hasta calibrar y verificar.
- [ ] Mantener velocidad con atributos Paper hasta determinar equivalencia sin duplicados.
- [ ] Tests de curación, espada DRAGON y cancelación.

## Hito C — Asterión

Contrato actual:
- Furia por golpear/recibir daño, activación por doble Shift a 100/100.
- Combo sobre un **mismo** objetivo hostil, con ventana 50 ticks y máximo 4 acumulaciones; cada una +3 % (hasta +12 %).
- Reiniciar al cambiar de presa, exceder ventana, perder el set o acabar transformación.
- [x] Aislar y testear el ciclo objetivo/combo sin depender de efectos visuales (`AsterionComboRules`, `AsterionComboSmoke`).
- [ ] Verificar daño ingame frente a MythicLib/MMOItems. Instrumentación opcional ya creada: `engine.combat.debug-multipliers: true`; registra original, esperado, evento y final, limitado a un log por segundo/jugador.
- [x] Mantener el multiplicador focalizado por UUID de presa (sin Strength global), con limpieza en vencimiento y cambio de objetivo. **Daño final ingame sigue pendiente.**
- [x] Testear secuencia de 1–6 golpes, cambio de objetivo y caducidad en pruebas puras Java.

## Hito D — Slayer y portales

- [ ] Cobro y verificación de misión Slayer de forma transaccional, con permisos propios; eliminar `player-command-as-op` como puente para jugadores.
- [ ] Evitar cobro si ya hay misión activa, faltan permisos o no puede iniciarse.
- [ ] Validar identificador de instancia único para sesiones concurrentes de Dungeons+; `CRIPTA_PENDIENTE` es compartido.
- [ ] Revalidar toda la ruta del portal: registrar → detectar Minotauro → generar → temporizador → `dungeons end ...` → confirmar salida → limpiar. El cambio de comando Join resolvió un fallo de registro, pero eso no demuestra automáticamente cada paso.

## Hito E — UX

- [x] Implementación experimental de issue #1 (8 presets) con advancement temporal; requiere prueba visual ingame para declararla completa.
- [x] Antiflood y toggles YAML con fallback funcional preparados; **compatibilidad y eventos de avances aún sin validar ingame**.
- [x] Conservar actionbar de estados continuos y utilizar toast para hitos; el pago Vault confirmado usa toast + monto en chat.

## Hito F — Release candidate única

- [x] Build Java 21, validadores YAML, smoke tests y artefacto verificados en ejecución intermedia; repetir sobre SHA final antes de RC.
- [ ] Probar el control pendiente de comandos inválidos de la Prueba 19.
- [ ] Plan de rollback, backups de JAR/YAML/datos, changelog y protocolo de upgrade.
- [ ] **Única ventana de mantenimiento**: detener normalmente, instalar JAR, iniciar una vez y realizar QA ingame.
- [ ] Prueba 17 pendiente: persistencia de Slayer, Resonancia, Cocina, Modelos, Trabajos e integridad de almacenamiento tras el arranque.
- [ ] Spark y MSPT antes/después.
- [ ] Fusionar y publicar solo cuando las mecánicas críticas estén aceptadas.

**No incluye:** mecánicas nuevas fuera de este roadmap; no modificar la economía ni las probabilidades actuales sin solicitud expresa.

## Hito G — Migración de zMenu «Misiones» / «Monarquía Renacida»

- [x] Dos menús de 54 slots rediseñados con misma familia visual (Índice de Misiones / Monarquía Renacida). Conservan las cabezas Base64, precios 200/500/950 y objetivos 20/50/90.
- [x] Los tres contratos y cancelación invocan exclusivamente comandos de jugador `/spectrecore slayer contratar|abandonar`. No usar withdrawals ni `player-command-as-op` en zMenu.
- [x] Preparar compra nativa del `MATERIAL:TELEFONO1` por 500 ↾, validando disponibilidad de MMOItems, inventario y confirmación Vault, sin elevar privilegios.
- [ ] Integrar menús al despliegue sincronizado (los archivos deben reemplazar los inventarios activos de zMenu solo tras instalar el JAR nuevo).
- [ ] Activar explícitamente `contratos.habilitados` y `acceso-remoto.habilitado` en staging, mediante reload si fuera necesario, tras backup.
- [ ] Pruebas de compra de teléfono, no duplicación de objetos, inventario lleno, y errores Vault/MMOItems.
- [ ] Comprobar identificadores de archivos zMenu `misiones` y `slayer_zombie`; ambos deben coincidir con los registrados en el servidor.
