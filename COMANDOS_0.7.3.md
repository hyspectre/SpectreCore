# SpectreCore 0.7.3 — comandos unificados

La única entrada registrada es /spectrecore. Usa /spectrecore ayuda y /spectrecore ayuda <categoria>. Tab muestra las rutas permitidas y activas.

| Comando | Función | Acceso |
|---|---|---|
| /spectrecore status | Estado de habilidades | Consulta pública |
| /spectrecore modulos | Estado de módulos | Consulta pública |
| /spectrecore reload | Recargar configuración | Administración general |
| /spectrecore reset | Reiniciar tus habilidades y recargas | Administración general |
| /spectrecore demo <habilidad> | Alternar una habilidad de prueba | Administración general |
| /spectrecore blink <jugador> | Notificar un Blink confirmado | Solo consola |
| /spectrecore armaduras | Libro de armaduras | Consulta pública |
| /spectrecore cocina <ayuda\|libro\|recetas\|estado\|reparar\|recargar> | Cocina y recetario | Consulta pública |
| /spectrecore trabajos | Niveles de trabajos | Consulta pública |
| /spectrecore resonancia | Carga del Pico Perdido | Consulta pública |
| /spectrecore resonancia debug | Diagnóstico de resonancia | Administración general o Hydev |
| /spectrecore item nombre | Inspeccionar y copiar el nombre del objeto | Administración general o Hydev |
| /spectrecore slayer estado | Progreso de misiones Slayer | Consulta pública |
| /spectrecore slayer activar <categoria> <1\|2\|3> | Iniciar una misión Slayer | Administración general o Hydev |
| /spectrecore slayer cancelar <categoria> | Cancelar una misión Slayer | Administración general o Hydev |
| /spectrecore slayer reiniciar <categoria> | Reiniciar una misión Slayer | Administración general o Hydev |
| /spectrecore slayer invocar <categoria> | Reintentar la invocación de un objetivo completado | Administración general o Hydev |
| /spectrecore portal registrar <jugador> <sesion> | Registrar participante y sesión | Solo consola |
| /spectrecore portal salir <jugador> | Retirar el registro de un participante | Solo consola |
| /spectrecore portal estado [jugador] | Diagnóstico del portal | Administración general o Hydev |
| /spectrecore portal demo [jugador] | Previsualizar el portal | Administración general o Hydev |
| /spectrecore portal limpiar | Retirar los portales activos | Administración general o Hydev |
| /spectrecore tesoros abrir | Abrir un cofre con premio | Administración de actividades |
| /spectrecore tesoros mineria <1\|2\|3> | Previsualizar un tesoro minero | Administración general o Hydev |
| /spectrecore tesoros gema <basica\|intermedia\|superior> | Previsualizar una gema | Administración general o Hydev |
| /spectrecore tesoros combate <1\|2\|3> | Previsualizar un alma | Administración general o Hydev |
| /spectrecore tesoros agricultura <1\|2\|3> | Previsualizar un hallazgo agrícola | Administración general o Hydev |
| /spectrecore cygnus rompemuelas | Oferta de Cygnus: rompemuelas | Administración de actividades |
| /spectrecore cygnus taladroroto | Oferta de Cygnus: taladroroto | Administración de actividades |
| /spectrecore cygnus furiaestelar | Oferta de Cygnus: furiaestelar | Administración de actividades |
| /spectrecore cygnus capuchaencogedora | Oferta de Cygnus: capuchaencogedora | Administración de actividades |
| /spectrecore cygnus slimehechizado | Oferta de Cygnus: slimehechizado | Administración de actividades |
| /spectrecore cygnus compactador | Oferta de Cygnus: compactador | Administración de actividades |
| /spectrecore cygnus golemcobre | Oferta de Cygnus: golemcobre | Administración de actividades |
| /spectrecore polvos diamante | Pago de polvo: diamante | Administración de actividades |
| /spectrecore polvos hierro | Pago de polvo: hierro | Administración de actividades |
| /spectrecore polvos oro | Pago de polvo: oro | Administración de actividades |
| /spectrecore polvos cobre | Pago de polvo: cobre | Administración de actividades |
| /spectrecore polvos lapis | Pago de polvo: lapis | Administración de actividades |
| /spectrecore polvos redstone | Pago de polvo: redstone | Administración de actividades |
| /spectrecore desbloquear almas | Desbloquear almas de tier 1 | Administración de actividades |
| /spectrecore desbloquear mineria | Desbloquear minería de tier 1 | Administración de actividades |
| /spectrecore desbloquear slayerzombie 1 | Desbloquear Revenant de tier 1 | Administración de actividades |
| /spectrecore desbloquear slayerzombie 2 | Desbloquear Revenant de tier 2 | Administración de actividades |
| /spectrecore desbloquear slayerzombie 3 | Desbloquear Revenant de tier 3 | Administración de actividades |
| /spectrecore telefono | Abrir el menú de Slayers | Administración de actividades |

