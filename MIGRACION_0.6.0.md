# SpectreCore 0.6.0-alpha — Cygnus, agricultura y actividades

Incluye todas las funciones de 0.5.1. Java 21, Paper 1.21.10. El paquete contiene el JAR compilado, las fuentes y este documento. Se aprobaron compilación, regresiones de reglas y validación YAML; falta ensayo dentro de Paper.

## Instalar

1. Detén el servidor y respalda configuraciones y datos.
2. Desactiva los Skripts pegados en este lote: movimiento de Cygnus al entrar; sus siete comandos de oferta; tesoros agrícolas; resistencia al entrar; /opentreasure; los seis comandos de polvo; /almas1, /mineria1, /telefono1 y /slayerzombie1/2/3. Los Skripts de lotes anteriores también deben seguir desactivados.
3. Sustituye el JAR anterior por SpectreCore-0.6.0-alpha.jar. No dejes varias versiones del plugin.
4. Conserva config.yml, mining.yml, combat-treasures.yml, treasures.yml, todos los datos persistentes y Skript/tesoros-config.yaml. No es necesario reinstalar armaduras ni recetas.
5. Arranca. Se crean activities.yml y agriculture-treasures.yml. Al crear este último se importan probabilidades.agricultura.tier1/2/3 del YAML antiguo. Sin esas tasas, los tiers quedan al 0% hasta configurarlos.
6. /spectrecore modulos muestra Agricultura y Actividades. /spectrecore reload recarga sus dos archivos. Cambiar módulos completos requiere reinicio.

modules.activities.enabled y modules.agriculture-treasures.enabled valen true por defecto, incluso si tu config.yml conservado todavía no tiene esas claves. Puedes añadirlas y ponerlas en false para desactivar módulos.

## Cygnus

- Se conservan los siete destinos y el comando externo npc move_to Cygnus ... hydevspawn, sin sustituirlo por una API de NPC distinta.
- Se mueve al NPC en cada entrada de jugador, como hacía el código. El encabezado decía 03:00 AM, pero no existía una tarea horaria: no se creó una alarma ni un horario nuevo.
- activities.yml permite desactivar el movimiento al entrar o editar los comandos de destino.
- Cada oferta conserva 30% de fuga y 70% de entrega. Se mantiene una sola unidad.
- El script NO descontaba dinero ni consumía materiales: este módulo tampoco introduce cobros ni consumo. Si un menú externo cobra, ese menú sigue controlándolo.
- Si falta el objeto MMOItems no se entrega un sustituto. La fuga necesita que exista el comando npc. El resultado semántico de mover al NPC depende del plugin que provee ese comando.

| Comando | Recompensa de éxito |
| --- | --- |
| /rompemuelas | SWORD:ROMPEMUELAS |
| /taladroroto | TOOL:TALADRO |
| /furiaestelar | SWORD:FURIA_ESTELAR |
| /capuchaencogedora | ARMOR:CAPUCHA |
| /slimehechizado | ARMOR:BOTASSLIME |
| /compactador | MATERIAL:COMPACTADOR |
| /golemcobre | ARMOR:COPPERGOLEM |

## Agricultura

| Tier | Cultivos | Premio (CONSUMABLE) |
| --- | --- | --- |
| 1 | Trigo, zanahorias, patatas, remolacha | AGRICULTURA_T1 |
| 2 | Calabaza, sandía, caña de azúcar, cacao | AGRICULTURA_T2 |
| 3 | Nether wart, enredaderas con bayas luminosas, arbusto de bayas dulces | AGRICULTURA_T3 |

Se conceden mediante tesoros.agricultura.tier1/2/3, declarados default false. OP por sí solo no sustituye esos permisos. El premio se genera con la API MMOItems: corrige el comando original, cuyo orden de jugador e ID estaba invertido.

Se conserva la ausencia de requisito de supervivencia o madurez: cosecha.solo-supervivencia y solo-maduros están en false. Puedes activarlos explícitamente. Las enredaderas sin bayas no cuentan como bayas. Además de las roturas, se contempla la recolección real de bayas con clic derecho, desactivable con incluir-recoleccion-bayas.

Mejora de protección: las calabazas y sandías colocadas por jugadores no dan tesoros de forma predeterminada. Sus marcas persisten en el chunk y se siguen en movimientos por pistones. No se bloquea la siembra normal de cultivos. Las colocaciones anteriores al módulo no tienen esta marca. Esta protección es configurable y no detecta todos los cambios directos de WorldEdit u otros plugins.

El cooldown es el MISMO de minería y combate: ganar uno de esos tres tesoros bloquea los otros durante el plazo de treasures.yml (5 s por defecto). La resonancia y /opentreasure no consumen ese plazo. Fallar la generación de la recompensa no reserva cooldown.

