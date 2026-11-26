# Auditoría de experiencia del jugador · dev09

**Fecha:** 26/11/2026. Base: `3920b3f` (`0.9.0-dev8`, indicador de resonancia).
Revisión estática de los recorridos, mensajes, menús y reglas de los módulos.
No es una certificación de jugabilidad ingame. El parche evita cambios en economía,
probabilidades, requisitos, costos, controles, progresión, IDs y datos persistentes.
La UX se mantiene configurable y los catálogos siguen siendo independientes.

## Hallazgos confirmados y correcciones

| ID | Sistema | Antes (evidencia en la base) | Ahora |
|---|---|---|---|
| UX-01 | Trabajos | `JobsMenu.resolve` mostraba solo la espera configurada; `JobsModule.pay` guardaba el tiempo real pero no lo exponía. | La ficha de cada rango contratado muestra la espera restante por categoría de actividad, calculada desde el mismo último pago. Es una instantánea al abrir el menú, no una promesa de pago ni una nueva tarea de refresco. |
| UX-02 | Armaduras | `ArmorGuide.equippedLore` anunciaba disponibilidad solo por llevar cuatro piezas, incluso en recarga. | Expone recarga del motor y distingue conjunto completo de habilidad indisponible. No confunde ausencia de recarga con cumplimiento de todas las condiciones. |
| UX-03 | Asterión y Último Juramento | Furia llena sugería doble Shift aun durante recarga; ambos intentos manuales podían fallar sin explicación. | Asterión muestra el tiempo real en su indicador existente. Un intento de doble Shift rechazado explica carga insuficiente o recarga. No se añade un aviso a cada golpe ni una tarea nueva. |
| UX-04 | Cocina | La inspección concatenaba el enum del ingrediente; un ingrediente incorrecto no explicaba qué añadir. | Usa nombres del recetario y el número exacto que falta del tipo siguiente, incluso para varias raciones. No se modifica el consumo. |
| UX-05 | Relicario | Confirmación mostraba origen, pero no la ganancia ni su límite; estadísticas vacías carecían de orientación. | Antes de consumir una reliquia, muestra el incremento de la colección calculado por el mismo acumulador y sus límites, o límite alcanzado. Indica que consume una unidad y cómo iniciar una colección vacía. |
| UX-06 | Slayer | Contrato con bajas completas y jefe pendiente mostraba solamente cuota completa; el reintento podía ser silenciosamente limitado. | El contrato indica que la invocación requiere reintento y este informa el tiempo restante. El estado respeta también la regla de una única misión activa. No altera invocación ni precio. |
| UX-07 | El Mago | Puntos de resonancia, cocina y reliquias eran genéricos; no enseñaban qué hacer. | Explica menas naturales, controles distintos de los picos, montaje del caldero, recetario, utensilios y clic de absorción. |
| UX-08 | Portales | La guía describía acceder a una expedición; `PortalModule.boss` genera una salida tras el Minotauro. | La guía explica el arco de extracción, el cofre y permanencia en el arco. El funcionamiento efectivo sigue dependiendo del registro de la sesión. |

Las modificaciones de los menús migran únicamente textos/listas por defecto
reconocidos y añaden etiquetas que falten; los textos personalizados conservan
prioridad. Se respaldan los menús editados antes de su reescritura. El Relicario
consulta los textos faltantes en la plantilla empaquetada. Instalar el nuevo JAR
con el servidor detenido y conservar los datos existentes; no reemplazar los YAML
personalizados con las plantillas de ejemplo. No se necesita reinstalar integraciones.

## Ámbitos también examinados

- Minería ya cuenta con la barra por herramienta; la guía actualizada complementa
  ese parche. Los taladros de piedra tienen excavación automática, sin resonancia.
- La progresión Slayer ya muestra XP, recompensas y acceso a rangos; no se añade
  otro sistema de progreso.
- El recetario predeterminado tiene 15 iniciadores distintos y muestra ingredientes,
  tiempos, efectos y descubrimiento; no se modifica la selección de recetas.
- El Relicario ya muestra pistas de origen, hitos y bonificaciones; no se crea
  una segunda colección.
- Trabajos, taller y abastecimiento conservan sus propios menús y transacciones.

## Lo que requiere observar el juego (no se afirma que sean fallos)

1. **Descubrimiento real:** desde el primer ingreso, cuáles NPC/tablones encuentra
   un jugador, si comprende los premios por nivel y cómo llega al recetario.
