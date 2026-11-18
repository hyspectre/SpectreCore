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

## Pendiente

- Medición ingame y compatibilidad de AuraSkills en el servidor real.
- Reparación de Asterión y Herencia Dracónica (fuera del alcance de esta rama).
- Validación de gemas y la Prueba 17 de persistencia general.
