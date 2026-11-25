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
| 12 | `hydev.mineria.duendes` | Duendes, Taladro Reforzado |
| 15 | `hydev.mineria.resonancia` | Prospectores, Pico Perdido, tercer contrato y Taladro Calibrado |
| 18 | `hydev.mineria.mitrilo` | Mitrilo y picos |
| 22 | `hydev.mineria.arcana` | Pico de Amatista |
| 25 | `hydev.mineria.ingenieria` | Brújula, Farol y Barrena |

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
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems --aplicar
```

El tipo personalizado RELIQUIA debe estar registrado como en la instalación del Relicario.
El instalador fusiona las cinco reliquias, actualiza las herramientas y el lore de
Rubí, Ónice y Amatista I–III, e instala sus formatos. Para una instalación manual,
fusiona las entradas por ID en sus tipos, copia los dos formatos en
`MMOItems/language/lore-formats/` y la estación en `MMOItems/crafting-stations/`.
Las herramientas nuevas usan ranuras de color de texto `1`. En las dos plantillas
originales TALADRO y PICO_GEMAS añade una ranura de ese color.

La estación `forja-minera` incluye las diez herramientas, el Compactador,
la Llave Resonante, la Carga Resonante y las cinco reliquias. Si la forja anterior
contiene recetas propias del Guante Minero o el Compactador, el instalador usa esas
recetas. Puedes señalarla con `--forja /ruta/forja.yml`.

Tras arrancar, abre `/forjaminera` con Minería inicial, o usa desde consola
`mi stations open forja-minera JUGADOR`. Prueba una herramienta recién creada y
una gema antes de mejorar una herramienta valiosa: las recetas que consumen la
herramienta anterior fabrican una nueva plantilla y no transfieren gemas instaladas.
