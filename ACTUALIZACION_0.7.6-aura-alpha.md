# SpectreCore 0.7.6-aura-alpha · Experiencia de jugador

Esta versión amplía la 0.7.5 sin conectar menús de categorías independientes.

## 1. Subidas de nivel Slayer

El avance de Monarquía Renacida se confirma contra la XP acumulada.
Después de **guardar** el progreso, el sistema concilia los permisos
de desbloqueo de **recetas**. Solo al confirmar el guardado de permisos se
envía una notificación de subida de nivel y un mensaje de chat con las
recompensas alcanzadas, **sin entregar ni restringir objetos**.

- Toast: `toasts.presets.slayer-nivel` en
  `modules/nucleo/ajustes.yml`; texto, icono, sonido, volumen, tono e intervalo.
- Recompensas visibles: `progresion.revenant.recompensas."1"..."11"` en
  `modules/slayer/config.yml`. Son descripciones, no conceden permisos.
- Textos de subida y error: `modules/slayer/mensajes.yml`.
- Si el guardado de permisos falla, se muestra un aviso de sincronización
  pendiente y no se anuncia falsamente el desbloqueo de recetas.
- Un salto de varios niveles produce un único aviso con las nuevas recompensas.
- Los permisos se mantienen persistentes y se reintentan al entrar, sin
  repetir avisos por inicios de sesión.

**La Forja aún debe consultar los permisos de receta**. Este cambio no
implementa un nuevo menú de fabricación.

## 2. Progresión Slayer visual

`ui/misiones.yml` incorpora `progresion-visual`:

- Nueve cristales en los espacios 27–35 de la GUI.
- El relleno se calcula sobre **el tramo de XP del nivel actual**, no sobre
  todo el progreso acumulado.
- Hover: nivel, XP, XP faltante, siguiente recompensa y cristales llenos.
- El nivel máximo muestra los nueve cristales completos.
- Se permiten materiales, títulos y textos propios; si los espacios están
  ocupados por otra personalización, no se dibuja la barra.
- Las instalaciones existentes reciben la nueva sección con respaldo;
  las páginas y premios personalizados no se sustituyen.

## 3. Armaduras y equipo real

La GUI muestra `0/4`, `2/4`, `4/4` y valores intermedios según las
piezas equipadas. Solo cuentan los **IDs verificados de la armadura**, no
el nombre ni el lore.

- Los conjuntos Laberinto, Voto y Dragón identifican bonificaciones
  parciales cuando se alcanza el umbral configurado.
- Con cuatro piezas se informa que la habilidad del conjunto está
  disponible (no que esté activada en ese instante).
- Los textos son editables en `ui/armaduras.yml` → `indicador`.
- Se corrigió la lectura de estadísticas para consultar el módulo
  `equipamiento/config.yml`, no la antigua configuración monolítica.

## 4. El Mago: biblioteca visual

`/mago` presenta una guía nativa de 54 espacios con iconografía
Base64, portada, contenido temático, descripciones breves y regreso
a la lectura anterior.

- Secciones independientes de combate, profesiones y guías.
- La navegación de iconos es **exclusivamente dentro del Mago**.
  Ningún icono redirige a los menús separados de Misiones, Trabajos,
  Armaduras u otros sistemas.
- Los comandos históricos explícitos para abrir Armaduras y consultar
  recetas se conservan, pero no se muestran en los iconos.
- `ui/mago.yml`: `visual`, `pages.*.resumen`, `pages.*.puntos` y
  `buttons[].descripcion`. Todos editables.
- Los datos visuales nuevos se incorporan conservadoramente a archivos
  existentes. Respaldo: `backups/mago-visual-v1/ui/mago.yml`.

## 5. Validación

CI incorpora pruebas de cálculo de XP, límites de toast, presentación
sin nombres técnicos, iconos sin comandos y la GUI del Mago.

Pruebas manuales recomendadas en staging:

1. `/version SpectreCore`: confirmar `0.7.6-aura-alpha`.
2. Abrir `/spectrecore misiones progresion`, observar cristales con
   0 XP, algo de XP y nivel máximo.
3. Completar un contrato de Monarquía, recibir XP y comprobar que
   el toast y las recetas se anuncian después de asignar permisos.
4. Abrir `/spectrecore armaduras` con 0, 2 y 4 piezas genuinas del
   mismo conjunto, y con ítems normales renombrados (no deben contar).
5. Abrir `/mago`, consultar todas las secciones, comprobar el regreso
   y que sus iconos nunca abran otra categoría.
6. Cambiar un nombre o texto en YAML, recargar donde corresponda y
   comprobar que se conserva al reiniciar.

La compilación y los tests de CI no reemplazan pruebas reales en Paper,
especialmente de MythicMobs, equipo y permisos.