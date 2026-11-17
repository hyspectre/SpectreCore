# SpectreCore 0.5.0-alpha — migración de Skript

Paper 1.21.10, Java 21, MMOItems para recompensas. MythicMobs Free mantiene sus mobs y skills; SpectreCore reemplaza el controlador de daño por scoreboard tags. No se redistribuye MMOItems.

## Instalación

1. Detén el servidor y respalda la configuración y las variables actuales de Skript.
2. Retira el JAR anterior de SpectreCore. Instala únicamente SpectreCore-0.5.0-alpha.jar.
3. Desactiva los scripts originales: hydev_v13_damage.sk, Resonancia Mineral, Tesoros de minería y las versiones de /resonancia, /debugresonancia y /nombreitem que pegaste. La resonancia estaba duplicada en el mensaje: aquí hay un solo módulo.
4. Conserva Skript/tesoros-config.yaml en su ubicación. Al primer arranque, si NO existe SpectreCore/mining.yml, el plugin crea ese archivo e importa las claves gemas.* y probabilidades.mineria.*. No copies antes una plantilla de mining.yml si quieres esta importación automática.
5. Arranca. Revisa en consola el resultado de la importación y ejecuta /spectrecore modulos. El módulo de jefes y el de minería se activan por defecto, incluso al conservar un config.yml antiguo.
6. Abre SpectreCore/mining.yml: verifica gemas.ids.basica/intermedia/superior y probabilidades.mineria.tier1/tier2/tier3. Si el archivo antiguo no existe, no se inventan IDs ni probabilidades: las gemas quedan sin ID y los tesoros en 0% hasta configurarlos.
7. /spectrecore reload recarga mining.yml, además de las habilidades y recetas. Activar o desactivar módulos enteros requiere reiniciar.

No copies la carpeta FUENTES al servidor. Los mundos, datos de cocina, las armaduras, el libro /armaduras y las cinco habilidades existentes se conservan.

## Configuración y datos

- SpectreCore/config.yml: modules.boss-damage.enabled y modules.mining.enabled. Valores predeterminados true. Añade esos bloques si quieres poder desactivarlos explícitamente en una configuración antigua.
- SpectreCore/mining.yml: herramienta, IDs de gemas, probabilidades, tesoro, cooldown y límites de animación.
- SpectreCore/mining-data.yml: carga por UUID, vencimiento absoluto de resonancia y cooldown del tesoro. Guardado periódico, al salir y al detener el plugin, mediante reemplazo de archivo temporal.
- Los minerales colocados después de instalar esta versión se registran en los datos persistentes de su chunk, que se guardan con el mundo.

IMPORTANTE SOBRE LOS DATOS ANTIGUOS: la importación automática es de configuración YAML, no de las variables internas de Skript. Las cargas, ventanas activas y marcas de minerales colocados que existían en Skript no se transfieren en este paquete; el estado Java comienza vacío. Por tanto, minerales colocados ANTES de esta migración no pueden distinguirse de los naturales si no tienen una marca nueva. Conserva el respaldo de variables si necesitas un importador específico de esos datos antes de abrir la minería a jugadores.

## Reglas conservadas

### Jefes

- HYDEV_SPECTRE_ETEREO cancela daño.
- DRACO_AGOTADO x1.5; ENT_CORTEZA x0.65; CABA_VULNERABLE x1.25; MINO_VULNERABLE x1.25; MINO_SHIELD x0.20. Todos conservan el prefijo HYDEV_ y se multiplican si coinciden.
- CABA_CANAL acumula 200 HP Java de daño final no cancelado. CABA_RESET borra la cuenta y se consume; al alcanzar 200 se añade CABA_BREAK. No se confunden los 100 corazones de Skript con 100 HP.
- MINO_LOCK70 y MINO_LOCK35 detienen el golpe que cruza el umbral y fijan la vida a 70% o 35%. No se añadió un bloqueo al 10% porque no está en este script.
- Se conservan literalmente los nombres de tags y el patrón de golpe cancelado. Las skills de MythicMobs siguen encargándose de las transiciones y de retirar los locks. No deben depender de recibir un onDamaged cancelado; conserva las comprobaciones de vida del diseño actual.
- Un lock que se quedó activo por error nunca cura al jefe. Los golpes cancelados tampoco cuentan para romper Robo de Vitalidad.

### Resonancia

T1: carbón/cobre/hierro/oro, normales o de pizarra profunda, +1. T2: redstone/lapislázuli, +2. T3: diamante/esmeralda, +4. Umbral predeterminado 50 y ventana 30 segundos, importables del YAML.

