# Minería de Hydev — dev7

## Progresión jugable

La experiencia, los niveles, las estadísticas **Luck** y **Toughness** y el menú de progresión
pertenecen únicamente a AuraSkills. SpectreCore consulta su API oficial;
no crea una segunda barra ni un menú de niveles.

| Minería | Función |
| ---: | --- |
| 1 | Minería Inicial y contrato Novato |
| 3 | Compactación y pulverización nativas (64:1, NPC Minero) |
| 6 | Paladio, contrato Experimentado y Taladro |
| 10 | Recompensa: 1 Cénit |
| 12 | Duendes al extraer oro natural y Chatarra Mecánica |
| 15 | Prospectores al extraer diamante, esmeralda u obsidiana; Pico Perdido y contrato Profesional |
| 18 | Mitrilo y Picos de Mitrilo |
| 22 | Pico de Amatista y Resonancia Arcana |
| 25 | Linterna de Mitrilo y Barrena |

**Los contratos antiguos adquiridos se conservan**. Firmar uno nuevo exige nivel,
contrato anterior y el importe ya existente; los niveles no se consumen.

## Luck de AuraSkills

Lectura directa `SkillsUser.getStatLevel(Stats.LUCK)`. Se toman los modificadores
del atributo que AuraSkills aplique al jugador, sin crear un segundo sistema.
Los valores se ajustan en `modules/tesoros/mineria.yml`:

- **Encontrar Paladio/Mitrilo:** +1,5 % de probabilidad *relativa* por punto de Luck,
  hasta ×2 de la probabilidad normal.
- **Encontrar Duendes/Prospectores:** misma regla (+1,5 % relativo por punto, ×2 máximo).
- **Obtener botines de encuentros:** +1 % relativo por punto, ×1,8 máximo.
- **Cantidad de Paladio:** probabilidad de **una sola unidad extra** igual a
  `min(35 %, Luck × 1 %)`.
- **Cantidad de Mitrilo:** probabilidad de **una unidad extra** igual a
  `min(25 %, Luck × 0,8 %)`.
- **Calidad de gemas:** aumenta hasta cinco puntos porcentuales la tirada
  de calidad del Pico Perdido y el Pico de Amatista; no vuelve raras las gemas
  extraídas de menas de tier 1, donde solo existe calidad básica.

**Ejemplo con 12 Luck:** encuentro de Prospector 2,2 % → 2,596 %,
probabilidad de una unidad extra de Paladio 12 %, y de Mitrilo 9,6 %.
La suerte nunca crea hallazgos cuando la probabilidad original es cero.

Las probabilidades base sugeridas de Paladio son 0,65 % (T1), 0,9 % (T2)
y 1,4 % (T3). Mitrilo conserva 1 % (T2) y 2 % (T3),
antes de bonificaciones de herramienta y Luck.

## Instalar las integraciones

1. **AuraSkills:** copiar con respaldo el fragmento `integracion-auraskills/rewards/mining.yml`
   en `plugins/AuraSkills/rewards/mining.yml`. Conserva Toughness +1
   cada nivel y Luck +1 cada dos niveles; quita el permiso obsoleto
   `tesoromineria1` del nivel 30. **No sobrescribir otras recompensas
   personales que no estén en el fragmento sin compararlas primero.**
2. **MythicMobs Free:** instalar `integracion-mythicmobs/HYDEV_MINERIA.yml`
   en su carpeta `Mobs/` y recargar; los IDs usados son
   `HYDEV_DUENDE_MINERO` y `HYDEV_PROSPECTOR_ANTIGUO`.
   Se usan zombis bebé e iron golems como apariencia vanilla,
   con velocidades, vida y tamaño propios. Los mobs NO tienen drops propios.
3. **MMOItems:** fusionar `integracion-mmoitems/MATERIAL_MINERIA.yml` en
   `item/MATERIAL.yml`; fusionar `TOOL_TALADRO_MEJORAS.yml` en
   `item/TOOL.yml` sin tocar `TOOL:TALADRO` original; fusionar
   `GEM_STONE_PICO_PERDIDO.yml` en `item/GEM_STONE.yml`.
4. **Forja:** añadir `RECETAS_FORJA_TALADRO.yml` a su estación existente.
   **Reemplazar** la receta `picoperdido` con
   `RECETA_PICO_PERDIDO.yml` para mantener el objeto genuino
   `TOOL:PICO_GEMAS`, comprobado en la receta anterior del servidor.
   No generar `TOOL:PICO_PERDIDO`.
5. **NPC Minero:** su interacción debe ejecutar como jugador
   `/spectrecore minero`. El Taller nativo de SpectreCore reemplaza
   la estación de compactación; no hay otro menú de progresión minera.
6. **Reiniciar/recargar:** instalar los fragmentos antes de habilitar premios
   o encuentros. Probar la economía, los permisos de WorldGuard y que cada
   tipo/ID exista en MMOItems.

### Seguridad y límites

- Solo roturas **manuales, sin cancelar, en Supervivencia y de menas
  marcadas como naturales** pueden disparar tesoros y encuentros.
- La obsidiana colocada se marca a partir de esta versión. **Obsidiana
  colocada antes de instalarla no puede identificarse retrospectivamente.**
  Conviene probar o restringir temporalmente esas zonas.
- Máximo 12 criaturas mineras activas; Duendes 70 s y Prospectores 150 s
  de intervalo por jugador, por tipo. Sin escaneos globales del mundo.
- Un encuentro es generado por MythicMobs usando su helper público.
  Los drops y condiciones quedan en SpectreCore, no en MythicMobs.
  El propietario recibe los materiales si está online, en Supervivencia
  y cerca cuando una persona mata al enemigo.
- El Taladro básico solo rompe piedra; sus mejoras despejan hasta uno
  o tres bloques extra de roca sin drops ni XP, respetando WorldGuard.
  No abre túneles largos: esa función sigue siendo de la Barrena de Mitrilo.
- La lectura del nivel AuraSkills falla cerrada cuando el jugador no tiene
  su perfil cargado. La Luck no disponible equivale a **ningún bono**.
- Las configuraciones antiguas del Pico Perdido, cuando coinciden con sus
  valores predeterminados, se respaldan bajo
  `backups/pico-perdido-v1` y completan ID `PICO_GEMAS`
  y gemas de resonancia.

### Validación pendiente en servidor

Aunque CI compile, deben probarse en Paper 1.21.10 + MythicMobs Free 5.13.0
los spawns en cuevas, arena de combate, death-drops, herramientas MMOItems,
AuraSkills Luck real, obsidiana previamente colocada, economías y
descarga/recarga de chunks. Integración al NPC y la Forja debe realizarse
en sus archivos reales; esas configuraciones no se almacenan en el repo.
