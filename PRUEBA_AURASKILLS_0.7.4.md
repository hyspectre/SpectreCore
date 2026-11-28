# SpectreCore 0.7.4-aura-alpha — Prueba de integración AuraSkills / Último Juramento

**Estado:** rama experimental. No reemplazar el JAR de producción hasta validar en staging.

## Objetivo y alcance

Último Juramento aplica un modificador temporal **+45 Strength** usando la API de AuraSkills 2.3.x. En Hydev, las mediciones con Umbría indicaron ~+15 % de daño normal (24,8 → 28,5), pero **la conversión no es universal** y depende de AuraSkills, MythicLib, MMOItems y de los demás bonificadores activos.

- Modifier ID: `spectrecore_ultimo_juramento_strength`.
- Duración base: 7 s, máximo 10 s; el modificador se elimina cuando expira la habilidad.
- Seguridad: `setNonPersistent()` y expiración de respaldo (> duración máxima); no modifica nivel ni XP.
- Mientras exista el modificador AuraSkills, SpectreCore **no vuelve a aplicar** el +15 % del evento melee.
- Si AuraSkills está ausente, inactivo o no puede cargar al jugador, se utiliza el multiplicador anterior `melee-damage-bonus: 0.15`.
- El +20 % de daño recibido, el Blink +10 % y la extensión +12 ticks por golpe siguen calculándose en SpectreCore.
- Cambio de alcance: Strength afecta el daño procesado por AuraSkills en general, no solo ataques a mobs hostiles. Verificar si esto es deseable para PvP / entidades pasivas.

## Configuración

`plugins/SpectreCore/modules/equipamiento/config.yml`:

```yaml
skills:
  ultimo-juramento:
    melee-damage-bonus: 0.15  # Respaldo sin AuraSkills
    auraskills:
      enabled: true
      strength-bonus: 45.0
```

La nueva clave es opcional para instalaciones existentes: el código tiene valores de respaldo, pero conviene añadirla explícitamente.

## Preparación

1. Respaldar configuración y datos. Probar en un servidor Paper 1.21.10 / Java 21.
2. Confirmar que AuraSkills está activo y es compatible con la API 2.3; utilizar `/spectrecore integraciones`.
3. Eliminar el modificador de calibración manual `hydev_test` si aún existiera; no mezclar otros boosts en la prueba.
4. Tener las cuatro piezas del conjunto Voto y un objetivo hostil con vida suficientemente alta para mediciones; no usar un dummy con regeneración desconocida.
5. Registrar daño normal, crítico, doble crítico por separado. Comparar **con la misma armadura y arma**, antes y durante la habilidad.

## Pruebas de aceptación

1. **Activación:** doble Shift. `/sk modifier list <jugador> strength` debe mostrar el ID `spectrecore_ultimo_juramento_strength` por ~7 s.
2. **Daño:** el daño normal con la habilidad activa debe subir aproximadamente +15 % en la configuración de Hydev. No debe duplicarse a ~+30 %.
3. **Extensión:** un golpe melee válido contra un mob hostil añade 12 ticks (0,6 s), hasta máximo 10 s desde activación. Asegurarse de que la activación dura más de 7 s al golpear, sin reiniciarse indebidamente.
4. **Daño recibido:** durante la habilidad, daño recibido +20 % respecto al mismo escenario antes de la activación.
5. **Sin arma específica:** el +45 Strength de la forma sigue funcionando; la sinergia Blink solo se permite con JURAMENTO_PROFANADO.
6. **Limpieza:** muerte, cambio de mundo, desconexión o retirada de una pieza eliminan el modificador. Comprobar también expiración normal y `/spectrecore reload`.
7. **Reentrada:** una nueva activación después del cooldown no apila modificadores ni deja el anterior.
8. **Respaldo:** cambiar `auraskills.enabled: false`, ejecutar `/spectrecore reload`, y comprobar que el multiplicador original sigue funcionando (sin el modificador AuraSkills).

**Observación de seguridad:** la limpieza explícita ocurre con el ciclo de SpectreCore. El identificador del modificador es no persistente y tiene un vencimiento de respaldo, pero los reinicios/crashes del servidor no sustituyen una prueba real de limpieza y persistencia.

## Herencia Dracónica (prototipo de opt-in; no se activa por defecto)

`skills.sangre-primer-dragon.auraskills.enabled: false` conserva por defecto el daño original.
El código ya permite sumar +9 Strength de forma pasiva y +18 adicionales durante la bendición; si el puente no aplica alguno, conserva el porcentaje anterior correspondiente. Las magnitudes **no están calibradas en el servidor**.

Cuando se haya instalado esta versión en staging, se podrá cambiar `enabled: true` y ejecutar `/spectrecore reload` **sin otro reinicio** para comprobar el nuevo modelo. Hay que medir con el mismo conjunto/arma el daño normal pasivo y activo. Los objetivos de diseño originales son +3 % pasivo y +6 % adicional (total +9 %); las reducciones de daño siguen bajo el motor legado hasta calibrar Toughness. Velocidad se mantiene vía atributo Paper, sin duplicar Speed de AuraSkills. La cura de 4 HP, el arma DRAGON y los temporizadores permanecen sin cambiar.

Probar pérdida de conjunto, muerte, desconexión, CD y expiración: los IDs son `spectrecore_dragon_passive_strength` y `spectrecore_dragon_active_strength`.

## Pendiente