Al alcanzar el umbral se activa la ventana. Para extraer, agáchate y rompe otro mineral válido. La carga no se convierte en una extracción en el mismo golpe que la completa. T1: 100% básica; T2: 75% básica / 25% intermedia; T3: 50% básica / 40% intermedia / 10% superior. Se entrega exactamente una GEM_STONE, se reinicia la carga y termina la ventana. El tiempo transcurre también estando desconectado.

Se conserva la identificación por nombre que usaba el script: un pico con nombre personalizado que contenga Pico Perdido, sin colores. gemas.pico era leído pero no usado por el Skript. Si prefieres exigir el objeto real, cambia gemas.identificar-por a id y configura gemas.pico y gemas.tipo-pico con el ID/tipo MMOItems reales. No se adivina ese ID.

Si MMOItems no genera la gema, no se consume la resonancia, aunque su ventana sigue transcurriendo. Un inventario lleno deja el sobrante a los pies del jugador con propietario asignado.

### Tesoros

Se mantienen tesoros.mineria.tier1/2/3 y sus probabilidades independientes, con cooldown compartido por jugador de 5 segundos. Los permisos deben concederse mediante tu gestor de permisos; tener OP no sustituye esas concesiones explícitas (default false).

Los tres tiers entregan MATERIAL:PALADIO, cantidad aleatoria inclusiva de 2 a 8. El script ignoraba MINERIA_T1/T2/T3 dentro de la función y siempre daba PALADIO: se conserva el comportamiento real. El nuevo YAML permite cambiar ID, tipo, cantidad y cooldown.

Mejoras intencionales: ambos sistemas exigen supervivencia y minerales no marcados como colocados. También se marcan minerales movidos por pistones. Las roturas canceladas no dan carga ni premios. La recompensa se genera y se entrega inmediatamente; el cooldown comienza entonces y la animación es cosmética. Así no hay una espera con recompensa pendiente que se pierda al salir o al apagar el plugin, ni activaciones simultáneas durante esa espera.

La protección cubre colocación normal, pistones, roturas y explosiones. Cambios directos de bloques mediante WorldEdit u otros plugins no siempre emiten estos eventos y requieren integración específica para mantener la procedencia.

## Estética

- Tesoro: BlockDisplay del mineral, ascenso y rotación, tres fragmentos orbitales, espiral de partículas por tier y cierre con destellos y sonido. Ya no utiliza ArmorStand.
- Gema: ItemDisplay del objeto MMOItems real con órbitas y paleta verde/azul/morada según calidad.
- Resonancia: medidor de doce segmentos, halo de amatista al completar y cuenta atrás visible mientras sostienes el pico.
- Jefes: anillo rojizo y golpe sonoro al alcanzar un lock del Minotauro; destello de interrupción al romper la canalización del Caballero.
- Las animaciones no alteran bloques reales, no se pueden recoger y no necesitan resource pack. Se limitan por concurrencia; al alcanzar el límite, el botín se sigue entregando.
- Se limpian al terminar, apagar el módulo, descargar/cargar chunks o perder sus entidades. No se fuerzan chunks para prolongarlas.

## Comandos

- /resonancia: carga y segundos restantes.
- /debugresonancia: nombre, material, tipo/ID MMOItems, identificación de herramienta y probabilidades. Administración.
- /nombreitem: nombre original y limpio, botón nativo de copiar al portapapeles. Administración; no construye comandos tellraw con texto del objeto.
- /spectrecore mineria tesoro 1 (también 2 o 3): vista previa de cada tier sin dar botín ni modificar la carga.
- /spectrecore mineria gema superior (también basica/intermedia): vista previa con gema real o fragmento de amatista si falta la configuración. No entrega objetos.

Los diagnósticos y demostraciones requieren spectrecore.admin o hydev.admin. Los comandos existentes de cocina, demos de habilidades y libro de armaduras permanecen.

## Verificación realizada

- Gradle build correcto con Java 21 y Paper API 1.21.10.
- 98.339 aserciones de regresión, incluyendo las 300 tiradas posibles de calidades y las 98.304 posiciones de un chunk vanilla entre Y=-64 y Y=319.
- Pruebas existentes de combate, cooldowns, herramientas/tiempos/tandas de cocina: correctas.
- Validación YAML: correcta; sin IDs ni probabilidades inventados.
- Comparación binaria de 18 clases existentes de habilidades, cocina, núcleo y libro: sin cambios respecto a 0.4.1.

No se ha probado el JAR en un servidor Paper real. Sigue PRUEBAS_0.5.0.md para comprobar integración y aspecto.

Referencias de API consultadas: https://jd.papermc.io/paper/1.21.10/org/bukkit/event/entity/EntityDamageEvent.html y https://docs.phoenixdevt.fr/mmoitems/api/main.html
