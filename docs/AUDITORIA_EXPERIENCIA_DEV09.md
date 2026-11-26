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