Estética: BlockDisplay de un fardo que asciende y rota, fragmentos orbitales y espiral amarilla, naranja o violeta según tier. /spectrecore agricultura 1, 2 o 3 permite previsualizar sin recompensa ni cooldown.

## Entrada de jugadores

Un tick después de entrar se aplica Resistencia nivel 100 durante 30 segundos, con el sonido de nivel. Se conserva la duración del código, aunque el comentario antiguo decía 15 segundos. activities.yml permite cambiar duración/nivel o desactivarlo. No se elimina una resistencia más fuerte que ya exista.

## /opentreasure

Cofre como ItemDisplay, elevado delante del jugador: gira, vibra y tiene oro orbital, partículas y sonidos progresivos durante tres segundos. No utiliza ArmorStand ni modifica bloques del mundo.

| Premio vanilla, 1 unidad | Probabilidad |
| --- | ---: |
| COAL_ORE | 35% |
| IRON_ORE | 25% |
| COPPER_ORE | 20% |
| GOLD_ORE | 10% |
| REDSTONE_ORE | 5% |
| LAPIS_ORE | 3% |
| DIAMOND_ORE | 1% |
| EMERALD_ORE | 1% |

El premio se sortea al comenzar y se guarda en activity-pending.yml antes de animarse. Solo puede haber un cofre pendiente por jugador. Se entrega al terminar, al salir o al detenerse el módulo si el jugador está conectado; un pendiente offline se recupera al entrar. Esta protección cubre salidas y apagados normales; no es una transacción atómica entre el inventario y el disco frente a un fallo abrupto del proceso o del almacenamiento.

El inventario lleno deja el sobrante en el suelo con propietario asignado. El límite de animaciones no impide entregar el premio.

## Polvos y desbloqueos

Se mantienen los comandos de consola proporcionados, usando el nombre válido del jugador. eco, lp, zmenu y npc deben seguir siendo provistos por sus plugins originales. Se detecta si falta el comando; la confirmación real de pagos/desbloqueos sigue dependiendo del proveedor, porque se conservó el despacho por comando y no se inventó una integración económica distinta.

| Comando | Efecto |
| --- | --- |
| /polvodiamante | eco give, cantidad entera 70–200 |
| /polvohierro | 20–40 |
| /polvooro | 40–70 |
| /polvocobre | 5–20 |
| /polvolapis | 20–60 |
| /polvoredstone | 20–50 |
| /almas1 | LuckPerms: tesoros.combate.tier1 true |
| /mineria1 | LuckPerms: tesoros.mineria.tier1 true |
| /telefono1 | zmenu open zmenu:slayers |
| /slayerzombie1 | LuckPerms: slayer.revenant.tier1 true |
| /slayerzombie2 | LuckPerms: slayer.revenant.tier2 true |
| /slayerzombie3 | LuckPerms: slayer.revenant.tier3 true |

Los seis comandos de polvo NO comprueban ni consumen un mineral, igual que el script. Todos los 20 comandos migrados siguen siendo administrativos: requieren OP o spectrecore.activities.admin / spectrecore.admin. No se abrieron a jugadores normales.

## Prueba de aceptación pendiente

1. /spectrecore modulos, /armaduras, /cocina ayuda y /resonancia deben seguir funcionando.
2. /spectrecore agricultura 3: revisar fardo, órbitas y limpieza, sin premio. /opentreasure: comprobar los tres segundos, premio y desaparición. Prueba volver a ejecutarlo mientras abre, salir durante la animación y reiniciar normalmente.
3. En un servidor de pruebas, configura 100% agrícola y otorga el permiso. Revisa premio CONSUMABLE por cultivo, roturas canceladas, bayas de clic derecho, enredaderas vacías y calabazas/sandías colocadas antes y después de reiniciar.
4. Gana un premio agrícola y prueba inmediatamente minería y combate. Ninguno debe entregar un segundo tesoro antes del cooldown compartido.
5. Comprueba la resistencia al entrar y que el NPC cambia entre los siete puntos. Si no cambia, revisa la salida del comando npc del plugin que lo administra.
6. Verifica las siete ofertas con IDs reales y los comandos de polvo/desbloqueo en una cuenta de prueba. Un jugador sin permiso no puede ejecutarlos.
7. Restaura las probabilidades reales tras probar. No ejecutes estos módulos y sus Skripts anteriores simultáneamente.

## Verificación realizada

Compilación Java 21/Paper API 1.21.10 correcta, regresiones de reglas y YAML aprobados. Se verificaron exhaustivamente las 100 tiradas del cofre y el 30/70 de Cygnus, así como los tipos de bloque agrícolas y el plazo compartido. Falta validar los plugins externos y el aspecto dentro de un servidor real.
