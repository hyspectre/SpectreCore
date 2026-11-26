# Reglas oficiales de presentación de Hydev

Este documento se aplica a **todas las nuevas funciones y a las ya existentes**.

## Inmersión y traducción

1. Ningún texto visible para jugadores (chat, lore, menús, guías, barras de jefe,
   títulos o notificaciones) menciona motores, plugins, APIs, proveedores ni
   detalles del desarrollo. Los diagnósticos estrictamente internos en consola
   pueden conservar nombres técnicos cuando sean necesarios para soporte.
2. Los íconos de menús **no muestran comandos** en su nombre ni en su lore.
   Indican la acción de forma natural: «Clic para explorar», «Volver», etc.
3. Los IDs persistentes del motor y de los objetos no se usan como nombres de
   presentación. Cada oficio, rango, arma y recompensa tiene un nombre legible.
4. Toda presentación nueva debe venir de YAML configurable. Usar MiniMessage;
   cadenas entre **comillas dobles**; parámetros dinámicos mediante resolutores
   que no interpreten texto del jugador como formato.
5. Las GUIs siguen la dirección visual de Misiones: 54 espacios, títulos
   oscuros con detalles dorados, contenido legible, cabezas Base64 y
   navegación entre páginas. Las acciones permanecen **validadas en Java**.
   Nunca ejecutar comandos arbitrarios a partir de lore o YAML.
6. Al actualizar un YAML ya personalizado, migrar solo los valores conocidos,
   conservar cualquier cambio propio y guardar una copia exacta antes de escribir.

## Avisos y notificaciones

- **No usar actionbar en SpectreCore.** Los estados continuos usan barras nativas discretas que desaparecen al terminar; los hitos usan toasts o floats, y los resultados y errores puntuales usan chat sin spam.

- Cada toast define por separado `icon`, `titulo`, `descripcion`,
  `sonido`, `volumen`, `tono` e `intervalo-segundos`.
- `sonido: "NINGUNO"` desactiva únicamente el sonido del evento.
- La presentación del toast nativo depende del cliente: no se ofrecerá una
  duración de pantalla ficticia. `intervalo-segundos` es el tiempo mínimo
  **entre avisos de ese tipo**, no cuánto dura el dibujo en pantalla.
- Evitar doblar el sonido del evento con un efecto de recompensa anterior.

## Dónde se aplican

- `src/main/resources/ui/{misiones,trabajos,armaduras,mago}.yml`
- `src/main/resources/modules/*/mensajes.yml`
- `src/main/resources/modules/trabajos/config.yml` (nombres/rangos)
- `src/main/resources/modules/nucleo/ajustes.yml` (toasts)

Validadores de CI: `tests/validate_player_presentation_policy.py`,
`tests/validate_presentation.py`, `tests/validate_native_armors.py` y
`tests/validate_native_missions.py`.

**Nota:** la restricción de nombres y comandos afecta a textos mostrados a
jugadores, no a la documentación de desarrollo, metadatos de comandos,
configuración técnica o mensajes de diagnóstico exclusivos de la consola.
