# SpectreCore 0.7.0-alpha — trabajos, Slayer y portal

Incluye todo SpectreCore 0.6.0. Java 21, Paper 1.21.10. Compilación y pruebas de reglas aprobadas; todavía no se ha ejecutado en Paper real.

## Instalación

1. Si el Skript anterior del portal dejó arcos físicos, ejecuta su /hydevportalcleanup antes de desactivarlo. El módulo nuevo no borra bloques antiguos que no registró.
2. Detén el servidor. Desactiva los tres Skripts originales: trabajos, Slayer y portal. Los scripts ya migrados deben seguir desactivados.
3. Sustituye el JAR anterior por SpectreCore-0.7.0-alpha.jar. Conserva los archivos de SpectreCore y Skript/trabajos-config.yaml. No copies FUENTES ni las referencias de scripts a plugins/.
4. Al arrancar se crean jobs.yml, slayer.yml y portal.yml. jobs.yml importa trabajos-config.yaml SOLO si jobs.yml todavía no existe. Se conservan importes y cooldowns; sin esos datos no se inventan pagos.
5. /spectrecore modulos muestra los tres módulos nuevos. /spectrecore reload recarga sus configuraciones. Los módulos jobs, slayer y exit-portal se activan por defecto; para desactivarlos, añade modules.<nombre>.enabled: false y reinicia.

Las variables internas de Skript no se importan automáticamente. Los rangos almacenados en tu gestor de permisos permanecen; el progreso anterior de Slayer y los timestamps/ubicaciones guardados únicamente en variables Skript requieren una conversión específica si quieres conservarlos. Las partidas de dungeon deben iniciarse de nuevo tras instalar, como en el script del portal original.

## Trabajos

- /trabajo muestra minero, granjero y cazador con su mayor rango permitido.
- Permisos: trabajo.<minero|granjero|cazador>.<novato|experimentado|profesional>, todos explícitos (default false). OP no concede por sí solo esos rangos.
- Configuración original: <trabajo>.<nivel>.dinero.minimo, dinero.maximo y cooldowns.<trabajo>.<nivel>. Sin valores válidos, ese nivel no paga y se informa en consola.
- El recurso determina el nivel de pago, limitado por el rango desbloqueado. Un profesional que rompe carbón cobra como novato, como en el Skript.
- El último pago se guarda por UUID y trabajo; cada nuevo recurso consulta su propio cooldown desde ese timestamp. Los trabajos son independientes entre sí y de los tesoros.
- Minero conserva NETHER_GOLD_ORE y NETHER_QUARTZ_ORE como T3. No se añadieron a Resonancia Mineral ni a tesoros mineros.
- Granjero conserva rotura de cultivos, con tipos reales de bloque para bayas. No se añadió pago por clic derecho al trabajo. Cazador conserva los 14 tipos de mob y requiere baja atribuida a jugador conectado.
- Se excluyen bloques colocados con marcas persistentes por chunk y se reconocen también marcas existentes de minería/agricultura de SpectreCore. Los cultivos colocados como bloques pueden quedar excluidos por esa regla heredada. No se implementa un sistema nuevo de contratos: el script solo contenía el texto sobre contratos.
- Los eventos cancelados no pagan. No se añadió un requisito de supervivencia que no estaba en el script.

Economía: se prefiere Vault y se consulta transactionSuccess antes de marcar el cooldown. Un depósito fallido no se reintenta además por comando, evitando pago doble. Si no hay proveedor Vault, economia.permitir-comando-eco: true conserva eco give. Ese modo muestra Pago solicitado porque el retorno del comando no demuestra que el proveedor abonó. Ponlo en false si quieres exigir confirmación Vault.

jobs-data.yml guarda los últimos pagos. Las marcas se guardan con el chunk. Los avisos de espera están limitados a uno cada cinco segundos por jugador/trabajo.

## Slayer

| Categoría | Bajas | Jefe MythicMobs |
| --- | --- | --- |
| revenant | ZOMBIE | ReyZombie1/2/3 |
| necromancer | SKELETON | Nigromante1/2/3 |
| spider | SPIDER | MadreArana1/2/3 |

Objetivos: 20, 50 y 90 bajas para tiers 1, 2 y 3. No se añadieron variantes de esas entidades.