- Medición ingame y compatibilidad de AuraSkills en el servidor real.
- Reparación de Asterión y Herencia Dracónica (fuera del alcance de esta rama).
- Validación de gemas y la Prueba 17 de persistencia general.

## Toasts nativos — nueva integración experimental

Configuración: `plugins/SpectreCore/modules/nucleo/ajustes.yml`, sección `toasts`. Funciona en Paper 1.21.10 usando `UnsafeValues#loadAdvancement`, API **obsoleta y dependiente de la versión**. Se registran únicamente ocho advancements fijos con criterio `minecraft:impossible`, sin recompensas ni anuncios de chat. Su progreso se revoca al poco tiempo y al desconectarse; los avances registrados permanecen en el almacén del servidor entre reinicios, no se borran ni se llama a `reloadData()`.

- `/spectrecore toast prueba furia` — solo administradores; exige jugador en juego.
- `/spectrecore toast prueba resonancia`.
- `/spectrecore toast prueba gema-basica` / `gema-intermedia` / `gema-superior`.
- `/spectrecore toast prueba trabajo-minero` / `trabajo-granjero` / `trabajo-cazador`.
- Los activadores de juego son: carga de Asterión completa, Resonancia lista, gema **realmente entregada**, y pago de Trabajo **confirmado** por Vault. El pago solo enviado mediante comando conserva su texto anterior, sin toast de confirmación.
- `toasts.enabled`, `toasts.eventos.*` y `toasts.intervalo-global-segundos` se recargan con `/spectrecore reload`. Los iconos/títulos/descripciones requieren un arranque posterior; no conviene llamar `reloadData()`.
- El servicio devuelve estado de notificación atendida incluso cuando suprime una repetición por antispam. Así no vuelve a llenar el actionbar con eventos que acaba de filtrar.
- Si la API deja de estar disponible, se conservan los títulos o actionbars anteriores. Furia conserva su indicador actual.

**Aceptación obligatoria en staging:** confirmar toast visual arriba a la derecha; cero mensajes globales de logros, cero recompensas/XP no solicitadas, cero progresos de advancements dejados en los jugadores tras 5 s y reconexión, y sin conflictos con otros plugins que reaccionen a eventos de avances. Probar dos jugadores, ráfagas de eventos y configuración `enabled: false`. **No declarar producción apta sin validar el comportamiento real de Paper.**

## Asterión — combo controlado

Se ha aislado el algoritmo en `AsterionComboRules`: primer golpe fija la presa; siguientes 4 golpes rápidos alcanzan un máximo +12 %; cambio de objetivo o demora mayor que 50 ticks restablecen a cero. Las pruebas deterministas se ejecutan en `tests/AsterionComboSmoke.java`. El **daño final en combate contra MythicLib/MMOItems** todavía debe verificarse ingame: estos tests no prueban otros listeners de plugins.

## Contratos Slayer seguros (opt-in experimental)

Nueva ruta para jugadores con permisos de tier: `/spectrecore slayer contratar revenant 1` (o tiers 2/3).
Nueva cancelación: `/spectrecore slayer abandonar revenant`.

Configuración: `modules/slayer/config.yml`, `contratos.habilitados: true` por defecto **en nuevas instalaciones de 0.7.4**. Los valores anteriores de instalaciones existentes no se sobreescriben. El servidor activo sigue sin cambios. Precios de Revenant preconfigurados según Hordas: T1=200, T2=500, T3=950 y cancelación=200. Necromancer/Spider sin precio: no se pueden contratar por esta ruta hasta configurarlos.

El flujo valida primero: sistema habilitado, categoría/tier, ausencia de otra misión activa, permiso `slayer.<categoria>.tier<tier>`, precio numérico finito entero (0..1.000.000.000). Luego cobra con **Vault confirmado**, no con `eco take` ni `player-command-as-op`. Si falta Vault o el cobro no se confirma, no cambia misión. Si falla guardar, restaura el estado en memoria y solicita reembolso a Vault; registra error crítico en consola si el reembolso falla. **Esto no es una transacción ACID ante cortes eléctricos**: no habilitar en producción sin probar interrupciones y diseñar reconciliación si se requiere garantía estricta de dinero.

**IMPORTANTE — migración de zMenu en la misma ventana**:
- Eliminar acciones del menú que debitan dinero por separado (`eco take`, etc.).
- Reemplazar comandos anteriores `spectrecore slayer activar revenant 1` por `spectrecore slayer contratar revenant 1` ejecutados como jugador normal.
- Usar `spectrecore slayer abandonar revenant` para cancelación, sin otra resta de saldo.
- Evitar `player-command-as-op`. Comprobar que el menú respeta permisos y que el módulo cobra solo una vez.
- **No migrar los accesos de NPC/Teléfono Rojo mientras el servidor usa 0.7.3**: los nuevos menús Java aún no existen allí. Los dos YAML de zMenu ahora son solo referencia histórica; los inventarios se generan en `MissionsMenu`.

Pruebas obligatorias: sin saldo (sin misión ni deducción), sin permiso, misión ya activa, tier inválido, tier válido, cooldowns, dos pulsaciones rápidas, dos jugadores simultáneos y reinicio. Verificar que la misión y saldo se mantienen coherentes; guardar backup de `slayer-data.yml`. Si hay cobros pendientes o dudas durante la ventana de mantenimiento, establecer temporalmente `contratos.habilitados: false` y `acceso-remoto.habilitado: false` y ejecutar `/spectrecore reload`. No abrir los accesos antiguos de zMenu simultáneamente.
