# SpectreCore 0.5.1-alpha — almas y tesoros de combate

Incluye todo SpectreCore 0.5.0 y reemplaza el Skript de tesoros de combate suministrado. Paper 1.21.10, Java 21 y MMOItems.

## Instalar

1. Detén el servidor. Desactiva el Skript de tesoros de combate para evitar premios duplicados. Los scripts ya migrados en 0.5.0 también deben seguir desactivados.
2. Sustituye el JAR anterior por SpectreCore-0.5.1-alpha.jar. No conserves dos versiones del plugin en plugins/.
3. Conserva config.yml, mining.yml, mining-data.yml, todos los datos de cocina y Skript/tesoros-config.yaml.
4. Arranca. Al crearse combat-treasures.yml se importan las probabilidades.combate.tier1/2/3 existentes en el YAML de Skript. Sin una probabilidad configurada, ese tier queda al 0%; no se inventa una tasa.
5. Verifica MATERIAL:ALMA_1 en MMOItems y concede los permisos tesoros.combate.tier1/2/3 a los jugadores que correspondan. Están declarados default false, así que OP por sí solo no los concede.
6. /spectrecore modulos muestra el nuevo módulo. /spectrecore reload recarga también combat-treasures.yml y treasures.yml.

modules.combat-treasures.enabled permite desactivar el módulo completo tras reiniciar. Si conservas un config.yml anterior sin esta clave, el valor predeterminado es true.

## Reglas

| Tier | Entidades | Permiso |
| --- | --- | --- |
| 1 | Zombie, Skeleton, Spider, Creeper, Slime | tesoros.combate.tier1 |
| 2 | Witch, Enderman, Blaze, Guardian, Phantom | tesoros.combate.tier2 |
| 3 | Wither Skeleton, Evoker, Vindicator, Warden | tesoros.combate.tier3 |

Se usan exactamente esos tipos de entidad. No se añaden variantes como Husk, Magma Cube o Elder Guardian. No hay recompensas de este módulo por jugadores ni animales.

La baja debe estar atribuida por Paper a un jugador conectado (getKiller), incluyendo proyectiles del jugador. Sin jugador acreditado no hay tirada. Las muertes canceladas por otros plugins se ignoran. No se añade un requisito de modo supervivencia al combate, porque el script no lo tenía.

Una tirada ganadora genera exactamente una unidad de MATERIAL:ALMA_1, igual en los tres tiers. COMBATE_T1/T2/T3 no controlaban el objeto en el script: su función siempre ejecutaba ALMA_1. Puedes editar botin.tipo y botin.id en combat-treasures.yml, pero la cantidad sigue siendo uno.

Si falta MMOItems o el objeto, no se consume el cooldown ni se entrega un sustituto vanilla. Un inventario lleno deja la recompensa a los pies del jugador con propietario asignado.

## Cooldown compartido con minería

Se conserva la variable compartida que tenían ambos Skripts: ganar PALADIO bloquea temporalmente ALMA_1 y ganar ALMA_1 bloquea PALADIO. La Resonancia Mineral no consume este cooldown.

- treasures.yml: cooldown-compartido-segundos, predeterminado 5.
- Al crearse treasures.yml se recupera el valor antiguo tesoros.cooldown-segundos de mining.yml si existía. Después, la configuración común en treasures.yml es la que manda; la clave antigua queda obsoleta.
- treasure-data.yml conserva un único vencimiento por UUID, compartido por minería y combate. Al crearse por primera vez, se importan automáticamente los cooldowns treasure-until que guardó SpectreCore 0.5.0 en mining-data.yml.
- Las cargas y ventanas de resonancia siguen en mining-data.yml. No se reinician con esta actualización.
- El plazo comienza cuando se entrega la recompensa, antes de la animación, impidiendo premios simultáneos. No hay una entrega pendiente que se pierda al salir o reiniciar normalmente.

Las variables antiguas de Skript siguen sin importarse automáticamente; solo se importa su configuración YAML. La migración general desde Skript se detalla en MIGRACION_0.5.0.md.

## Visuales

La calavera es un ItemDisplay animado: asciende y gira, rodeada por tres faroles de alma orbitales y una espiral de fuego de alma. Los acentos son azul hielo (T1), azul eléctrico (T2) y azul violáceo (T3). Termina con una dispersión de fuego de alma y sonido. No usa ArmorStand ni bloques reales y no requiere resource pack.

El botín se entrega inmediatamente; la animación es cosmética. El límite de animaciones en combat-treasures.yml no elimina premios. Los displays no son recogibles; se limpian al terminar o apagar el módulo, y no fuerzan la carga de chunks vecinos.

Como administrador puedes ejecutar /spectrecore combate alma 1, 2 o 3 para ver el efecto sin entregar objetos ni consumir cooldown. Las demos mineras siguen disponibles.

## Prueba rápida

1. /spectrecore combate alma 3: revisar tamaño, órbitas y limpieza de la calavera.
2. En una copia de pruebas, configura 100% en un tier y concede su permiso. Mata una entidad de ese tier: exactamente un ALMA_1. Prueba también con arco y sin permiso.
3. Mata varias rápidamente: un único premio dentro de la ventana de cooldown.
4. Gana un tesoro minero y mata un mob antes de cinco segundos: no debe dar alma. Repite en el orden inverso.
5. Prueba inventario lleno, ALMA_1 inexistente, desconexión y reinicio durante el cooldown. Una recompensa inexistente no reserva el cooldown.
6. Prueba una muerte cancelada/revivida por otro plugin y un mob sin jugador acreditado: no deben dar premio.
7. Comprueba /armaduras, cocina, resonancia y controlador de jefes. Restaura las probabilidades reales después de las pruebas.

## Verificación

Compilación Java 21/Paper API 1.21.10 y regresiones de reglas aprobadas. Las pruebas incluyen los 14 tipos de mob, exclusiones, cooldown compartido en ambos sentidos y persistencia de sus plazos. También se valida el YAML. Falta probar integración y animaciones en un servidor Paper real.
