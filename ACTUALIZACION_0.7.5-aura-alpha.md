# SpectreCore 0.7.5-aura-alpha · Guía de actualización

## Cambios de experiencia

- **Armaduras:** catálogo nativo de 54 espacios, estética de Misiones, cinco
  páginas de conjuntos con íconos de bonificaciones y habilidad especial.
  Las dos entradas públicas de Armaduras comparten la misma GUI.
- **Slayer:** la página de progresión muestra el nivel actual en el título,
  un resumen con XP acumulada, siguiente umbral, victorias y 11 niveles.
  Se repara el acceso faltante en instalaciones antiguas de la GUI.
- **Oficios:** nombres visibles y rangos editables en
  `modules/trabajos/config.yml` bajo `presentacion`; estados de contrato
  editables en `ui/trabajos.yml` bajo `labels`.
- **Textos:** sin créditos de plugins ni comandos en los iconos de menús.
  Los mensajes conocidos reciben traducción limpia al actualizar, con respaldo.
- **Slayer y la Forja:** 16 nodos de progreso se conservan para desbloquear
  recetas en una futura Forja. **No impiden utilizar objetos ya obtenidos**.
  Se retiró el instalador anterior de `base.permission`. La Forja todavía
  debe implementar el control de receta; no está conectado automáticamente.

## Sonidos individuales de toast

Archivo: `plugins/SpectreCore/modules/nucleo/ajustes.yml`.

Ejemplo:
```yaml
toasts:
  enabled: true
  intervalo-global-segundos: 3
  eventos:
    furia: true
  presets:
    furia:
      icon: "minecraft:netherite_axe"
      titulo: "<gold>Furia completa"
      descripcion: "<gray>Doble Shift para despertar"
      sonido: "ENTITY_PLAYER_LEVELUP"
      volumen: 0.8
      tono: 1.0
      intervalo-segundos: 0
```

- `sonido` recibe el nombre de un sonido reconocido por Paper o
  `"NINGUNO"` para silenciar el toast.
- `volumen`: 0 a 4; `tono`: 0.5 a 2.
- `intervalo-segundos`: 0 a 3600 entre notificaciones del mismo tipo,
  además del intervalo global.
- **Sonidos, intervalos y activaciones:** recargables con la recarga general.
- **Texto e icono del toast nativo:** se registran al arrancar; necesitan
  reinicio del servidor para cambiarse.
- **Duración visible:** la determina Minecraft, no el servidor. No se altera
  con `intervalo-segundos`.

Hay ocho presets: furia, resonancia, gemas básica/intermedia/superior
y trabajos de minería/agricultura/caza.

## Migración segura de configuraciones existentes

No se sobrescriben traducciones propias de los usuarios. Se revisan únicamente
las frases antiguas exactamente reconocidas y se guarda copia antes del cambio:

- `backups/presentation-v2/modules/.../mensajes.yml`
- `backups/presentation-v2/ui/{misiones,trabajos,mago}.yml`
- `backups/progresion-v1/ui/misiones.yml` si faltaba la página de niveles.
- `backups/trabajos-labels-v1/ui/trabajos.yml` si faltaban etiquetas editables.

Las migraciones son idempotentes: un segundo arranque no vuelve a escribir
copias si los textos ya fueron actualizados.

**Atención:** si se había ejecutado el instalador antiguo de permisos de
equipo, restaurar el respaldo de `SWORD.yml` y `ARMOR.yml`. Este JAR no
modifica esos archivos ajenos al actualizarse.

## Pruebas mínimas de staging

1. Iniciar sin errores. Comprobar versión `0.7.5-aura-alpha`.
2. Abrir Misiones → Progresión Slayer y verificar nivel, XP, victorias y
   recompensas. Hacer clic en el acceso de progresión del catálogo antiguo.
3. Abrir Armaduras desde ambas rutas, cambiar entre cinco conjuntos y leer
   lore de bonos y habilidad; no deben aparecer comandos.
4. Abrir Trabajos y verificar nombres capitalizados, rangos y estados.
5. Probar un toast de Furia o Trabajo con `sonido: "NINGUNO"`, reiniciar
   solo para cambios visuales y recargar para ajustes de audio.
6. Probar con una cuenta sin OP que un objeto de Slayer **no** esté bloqueado
   por su nivel y verificar que la subida de nivel conceda el nodo de receta.

La compilación no equivale a una prueba ingame de los cinco apartados.
