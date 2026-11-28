# Hydev — Minería | Integraciones corregidas

La instalación consiste en **tres configuraciones distintas**. No reemplaces todos
los catálogos de MMOItems por fragmentos. Guarda respaldo antes de aplicar.

## 1. AuraSkills — recompensas y mensajes

Archivo preparado: `AuraSkills/rewards/mining.yml`. Reemplaza únicamente el
`rewards/mining.yml` instalado (después de guardar una copia). Mantiene
Toughness +1 por nivel, Luck +1 cada 2 niveles y concede por LuckPerms
estos permisos:

| Nivel | Permiso | Desbloquea |
| ---: | --- | --- |
| 1 | `hydev.mineria.inicial` | Minería inicial, contrato Novato |
| 3 | `hydev.mineria.hechizados` | Taller del Minero |
| 6 | `hydev.mineria.paladio` | Paladio, Taladro, segundo contrato |
| 10 | — | Recompensa: 1 Cénit (`CONSUMABLE:MEJORA_ARMA1`) |
| 12 | `hydev.mineria.duendes` | Duendes, Taladro Reforzado |
| 15 | `hydev.mineria.resonancia` | Prospectores, Pico Perdido, tercer contrato y Taladro Calibrado |
| 18 | `hydev.mineria.mitrilo` | Mitrilo y picos |
| 22 | `hydev.mineria.arcana` | Pico de Amatista |
| 25 | `hydev.mineria.ingenieria` | Linterna y Barrena |

Cada recompensa trae `menu_message` y `chat_message` coloreados,
con título en versalitas y descripción. **No se crea otro menú** ni un mensaje
duplicado en SpectreCore. El permiso obsoleto `tesoromineria1` del nivel 30
desaparece. Para nuevos niveles requiere que AuraSkills y LuckPerms funcionen
juntos; los permisos antiguos de contratos pagados siguen siendo independientes.

## 2. MythicMobs Free — mobs con mecánicas

Instala los **DOS** archivos:

- `MythicMobs/Mobs/HYDEV_MINERIA.yml`
- `MythicMobs/Skills/HYDEV_MINERIA_SKILLS.yml`

El Duende persigue, salta, retrocede y se acelera cuando recibe daño.
También puede ralentizar levemente en su ataque; no roba inventarios.

El Prospector persigue, utiliza una guardia corta y anuncia con partículas
un pisotón de radio corto con 25 ticks de anticipación. Alejarse esquiva el
ataque. Su botín lo entrega exclusivamente SpectreCore.

SpectreCore coloca estas criaturas en espacio respirable, les asigna objetivo,
limita los enemigos vivos y conserva los cooldowns. En dificultad Pacífica
no aparecen.

**Validar dentro del servidor:** la compilación de SpectreCore no confirma
que el parser de MythicMobs reconozca todos los skills. Comprueba consola
al recargar, IA y comportamiento en cuevas con jugadores de prueba.

## 3. MMOItems y SpectreCore

Instala el JAR de esta entrega. Con el servidor detenido, descomprime la integración
y abre una terminal en `MMOItems/mineria-v2/`. El primer comando simula y el segundo
guarda los cambios con respaldo bajo `.hydev-backups/`.

```bash
python3 -m pip install pyyaml
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems --aplicar
```

El instalador requiere Python 3 con PyYAML (usa un entorno virtual si tu sistema lo exige).
Rechaza claves duplicadas, incluso si una está entre comillas, antes de escribir.

El tipo personalizado RELIQUIA debe estar registrado como en la instalación del Relicario.
El instalador fusiona las cinco reliquias, actualiza las herramientas y el lore de
Rubí, Ónice y Amatista I–III, e instala sus formatos. Para una instalación manual,
fusiona las entradas por ID en sus tipos, copia los tres formatos en
`MMOItems/language/lore-formats/` y la estación en `MMOItems/crafting-stations/`.
Las herramientas usan ranuras reales de MMOItems con color de texto `'1'`.
El formato se escribe como lista YAML de bloque, incluso en la instalación
automática:

```yaml
    gem-sockets:
    - '1'
```

Cantidad de ranuras por herramienta:
una en el Pico Perdido, Pico de Mitrilo, Pico de Amatista, Guante Minero,
Linterna y Barrena; dos en el Pico de Mitrilo Templado y Taladro Reforzado;
tres en el Taladro Calibrado. El instalador conserva las ranuras originales y
añade solo las necesarias. El formato `herramienta-minera.yml` muestra las
ranuras y los atributos nativos sin modificar el formato global. El lore
describe las estadísticas de las habilidades con los valores predeterminados;
si cambias el balance en minería, actualiza también esas descripciones.
La revisión 8 utiliza versalitas, gradientes propios y separación visual
entre títulos, estadísticas, habilidad y espacios de mejora.