Cocina: ayuda, libro, recetas y estado son públicos; reparar y recargar exigen permiso de cocina o administración. Slayer: categorías revenant, necromancer y spider. Las consultas no conceden acceso a recompensas: se conservan los permisos de progreso originales.

## Equivalencias de los comandos anteriores

| Anterior | Actual |
|---|---|
| /trabajo | /spectrecore trabajos |
| /activarslayer | /spectrecore slayer activar |
| /cancelarslayer | /spectrecore slayer cancelar |
| /resetslayer | /spectrecore slayer reiniciar |
| /slayerstats | /spectrecore slayer estado |
| /slayerinvocar | /spectrecore slayer invocar |
| /hydevportaljoin | /spectrecore portal registrar |
| /hydevportalleave | /spectrecore portal salir |
| /hydevportalcleanup | /spectrecore portal limpiar |
| /hydevportalstatus | /spectrecore portal estado |
| /hydevportaldemo | /spectrecore portal demo |
| /resonancia | /spectrecore resonancia |
| /debugresonancia | /spectrecore resonancia debug |
| /nombreitem | /spectrecore item nombre |
| /armaduras | /spectrecore armaduras |
| /cocina | /spectrecore cocina |
| /opentreasure | /spectrecore tesoros abrir |
| /almas1 | /spectrecore desbloquear almas |
| /mineria1 | /spectrecore desbloquear mineria |
| /telefono1 | /spectrecore telefono |
| /rompemuelas | /spectrecore cygnus rompemuelas |
| /taladroroto | /spectrecore cygnus taladroroto |
| /furiaestelar | /spectrecore cygnus furiaestelar |
| /capuchaencogedora | /spectrecore cygnus capuchaencogedora |
| /slimehechizado | /spectrecore cygnus slimehechizado |
| /compactador | /spectrecore cygnus compactador |
| /golemcobre | /spectrecore cygnus golemcobre |
| /polvodiamante | /spectrecore polvos diamante |
| /polvohierro | /spectrecore polvos hierro |
| /polvooro | /spectrecore polvos oro |
| /polvocobre | /spectrecore polvos cobre |
| /polvolapis | /spectrecore polvos lapis |
| /polvoredstone | /spectrecore polvos redstone |
| /slayerzombie1 | /spectrecore desbloquear slayerzombie 1 |
| /slayerzombie2 | /spectrecore desbloquear slayerzombie 2 |
| /slayerzombie3 | /spectrecore desbloquear slayerzombie 3 |

Los nombres antiguos y sus alias ya no se registran como comandos independientes. Los alias del antiguo comando raíz (score, sskills, spectreskills) también se retiran. Las rutas antiguas que ya empezaban por /spectrecore mineria, combate o agricultura siguen admitidas por compatibilidad; las rutas canónicas son las de tesoros.

## Actualizar integraciones

En Dungeons+, usa desde consola:

    spectrecore portal registrar <jugador> <sesion>
    spectrecore portal salir <jugador>

En MMOItems, la notificación de Blink sigue siendo:

    spectrecore blink <jugador>

Reemplaza también los comandos anteriores en NPC, zMenu, misiones, consumibles y cualquier configuración externa. El plugin no puede modificar archivos de otros plugins que no se hayan proporcionado. Por ejemplo, cambia rompemuelas por spectrecore cygnus rompemuelas. Los comandos de actividades y Slayer que antes exigían un jugador siguen exigiéndolo; no ganan un argumento de jugador objetivo.

## Instalar

Detén el servidor, reemplaza el JAR anterior por SpectreCore-0.7.3-alpha.jar y conserva plugins/SpectreCore/. Al iniciar se añaden las descripciones de las rutas a modules/nucleo/comandos.yml. Las referencias explícitas a comandos antiguos en mensajes y usos configurados se actualizan, guardando su copia original en backups/commands-v1/. No se tocan los datos de jugadores.

Los textos de ayuda están en modules/nucleo/mensajes.yml, sección CommandRouter; los usos y descripciones de rutas están en modules/nucleo/comandos.yml, sección rutas. La carga de módulos y permisos conserva las reglas anteriores. Reinicia tras cambiar el JAR; para editar mensajes después, usa /spectrecore reload.

Compilación y pruebas automatizadas de argumentos, rutas, permisos, autocompletado y migración. Falta prueba de integración dentro de Paper con los plugins de tu servidor.