- /activarslayer <categoría> <tier>
- /cancelarslayer <categoría>
- /resetslayer <categoría>
- /slayerstats
- /slayerinvocar <categoría>: reintentar una invocación fallida con el objetivo completo.

Los comandos de gestión siguen siendo administrativos como jugador (spectrecore.admin o hydev.admin); /slayerstats es libre. Iniciar requiere además slayer.<categoría>.tier<tier>, declarado default false.

Solo se permite una misión activa entre las tres categorías. Una bossbar temática muestra el progreso, y el chat avisa cada diez bajas. Los datos persisten en slayer-data.yml y se restauran al reconectar.

Se corrigen las sintaxis de spawn inconsistentes del script usando la API MythicMobs. Solo una entidad invocada y válida completa la misión. Si falla o falta el ID, el objetivo se conserva; se puede reintentar por comando o con otra baja compatible, con máximo un intento cada cinco segundos. La misión termina al INVOCAR al jefe, como en el original, no al matarlo.

## Portal dinámico

Un PIGLIN_BRUTE con HYDEV_MINO_BOSS abre el arco al morir. Se busca la sesión registrada más cercana en radio 64 y se registran únicamente sus participantes no espectadores.

Callbacks conservados, SOLO CONSOLA:

- hydevportaljoin <jugador> <sesión>
- hydevportalleave <jugador>

Debes mantenerlos en las acciones de entrada/salida de Dungeons+, con un identificador de sesión real y resuelto. No se inventan sesiones por proximidad. Después de reiniciar se inicia una partida nueva: el estado temporal se limpia como en el Skript.

El arco 5x5 usa trece BlockDisplay de obsidiana llorosa, faroles marinos y oro, texto flotante y partículas. No modifica bloques, no bloquea jugadores ni daña schematics GRID. Los cofres personales siguen siendo responsabilidad de MythicMobs.

Se conservan cinco segundos dentro, duración quince minutos y área ±1,75 en X/Z, con altura relativa de -2 a 4,5. Salir, morir, teletransportarse o ser espectador reinicia la cuenta. Al desconectarse, cambiar de mundo, alejarse más de 128 bloques o recibir el callback de salida, el participante se retira. Al quedar cero participantes el arco se elimina. También expira automáticamente.

### Salida Dungeons+: requiere validación en tu instalación

portal.yml conserva la plantilla del script: dungeons end {player} --success. NO se verificó que esa sintaxis sea válida para tu versión. Ajusta salida.comando al comando individual que acepte tu Dungeons+ y mantén el callback hydevportalleave. La plantilla admite {player} y {session}.

Un comando despachado no se considera salida confirmada. Se espera el callback o la salida de la zona. Si tras diez segundos el jugador sigue dentro y registrado, se avisa y se permite reintentar, evitando el pending permanente del script anterior. No se añadió un teletransporte arbitrario que salte la finalización de la dungeon.

Administración (hydev.admin o spectrecore.admin):

- /hydevportalstatus [jugador]: sesión, portal, cuenta y pendientes.
- /hydevportalcleanup: elimina los portales del módulo.
- /hydevportaldemo: vista previa por quince segundos, sin registrar participantes ni ejecutar salida.

## Pruebas dentro del servidor

1. Verifica /trabajo, permisos, salarios y cooldowns reales. Prueba Vault/eco y menas colocadas, también tras reiniciar.
2. Activa cada Slayer con sus IDs reales; prueba bossbar y persistencia. En una copia de pruebas, usa un mob inexistente: el objetivo debe conservarse y completarse tras corregirlo y reintentar.
3. /hydevportaldemo para revisar aspecto. Después inicia una partida nueva con callbacks reales, mata al jefe y verifica cinco segundos, salida y desaparición del último participante.
4. Prueba un fallo del comando de salida, espectadores, muerte y salir/entrar del arco. Ningún fallo debe dar salida falsa ni bloquear permanentemente pending.
5. Verifica cocina, habilidades, libro y tesoros previos.

La compilación y regresiones de reglas pasaron. Las integraciones de economía, mobs y Dungeons+ y el aspecto visual todavía requieren estas pruebas en Paper.