2. **Recompensas y economía:** si entiende para qué sirve cada mineral/material,
   si identifica rutas útiles de fabricación y si las recompensas iniciales
   conservan utilidad. Los valores dependen del catálogo efectivo del servidor.
3. **Combate:** si los enemigos se distinguen por comportamiento, tienen avisos
   legibles y ofrecen decisiones; no basta contar habilidades en MythicMobs.
4. **Ritmo y accesibilidad:** revisar duración de las actividades, tamaño de las
   descripciones, colores, concurrencia de avisos, rendimiento y saturación visual.
5. **Exploración por biomas:** propuesta de futuro; no implementar ni prometer
   sistemas pendientes como si existieran actualmente.

Cuando se juegue en HydevLab, observar normalmente estos recorridos:
alternar dos herramientas, probar una habilidad en recarga, abrir Trabajos tras
cobrar, cocinar tres raciones, examinar una reliquia antes de absorber y consultar
un contrato pendiente. Son comprobaciones breves de experiencia, no pruebas
destructivas ni requisitos para que el propietario audite manualmente cada archivo.

## Asterión y política sin actionbar

La Furia carga la habilidad y el Ímpetu representa el daño adicional por golpes consecutivos al mismo enemigo durante la transformación. La guía explica el primer golpe, el límite y las causas del reinicio según los valores configurados. Una sola barra cambia entre Furia e Ímpetu y muestra disponibilidad, cargas, porcentaje de daño y tiempo restante. No se modifican el balance ni la progresión.

Se reemplazan las actionbars restantes de SpectreCore por barras nativas para estados temporales (brújula/portal), mensajes puntuales o avisos nativos existentes. Las barras se limpian al terminar o perder su contexto. La política se documenta en AGENTS y se protege con el validador de presentación. Las pruebas de cliente siguen pendientes.

## Señales de combate y descubrimiento minero

- **Botania:** muestra la cadena de golpes y la duración de las rosas despiertas. La ventana se mide entre golpes consecutivos, no para toda la cadena.
- **Corona de Ánimas:** disparos restantes, espera hasta el siguiente disparo y tiempo restante de invocación. Si no hay objetivo válido, no anuncia una pérdida de munición.
- **Último Juramento:** tiempo efectivo, posibilidad de extenderlo y Eco de Blink disponible con el arma afín.
- **Sangre del Primer Dragón:** golpes necesarios y duración de la bendición, incluida la extensión de Dracoreliquia mientras esté equipada.
- **Asterión:** el efecto al alcanzar el máximo de Ímpetu se reproduce al llegar al máximo, no en cada golpe posterior.
- Los golpes válidos con multiplicador directo de SpectreCore muestran un float privado y breve. No se atribuyen críticos, estadísticas externas, daño de armadura o daño secundario a ese porcentaje; se muestra solo el multiplicador de habilidad realmente aplicado. Las bonificaciones pasivas permanentes por sí solas no generan avisos. No se apilan floats.
- Mitrilo distingue recarga, resonancia faltante, falta de ruta y límite de campos. No cambia precios ni permisos. El Mago explica los usos de materiales según el catálogo minero.
- Los avisos de hitos se ajustan en el archivo de recompensas de AuraSkills, que sigue siendo la autoridad de niveles, permisos y mensajes. Las pistas de uso de los ítems mineros se actualizan en el catálogo de integración. Estos dos archivos son externos al JAR: si ya están instalados, utiliza solo las modificaciones de texto, sin perder personalizaciones ni reimportar recetas o datos de jugadores.

El indicador de armadura usa el tick de habilidad existente y se oculta al expirar, perder el conjunto o salir del contexto; no se añade un temporizador repetitivo por jugador. El float privado está acotado a uno por atacante y se retira desde el mismo motor. No se usan actionbars ni se cambian las reglas de combate. La apariencia y la visibilidad del cliente se comprueban ingame.

## Rechazos de contratos sin códigos internos

Las cacerías y los trabajos traducen exhaustivamente sus motivos de rechazo antes de mostrarlos. Si existe un requisito numérico de Monarquía Renacida, el mensaje utiliza el mismo umbral que el control de acceso; los desbloqueos externos no inventan un nivel. Firmar un contrato no disponible, volver a comprar un rango, saltar un rango, abandonar sin misión y una compra pendiente tienen explicaciones distintas. Los mensajes son configurables y los textos personalizados conservan el marcador de motivo. No se modifican permisos, precios, transacciones ni progresión.
