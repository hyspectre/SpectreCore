# Sesión única de pruebas — SpectreCore 0.7.3-m3-alpha

**Objetivo:** aprovechar **un solo arranque de un servidor de staging** y, cuando sea posible, **un segundo arranque de staging** para comprobar persistencia. No ejecutar tests de caídas deliberadas sobre Hydev en producción.

## Preparación (sin reiniciar Hydev)

1. Reserva una copia de prueba de Paper **1.21.10**, Java **21**, y un mundo de prueba. **No copies datos personales innecesarios de jugadores**.
2. Copia fuera del servidor `plugins/`, mundos relevantes, `plugins/SpectreCore/`, `plugins/MMOItems/`, `plugins/AuraSkills/`, configuraciones MythicLib/MythicMobs/ItemsAdder y las bases de datos que apliquen. Verifica que puedas restaurarlas.
3. Compila m3 con GitHub Actions o Gradle; **no uses el ZIP como un JAR**. Revisa que el artefacto indique `0.7.3-m3-alpha`.
4. Deja **un solo** `SpectreCore-*.jar` dentro de `plugins/`, retira las versiones anteriores de este plugin (no se borran sus datos). Evita JAR de SpectreSkills, SpectreCooking o HydevBotania simultáneos.
5. Instala las mismas versiones de los plugins base que se usan en Hydev, si están disponibles, y permite los permisos `spectrecore.admin` solo a operadores de prueba.
6. Mantén `plugins/SpectreCore/diagnostics.yml` con `enabled: false` inicialmente. Para medir contadores, cámbialo a `true` y ejecuta `/spectrecore reload` **tras comprobar que m3 ya inicia sin errores**.
7. **No borres** `cooking/stations.yml`, `cooking/claims.yml`, `activity-pending.yml` ni `models/placements.yml`. Si están dañados, recupéralos de respaldo antes de continuar.

## Arranque de staging (primer y principal inicio)

- [ ] `plugins/SpectreCore` se inicializa sin excepciones ni incompatibilidad de Paper.
- [ ] La consola informa la versión `0.7.3-m3-alpha`.
- [ ] `/spectrecore modulos` muestra ocho categorías/módulos esperados y sin cambios imprevistos.
- [ ] `/spectrecore integraciones` reconoce correctamente los plugins instalados (puede haber faltantes opcionales).
- [ ] `/spectrecore diagnostico` muestra estado de Cocina, cofres y modelos. **Señal de alerta:** `storage=false`, excepción o recuentos negativos inesperados.

## GUI y `/mago` (2–5 minutos)

Ejecutar como **jugador normal**, sin permiso de administración:

1. `/mago` → debe abrir un **Dialog** con RPG, Profesiones, Guías; no un libro.
2. Entrar RPG → Armaduras, Habilidades, Mascotas/Encantamientos/Accesorios (estas tres son informativas, no funcionalidad jugable todavía).
3. `/mago armaduras` → guía de los cinco conjuntos en Dialogs; comprobar que coincida con los datos YAML.
4. `/mago recetas` → GUI del recetario actual; volver y cerrar sin generar objetos ni alterar inventarios.
5. Entrar Profesiones → Cocina, Trabajos, Agricultura; Guías → Slayer, Tesoros, Resonancia, Portales, Actividades.
6. `/mago pagina_inexistente` → error amigable, sin stack trace.
7. `/spectrecore modelo colocar ejemplo` y `/spectrecore diagnostico` como jugador normal → **denegado**.
8. Abrir y cerrar `/mago` muchas veces → sin duplicaciones de GUI ni lag visible.

## Cocina: duplicación, regresión y permisos (10–20 minutos)

Materiales: caldero de agua, fogata encendida debajo, mechero, esponja, pala de madera, cuencos e ingredientes de **una receta realmente existente** de `plugins/SpectreCore/modules/cocina/recetas.yml`. Debe estar configurada la recompensa MMOItems. Trabajar en mundo de prueba y, para errores, registrar consola.

1. Mira el caldero y haz clic con el mechero: una estación visual, **no dos**. Repite clic: no debe registrarse otra.
2. Intenta crear cocina sin fogata, sin encenderla, o demasiado cerca de otra estación: debe rechazarse según YAML.
3. Arroja un ingrediente de receta: debe descontar **solo la cantidad aceptada**. Comprueba que el resto vuelve al jugador o al suelo; duplicación = **BLOQUEADO**.
4. Intenta arrojar un ingrediente incorrecto y un objeto custom con metadata: no deben ser consumidos cuando la opción está desactivada.
5. Completa ingredientes en orden, remueve con la pala de madera, espera la ventana de listo y sirve con un cuenco: exactamente una porción por cuenco, salvo modo creativo. Prueba inventario lleno: no perder ni duplicar objetos.
6. Prueba servir dos veces rápidamente, dos jugadores pulsando el mismo caldero y limpieza con esponja mientras el otro sirve. Ningún producto duplicado.
7. Limpia una preparación quemada. Comprueba que la esponja no pueda reembolsar ingredientes de un lote que ya entregó una ración.
8. Como operador, `/spectrecore cocina transacciones` → **cero** transacciones pendientes tras operaciones completas normales. Si aparece alguna, **no ejecutes `/resolver` sin inspeccionar** estación, inventario y logs; anota UUID de claim.
9. WorldGuard: crea una región protegida donde el jugador NO pueda construir. Intenta crear/retirar estación allí: denegado. Si todo está denegado incluso con permisos, registra versión WG y la excepción de consola.

### Ensayo adversarial de fallo de disco — solo staging

