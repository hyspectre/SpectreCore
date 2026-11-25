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

## 3. MMOItems — nuevos archivos, sin mezclar a mano

Archivos fuente dentro de `MMOItems/mineria-v2/`:

- `MATERIAL_MINERIA_NUEVOS.yml` (7 materiales)
- `TOOL_MINERIA_NUEVOS.yml` (8 herramientas)
- `GEM_STONE_MINERIA_NUEVOS.yml` (3 gemas de amatista; Resonancia utiliza el catálogo de dungeons)
- `RECETAS_FORJA_MINERIA.yml` (10 recetas)
- `RECETA_PICO_PERDIDO_REEMPLAZO.yml` (la receta original `picoperdido`)
- `PERMISOS_OBJETOS_EXISTENTES.yml` (referencia, no instalar como tipo)
- `instalar_mineria.py` (instalador seguro y verificable)

**MMOItems exige que los objetos estén dentro de sus catálogos originales por
tipo**, por lo que los archivos nuevos son fuentes independientes y el
instalador solo fusiona sus entradas. No se crea un tipo nuevo, no se pierde
el Paladio anterior y **los objetos originales** `TOOL:TALADRO`,
`TOOL:PICO_GEMAS` y `MATERIAL:COMPACTADOR` conservan
nombres, lore y demás estadísticas.

### Instalar sin editar archivos a mano

Desde la carpeta `MMOItems/mineria-v2/`:

```bash
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems
```

Este comando es una **simulación**, muestra los cambios y no escribe nada.

Cuando compruebes que detectó tu Forja:

```bash
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems --aplicar
```

Si la Forja real no contiene la receta `picoperdido`, indícala:

```bash
python3 instalar_mineria.py /ruta/servidor/plugins/MMOItems --forja /ruta/forja.yml --aplicar
```

Se hacen respaldos en `plugins/MMOItems/.hydev-backups/`, se preservan
entradas ajenas a Minería, y se modifican solamente entradas nuevas,
las recetas seleccionadas y los tres permisos de objetos antiguos.
Las recetas de Forja requieren el permiso concedido por AuraSkills.

**Prueba el instalador en una copia del servidor antes de producción.**
Para reiniciar el servidor se recomienda detenerlo durante la edición para
no competir con guardados en vivo de MMOItems.

## SpectreCore

Debe instalarse la versión compilada desde la rama correspondiente a esta
integración. Verifica en servidor que los controles de permisos, la API de
Luck y el spawn funcionen antes de permitir el contenido al público.
No se sobrescribieron datos del progreso de jugadores.