Las plantillas revisadas tienen revisión 8 como mínimo. Para actualizar los
objetos existentes, comprueba que el mecanismo de revisión de MMOItems esté
activo y que `item-revision.keep-data.gems` y `item-revision.drop-extra-gems`
estén habilitados en su configuración; conserva las gemas y respaldos del
servidor. Prueba un pico existente con una gema instalada y uno recién generado
antes de actualizar en producción. No se cambian los atributos ni el balance.

La estación `forja-minera` incluye las diez herramientas, el Compactador,
la Llave Resonante, la Carga Resonante, las cinco reliquias y nueve recetas de gemas. Si la forja anterior
contiene recetas propias del Guante Minero o el Compactador, el instalador usa esas
recetas. Puedes señalarla con `--forja /ruta/forja.yml`.

Tras arrancar, abre `/forjaminera` con Minería inicial, o usa desde consola
`mi stations open forja-minera JUGADOR`. Las mejoras del Guante Minero, del Taladro Reforzado y del Pico de Mitrilo
son instantáneas. Instala juntos este JAR y las recetas. La Forja conserva las gemas
de la herramienta consumida y añade una ranura vacía. Prueba primero con un ejemplar
de prueba y comprueba también el bono de una gema de dungeons tras la mejora.

### Gemas de minería

GEM_STONE_RAMAS_MINERAS.yml contiene las 17 plantillas nuevas. El instalador las
fusiona por ID. El Pico Perdido conserva su botín de dungeons. El Pico de Mitrilo y
el Templado tienen un 1 % por mena natural de encontrar Veta I, Magnetita I,
Fluorita I o Volcanita, con la misma probabilidad dentro del grupo. La Forja
Minera fabrica Diamante Loco I con diamantes y Amatista Resonante. También permite
subir las cuatro ramas a II y III. Los grados IV salen de las geodas reservadas.
Volcanita tiene un único grado.

### Medidor de geodas

Desde Minería 22, cada mena natural de diamante, esmeralda o redstone suma
0,5 % de avance; no se requiere un pico específico. La Lupa de Espato aumenta
el avance un 30 %. El medidor permanece visible 12 segundos después de minar
y el progreso pertenece al jugador, no a la herramienta. Al alcanzar 100 %
se revela una geoda si hay roca adyacente en una zona permitida y las cuatro
gemas IV y la llave están registradas. Si no es posible, el medidor queda
completo hasta la próxima mena apta. La ubicación se reserva por diez minutos.
Usa una Llave Resonante, agáchate y haz clic derecho para alternar Veta,
Magnetita, Fluorita y Diamante Loco IV; clic de pie para abrir. Diamante Loco
también consume un III.

El JAR migra `geodas.probabilidad-porcentaje` a `geodas.avance-por-mena`
con respaldo, conservando el valor antiguo como porcentaje de progreso por
mena. Ajusta `modules/tesoros/mineria.yml` para cambiar el avance; los efectos
están en `modules/resonancia/ajustes.yml`. Para una prueba rápida de desarrollo,
establece el avance en 100, pica una mena natural en una región permitida y
vuelve al valor de balance. Comprueba relog, el bono de Lupa de Espato, selección
y pago de cada núcleo, y que los efectos sean visibles para el descubridor.

Las habilidades leen las gemas instaladas en la herramienta de la mano principal.
Si hay varias de la misma rama cuenta el grado mayor. Para comprobar la instalación,
genera una gema con /mi give GEM_STONE GEMA_MAGNETITA_I, arrástrala sobre una
herramienta nueva con una ranura libre y pica una mena natural en supervivencia.

Si una Forja Minera anterior muestra un aviso al cargar la vista de recetas, actualiza el YAML o ejecuta el instalador corregido. Este incorpora la sección de presentación `gui-layout.items.recipe.upgrade` que MMOItems requiere incluso al usar solo recetas de fabricación, conservando la configuración personalizada.


### Linterna de Mitrilo — actualización dev09
El objeto conserva el ID `TOOL:FAROL_MITRILO` (revisión 8); actualizar la plantilla y las recetas mediante el instalador. Se retiran las ofertas antiguas de brújula y farol, no los objetos poseídos. Las brújulas ya existentes quedan sin habilidad; comprueba las recetas personalizadas que hagan referencia a ellas. La receta `linterna-mitrilo` conserva el costo del farol. Actualiza también `rewards/mining.yml`, reinicia y verifica la revisión de los ítems en Minecraft. El JAR agrega automáticamente los nuevos ajustes y migra la bonificación de Bobina de Cobre con respaldo.

Al alcanzar Minería 10, AuraSkills ejecuta la entrega de un Cénit existente (`CONSUMABLE:MEJORA_ARMA1`). Verifica que el ID exista en el servidor antes de instalar las recompensas. No es retroactivo para niveles alcanzados antes de la instalación.