- Con servidor **apagado**, prepara **una copia** de `cooking/claims.yml` y `stations.yml` en un directorio externo. NO renombres/sustituyas estos archivos mientras el servidor está ejecutándose.
- En una copia de staging *desechable*, prueba con `cooking/claims.yml` deliberadamente malformado **antes del inicio**. Arranca: las operaciones deben quedar bloqueadas, no limpiar automáticamente el archivo. Restaura después el original y reinicia staging para seguir. **Este ensayo requiere otro arranque, por eso es opcional y no forma parte de la sesión principal.**

## Cofres y economía (5–10 minutos)

1. Confirma que las tasas y premios de `modules/actividades/config.yml` coinciden con la configuración original.
2. Como operador de pruebas, ejecuta el comando unificado de abrir cofre (usa Tab/comandos de ayuda de tu instalación; no repetir en servidor público). Completa la animación y recibe un solo objeto.
3. Desconecta a un jugador durante la animación: al volver, completar una sola vez, si el premio quedó pendiente.
4. Inspecciona `activity-pending.yml` (solo lectura): ninguna reclamación `claims.*` de una operación normal completada.
5. Con economía Vault configurada, revisa que un premio económico cambie el saldo **una sola vez**. Si la API falla, no se debe ejecutar un comando alternativo arbitrario.
6. No intentes generar decenas de premios en producción; usa copia de prueba y observa saldo/inventario antes y después.

## Modelos con Display Entities y Base64 (5–10 minutos)

Como operador:

1. `/spectrecore modelo vista ejemplo` → aparece modelo temporal con tres piezas (magma, cabeza y texto); desaparece aproximadamente a los 10 segundos.
2. `/spectrecore modelo colocar ejemplo` → modelo persistente, indica el UUID de instancia.
3. `/spectrecore modelo lista` → muestra las instancias cercanas con sus UUID.
4. Comprueba la interpolación BOB de la cabeza. Comprueba que no se fuerzan chunks lejanos a cargar al crear o restaurar.
5. `/spectrecore modelo quitar UUID` (sustituye UUID por el que muestra el sistema) → desaparece y no regresa.
6. WorldGuard: prueba una región protegida con usuario sin BUILD; colocar/retirar debe denegarse.
7. Para probar **cabeza Base64**, duplica `plugins/SpectreCore/models/ejemplo.yml` como `cabeza-prueba.yml`, sustituye `base64: ''` por un valor real de propiedad Minecraft `textures` de una cabeza conocida y ejecuta `/spectrecore modelo vista cabeza-prueba`. Una URL o el PNG directamente **no** es Base64 GameProfile. Nunca uses Base64 obtenido de fuentes desconocidas para otras URLs.
8. Pon un valor Base64 inválido en ese archivo de prueba → error claro, sin spawn parcial ni caída del servidor. Restablece o elimina ese archivo después.

## Configurabilidad, traducción y `reload` (5–10 minutos)

1. Cambia `ui/mago.yml` por ejemplo `pages.inicio.title` y un botón. Ejecuta `/spectrecore reload`. Vuelve a `/mago` → cambios aplicados.
2. Cambia un mensaje en `modules/cocina/mensajes.yml`; prueba su evento correspondiente sin cambiar la mecánica.
3. Cambia `models/settings.yml` a un valor permitido y vuelve a cargar; comprueba límites. No hagas pruebas agresivas de spam en el servidor principal.
4. **Importante:** introduce temporalmente un valor inválido (por ejemplo, un destino de botón que no existe). Ejecuta `/spectrecore reload` y comprueba que se conserva el último menú válido. Restaura el YAML.
5. Verifica que la edición de un valor no requiere recompilar el código y que no cambia el contenido de otro módulo.

## Diagnóstico y carga (5–10 minutos)

1. En `diagnostics.yml`, activa `enabled: true`; `/spectrecore reload`; `/spectrecore diagnostico` muestra métricas y contadores.
2. Con dos jugadores, crea/retira estaciones y modelos repetidamente (sin exceder límites). Revisa errores y conteos; no provocar spam extremo en producción.
3. Usa Spark/Timings admitidos por Paper para medir MSPT, cargas de chunks y memoria con el mundo habitual. Compara con una medición de la v0.7.3 anterior; **no inventar números**.
4. Vuelve a dejar `diagnostics.yml` en `enabled: false` cuando terminen los ensayos si no se necesita telemetría continua.

## Persistencia (segundo inicio, opcional y programable)

**En STAGING únicamente:** deja una cocina registrada, un modelo colocado y (si aplica) un cofre pendiente; ejecuta `stop` normal en consola (no `/reload`). Arranca staging nuevamente. Esperado:

- Cocina y progreso recuperados, sin ingredientes duplicados.
- Instancia `modelo` restaurada al cargar chunk, sin displays duplicados.
- Cofre pendiente reanuda una vez o queda en cuarentena si se había empezado a entregar; NO hay segundo premio automático.
- `/spectrecore diagnostico`: storage=true, sin reclamos inesperados; cualquier `claims.yml` o cofre `claiming` requiere evaluación manual.

## Reporte de bugs (formato)

```text
SpectreCore: 0.7.3-m3-alpha
Paper / Java / plugin externo y versión:
Comando y mecánica:
Pasos exactos:
Esperado:
Observado:
Afecta objetos, dinero o datos: sí/no
¿Reproducible?: sí/no/no sé
Jugadores implicados:
UUID de claim, si existe:
Primer error de consola y 10–20 líneas antes/después:
Spark/MSPT (si medido):
```

## Condición de parada

**No publicar** si: Paper no compila o no carga, se duplican recursos, se ignoran protecciones WorldGuard, hay pérdidas de inventario, hay errores de migración de YAML, se generan displays huérfanos o aparecen claims ambiguos sin política de reconciliación.

La corrección de un bug debe incluir una prueba de regresión nueva cuando se pueda automatizar.
